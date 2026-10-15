package io.oea.api.identity;

import io.oea.api.generated.IdentityApi;
import io.oea.api.generated.model.EffectivePermission;
import io.oea.api.generated.model.EffectivePermissionGrantedByInner;
import io.oea.api.generated.model.Logout200Response;
import io.oea.api.generated.model.Me;
import io.oea.api.generated.model.MeRolesInner;
import io.oea.api.generated.model.PrincipalRef;
import io.oea.api.generated.model.RoleRef;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.config.security.OeaAuthentication;
import io.oea.config.security.SessionCookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Current principal and logout (REQ-164, REQ-165); local login follows in the same interface. */
@RestController
public class IdentityController implements IdentityApi {

	private final Sessions sessions;

	private final PermissionResolver permissions;

	public IdentityController(Sessions sessions, PermissionResolver permissions) {
		this.sessions = sessions;
		this.permissions = permissions;
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
		HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
			.getResponse();
		SessionCookie.clear(response);
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
