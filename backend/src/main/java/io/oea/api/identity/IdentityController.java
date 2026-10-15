package io.oea.api.identity;

import io.oea.api.generated.IdentityApi;
import io.oea.api.generated.model.EffectivePermission;
import io.oea.api.generated.model.EffectivePermissionGrantedByInner;
import io.oea.api.generated.model.LocalLoginRequest;
import io.oea.api.generated.model.LocalLoginTotpRequest;
import io.oea.api.generated.model.LoginProvider;
import io.oea.api.generated.model.LoginResult;
import io.oea.api.generated.model.Logout200Response;
import io.oea.api.generated.model.Me;
import io.oea.api.generated.model.MeRolesInner;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.RoleRef;
import io.oea.api.generated.model.SecondFactor;
import io.oea.api.generated.model.TotpConfirmation;
import io.oea.api.generated.model.TotpEnrollment;
import io.oea.api.generated.model.TotpEnrollmentRequest;
import io.oea.app.login.LocalLogin;
import io.oea.app.login.OidcLoginFailedException;
import io.oea.app.login.OidcLoginFlow;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.config.oidc.OidcProviderUnavailableException;
import io.oea.config.security.LoginCookie;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.security.SessionCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Local and OIDC login, second factor, current principal and logout (US-013 AC2, US-001,
 * REQ-164, REQ-165, REQ-171).
 */
@RestController
public class IdentityController implements IdentityApi {

	private final Sessions sessions;

	private final PermissionResolver permissions;

	private final LocalLogin localLogin;

	private final io.oea.app.login.TotpEnrollment enrollment;

	private final OidcLoginFlow oidcLogin;

	public IdentityController(Sessions sessions, PermissionResolver permissions, LocalLogin localLogin,
			io.oea.app.login.TotpEnrollment enrollment, OidcLoginFlow oidcLogin) {
		this.sessions = sessions;
		this.permissions = permissions;
		this.localLogin = localLogin;
		this.enrollment = enrollment;
		this.oidcLogin = oidcLogin;
	}

	@Override
	public ResponseEntity<List<LoginProvider>> listLoginProviders() {
		return ResponseEntity.ok(this.oidcLogin.providers()
			.stream()
			.map((provider) -> new LoginProvider(provider.id(), provider.name()))
			.toList());
	}

	@Override
	public ResponseEntity<Void> startOidcLogin(UUID providerId, String returnTo) {
		try {
			OidcLoginFlow.Started started = this.oidcLogin.start(providerId, returnTo);
			LoginCookie.write(response(), started.browserBinding());
			return redirect(started.authorizationUri());
		}
		catch (OidcProviderUnavailableException ex) {
			return redirect(URI.create(this.oidcLogin.publicUrl() + "/login?error=provider-unavailable"));
		}
	}

