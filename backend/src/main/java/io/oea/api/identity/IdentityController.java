package io.oea.api.identity;

import io.oea.api.generated.IdentityApi;
import io.oea.api.generated.model.EffectivePermission;
import io.oea.api.generated.model.EffectivePermissionGrantedByInner;
import io.oea.api.generated.model.LocalLoginPasskeyOptionsRequest;
import io.oea.api.generated.model.LocalLoginPasskeyRequest;
import io.oea.api.generated.model.LocalLoginRequest;
import io.oea.api.generated.model.LocalLoginTotpRequest;
import io.oea.api.generated.model.LoginProvider;
import io.oea.api.generated.model.LoginResult;
import io.oea.api.generated.model.Logout200Response;
import io.oea.api.generated.model.Me;
import io.oea.api.generated.model.MeRolesInner;
import io.oea.api.generated.model.PasskeyChallenge;
import io.oea.api.generated.model.PasskeyLoginRequest;
import io.oea.api.generated.model.PasskeyRegistration;
import io.oea.api.generated.model.PasskeyRegistrationStart;
import io.oea.api.generated.model.PasswordChange;
import io.oea.api.generated.model.PasswordRules;
import io.oea.api.generated.model.PasswordSuggestion;
import io.oea.api.generated.model.PersonPreferences;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.RequiredAction;
import io.oea.api.generated.model.RoleRef;
import io.oea.api.generated.model.SecondFactor;
import io.oea.api.generated.model.TotpConfirmation;
import io.oea.api.generated.model.TotpEnrollment;
import io.oea.api.generated.model.TotpEnrollmentRequest;
import io.oea.api.generated.model.TokenResponse;
import io.oea.app.access.LanguagePreferences;
import io.oea.app.access.SecondFactors;
import io.oea.app.login.AccessTokens;
import io.oea.app.login.LocalLogin;
import io.oea.app.login.OidcLoginFailedException;
import io.oea.app.login.OidcLoginFlow;
import io.oea.app.login.PasskeyLogin;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.config.security.LoginCookie;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.security.SessionCookie;
import io.oea.infrastructure.oidc.OidcProviderUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

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

	private final io.oea.app.login.PasswordChange passwordChange;

	private final SecondFactors secondFactors;

	private final io.oea.app.login.PasskeyRegistration passkeyRegistration;

	private final PasskeyLogin passkeyLogin;

	private final LanguagePreferences languages;

	private final AccessTokens accessTokens;

	public IdentityController(Sessions sessions, PermissionResolver permissions, LocalLogin localLogin,
			io.oea.app.login.TotpEnrollment enrollment, OidcLoginFlow oidcLogin,
			io.oea.app.login.PasswordChange passwordChange, SecondFactors secondFactors,
			io.oea.app.login.PasskeyRegistration passkeyRegistration, PasskeyLogin passkeyLogin,
			LanguagePreferences languages, AccessTokens accessTokens) {
		this.languages = languages;
		this.accessTokens = accessTokens;
		this.sessions = sessions;
		this.permissions = permissions;
		this.localLogin = localLogin;
		this.enrollment = enrollment;
		this.oidcLogin = oidcLogin;
		this.passwordChange = passwordChange;
		this.secondFactors = secondFactors;
		this.passkeyRegistration = passkeyRegistration;
		this.passkeyLogin = passkeyLogin;
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
	public ResponseEntity<PasskeyChallenge> startLocalLoginPasskey(LocalLoginPasskeyOptionsRequest request) {
		PasskeyLogin.Options options = this.localLogin.passkeyOptions(request.getLoginTransaction());
		return ResponseEntity.ok(new PasskeyChallenge(options.challengeId(), options.options()));
	}

	@Override
	public ResponseEntity<LoginResult> localLoginPasskey(LocalLoginPasskeyRequest request) {
		return loggedIn(this.localLogin.loginWithPasskey(request.getLoginTransaction(), request.getChallengeId(),
				json(request.getCredential()), client()));
	}

	@Override
	public ResponseEntity<PasskeyChallenge> startPasskeyLogin() {
		PasskeyLogin.Options options = this.passkeyLogin.start();
		return ResponseEntity.ok(new PasskeyChallenge(options.challengeId(), options.options()));
	}

	@Override
	public ResponseEntity<LoginResult> passkeyLogin(PasskeyLoginRequest request) {
		return loggedIn(this.passkeyLogin.login(request.getChallengeId(), json(request.getCredential()), client()));
	}

	@Override
	public ResponseEntity<PasskeyRegistration> startPasskeyRegistration(PasskeyRegistrationStart request) {
		var started = this.passkeyRegistration.start(current().getPrincipal(),
				(request != null) ? request.getLabel() : null);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new PasskeyRegistration(started.registrationId(), started.options(),
					started.expiresAt().atOffset(ZoneOffset.UTC)));
	}

	@Override
	public ResponseEntity<SecondFactor> confirmPasskeyRegistration(UUID registrationId, Map<String, Object> response) {
		var registered = this.passkeyRegistration.confirm(current().getPrincipal(), registrationId, json(response));
		return ResponseEntity.ok(new SecondFactor(registered.credentialId(), SecondFactor.TypeEnum.PASSKEY,
				registered.createdAt().atOffset(ZoneOffset.UTC)).label(registered.label()));
	}

	/** A WebAuthn JSON object as the text WebAuthn4J parses (ADR-048). */
	private static String json(Map<String, Object> value) {
		return JsonMapper.shared().writeValueAsString(value);
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

	@Override
	public ResponseEntity<List<SecondFactor>> listOwnSecondFactors() {
		return ResponseEntity.ok(this.secondFactors.list(current().getPrincipal())
			.stream()
			.map((factor) -> new SecondFactor(factor.credentialId(), SecondFactor.TypeEnum.fromValue(factor.type()),
					factor.createdAt().atOffset(ZoneOffset.UTC))
				.label(factor.label())
				.lastUsedAt((factor.lastUsedAt() != null) ? factor.lastUsedAt().atOffset(ZoneOffset.UTC) : null))
			.toList());
	}

	@Override
	public ResponseEntity<Void> removeOwnSecondFactor(UUID credentialId) {
		this.secondFactors.remove(current().getPrincipal(), credentialId);
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<LoginResult> loggedIn(Sessions.NewSession session) {
		SessionCookie.write(response(), session.token(), this.sessions.absoluteLifetime());
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new LoginResult(principalRef(session.principal()), session.principal().secondFactorVerified(),
					session.csrfToken())
				.requiredAction(requiredAction(session.principal())));
	}

	@Override
	public ResponseEntity<Void> changeOwnPassword(PasswordChange request) {
		this.passwordChange.change(current().getPrincipal(), request.getCurrentPassword(), request.getNewPassword(),
				client().address());
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<PasswordSuggestion> suggestPassword() {
		return ResponseEntity.ok(new PasswordSuggestion(this.passwordChange.suggest(current().getPrincipal())));
	}

	@Override
	public ResponseEntity<PasswordRules> getOwnPasswordRules() {
		io.oea.app.login.PasswordChange.Rules rules = this.passwordChange.rules(current().getPrincipal());
		return ResponseEntity.ok(new PasswordRules(rules.minLength(), rules.maxLength(), rules.requireUppercase(),
				rules.requireLowercase(), rules.requireDigits(), rules.requireSpecialChars()));
	}

	private static RequiredAction requiredAction(OeaPrincipal principal) {
		return (principal.requiredAction() != null) ? RequiredAction.fromValue(principal.requiredAction()) : null;
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
		List<PermissionResolver.RoleGrant> grants = this.permissions.grants(principal);
		List<MeRolesInner> roleGrants = grants.stream()
			.map((grant) -> new MeRolesInner(new RoleRef(grant.roleId(), grant.roleName(), grant.privileged()),
					MeRolesInner.SourceEnum.fromValue(grant.source()), grant.effective())
				.mappingId(grant.mappingId())
				.notEffectiveReason((grant.notEffectiveReason() != null)
						? MeRolesInner.NotEffectiveReasonEnum.fromValue(grant.notEffectiveReason()) : null))
			.toList();
		List<EffectivePermission> effective = this.permissions.permissions(principal)
			.stream()
			.map((permission) -> new EffectivePermission(permission, grants.stream()
				.filter(PermissionResolver.RoleGrant::effective)
				.filter((grant) -> this.permissions.rolePermissions(grant.roleId()).contains(permission))
				.map((grant) -> new EffectivePermissionGrantedByInner(grant.roleId(),
						EffectivePermissionGrantedByInner.SourceEnum.fromValue(grant.source())))
				.distinct()
				.toList()))
			.toList();
		Me me = new Me(principalRef(principal), principal.secondFactorVerified(), roleGrants, effective)
			// a bearer request has no session and needs no CSRF token
			.csrfToken(authentication.bearer() ? null : Sessions.csrfToken(authentication.sessionToken()))
			.requiredAction(requiredAction(principal));
		String language = this.languages.language(principal);
		me.setUiLanguage((language != null) ? Me.UiLanguageEnum.fromValue(language) : null);
		return ResponseEntity.ok(me);
	}

	@Override
	public ResponseEntity<Void> updateMyPreferences(PersonPreferences personPreferences) {
		this.languages.update(current().getPrincipal(), personPreferences.getUiLanguage());
		return ResponseEntity.noContent().build();
	}

	@Override
	public ResponseEntity<Logout200Response> logout() {
		this.sessions.logout(current().getPrincipal());
		SessionCookie.clear(response());
		return ResponseEntity.ok().header("OEA-Purge-Local-Data", "session-ended").body(new Logout200Response());
	}

	/**
	 * The client credentials grant (ADR-050 rule 5; design D3 of s12-client-credentials-token):
	 * only {@code grant_type=client_credentials} with HTTP Basic client authentication; client
	 * credentials anywhere else are refused like a failed authentication.
	 */
	@Override
	@OAuthErrorResponse
	public ResponseEntity<TokenResponse> issueClientCredentialsToken(String grantType) {
		HttpServletRequest request = request();
		if (request.getParameter("client_id") != null || request.getParameter("client_secret") != null) {
			throw new OAuthRequestException(HttpStatus.UNAUTHORIZED, "invalid_client");
		}
		String[] grantTypes = request.getParameterValues("grant_type");
		if (grantTypes == null || grantTypes.length != 1 || grantType == null || grantType.isBlank()) {
			throw new OAuthRequestException(HttpStatus.BAD_REQUEST, "invalid_request");
		}
		if (!"client_credentials".equals(grantType)) {
			throw new OAuthRequestException(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
		}
		String[] credentials = basicCredentials(request.getHeader(HttpHeaders.AUTHORIZATION));
		AccessTokens.Issued issued = this.accessTokens.issue(credentials[0], credentials[1], request.getRemoteAddr());
		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.header(HttpHeaders.PRAGMA, "no-cache")
			.body(new TokenResponse(issued.accessToken(), TokenResponse.TokenTypeEnum.BEARER,
					Math.toIntExact(issued.expiresIn())));
	}

	/**
	 * Client ID and secret of an HTTP Basic header, each form-urlencoded (RFC 6749 §2.3.1); a
	 * missing or malformed header yields nulls, which the token service refuses like an unknown
	 * client.
	 */
	static String[] basicCredentials(String header) {
		if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
			return new String[] { null, null };
		}
		try {
			String decoded = new String(Base64.getDecoder().decode(header.substring(6).strip()), StandardCharsets.UTF_8);
			int colon = decoded.indexOf(':');
			if (colon < 0) {
				return new String[] { null, null };
			}
			return new String[] { URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
					URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8) };
		}
		catch (IllegalArgumentException ex) {
			return new String[] { null, null };
		}
	}

	public static PrincipalRef principalRef(OeaPrincipal principal) {
		PrincipalRef.PrincipalTypeEnum type = switch (principal.type()) {
			case SYSTEM_ADMIN_ACCOUNT -> PrincipalRef.PrincipalTypeEnum.SYSTEM_ADMIN_ACCOUNT;
			case SERVICE_ACCOUNT -> PrincipalRef.PrincipalTypeEnum.SERVICE_ACCOUNT;
			case PERSON -> PrincipalRef.PrincipalTypeEnum.PERSON;
		};
		Integer id = (principal.personId() != null) ? Math.toIntExact(principal.personId()) : null;
		return new PrincipalRef(type, principal.displayName()).id(id);
	}


	private static OeaAuthentication current() {
		return (OeaAuthentication) SecurityContextHolder.getContext().getAuthentication();
	}

}