	@Override
	public ResponseEntity<Void> completeOidcLogin(String code, String state, String error) {
		HttpServletRequest request = request();
		HttpServletResponse response = response();
		LoginCookie.clear(response);
		try {
			OidcLoginFlow.Completed completed = this.oidcLogin.complete(state, code, error, LoginCookie.read(request),
					new OidcLoginFlow.Client(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT)));
			SessionCookie.write(response, completed.session().token(), this.sessions.absoluteLifetime());
			return redirect(URI.create(this.oidcLogin.publicUrl() + completed.returnTo()));
		}
		catch (OidcLoginFailedException ex) {
			return redirect(URI.create(this.oidcLogin.publicUrl() + "/login?error=login-failed"));
		}
		catch (OidcProviderUnavailableException ex) {
			return redirect(URI.create(this.oidcLogin.publicUrl() + "/login?error=provider-unavailable"));
		}
	}

	private static ResponseEntity<Void> redirect(URI location) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.build();
	}

	private static HttpServletRequest request() {
		return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
	}

	@Override
	public ResponseEntity<LoginResult> localLogin(LocalLoginRequest request) {
		return loggedIn(this.localLogin.login(request.getLoginName(), request.getPassword(), client()));
	}

	@Override
	public ResponseEntity<LoginResult> localLoginTotp(LocalLoginTotpRequest request) {
		return loggedIn(this.localLogin.loginWithTotp(request.getLoginTransaction(), request.getCode(), client()));
	}

	@Override
	public ResponseEntity<TotpEnrollment> startTotpEnrollment(TotpEnrollmentRequest request) {
		var started = this.enrollment.start(current().getPrincipal(), (request != null) ? request.getLabel() : null);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(new TotpEnrollment(started.credentialId(), started.secret(), started.otpauthUri(),
					started.expiresAt().atOffset(ZoneOffset.UTC)));
	}

	@Override
	public ResponseEntity<SecondFactor> confirmTotpEnrollment(UUID credentialId, TotpConfirmation confirmation) {
		var confirmed = this.enrollment.confirm(current().getPrincipal(), credentialId, confirmation.getCode());
		return ResponseEntity.ok(new SecondFactor(confirmed.credentialId(), SecondFactor.TypeEnum.TOTP,
				confirmed.createdAt().atOffset(ZoneOffset.UTC)).label(confirmed.label()));
	}

	private ResponseEntity<LoginResult> loggedIn(Sessions.NewSession session) {
		SessionCookie.write(response(), session.token(), this.sessions.absoluteLifetime());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new LoginResult(principalRef(session.principal()), session.principal().secondFactorVerified(),
					session.csrfToken()));
	}

	private static LocalLogin.Client client() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
			.getRequest();
		return new LocalLogin.Client(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
	}

	private static HttpServletResponse response() {
		return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();
	}

	@Override
	public ResponseEntity<Me> getCurrentPrincipal() {
		OeaAuthentication authentication = current();
		OeaPrincipal principal = authentication.getPrincipal();
		List<String> roles = this.permissions.roles(principal);
		MeRolesInner.SourceEnum roleSource = MeRolesInner.SourceEnum.SYSTEM_ADMIN_ACCOUNT;
		List<MeRolesInner> roleGrants = roles.stream()
			.map((role) -> new MeRolesInner(new RoleRef(role, displayName(role), true), roleSource, true))
			.toList();
		List<EffectivePermission> effective = this.permissions.permissions(principal)
			.stream()
			.map((permission) -> new EffectivePermission(permission, roles.stream()
				.map((role) -> new EffectivePermissionGrantedByInner(role,
						EffectivePermissionGrantedByInner.SourceEnum.SYSTEM_ADMIN_ACCOUNT))
				.toList()))
			.toList();
		Me me = new Me(principalRef(principal), principal.secondFactorVerified(), roleGrants, effective)
			.csrfToken(Sessions.csrfToken(authentication.sessionToken()));
		return ResponseEntity.ok(me);
	}

	@Override
	public ResponseEntity<Logout200Response> logout() {
		this.sessions.logout(current().getPrincipal());
		SessionCookie.clear(response());
		return ResponseEntity.ok().header("OEA-Purge-Local-Data", "session-ended").body(new Logout200Response());
	}

	public static PrincipalRef principalRef(OeaPrincipal principal) {
		PrincipalRef.PrincipalTypeEnum type = (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT)
				? PrincipalRef.PrincipalTypeEnum.SYSTEM_ADMIN_ACCOUNT : PrincipalRef.PrincipalTypeEnum.PERSON;
		Integer id = (principal.personId() != null) ? Math.toIntExact(principal.personId()) : null;
		return new PrincipalRef(type, principal.displayName()).id(id);
	}

	private static String displayName(String roleId) {
		StringBuilder name = new StringBuilder();
		for (String word : roleId.split("-")) {
			name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return name.toString();
	}

	private static OeaAuthentication current() {
		return (OeaAuthentication) SecurityContextHolder.getContext().getAuthentication();
	}

}
