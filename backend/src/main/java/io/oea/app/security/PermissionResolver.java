package io.oea.app.security;

import io.oea.core.access.RoleStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Roles and effective permissions of a principal (ADR-032 §7), cached for at most 60 seconds.
 * The system admin account holds the built-in role {@code system-admin}; a person session holds
 * the roles derived from IdP group mappings (design D7 of us-001-oidc-login) and the person's
 * local role assignments (US-144). A privileged role is effective only in a session confirmed
 * with a second factor (SoD-8).
 */
@Service
public class PermissionResolver {

	public static final String SYSTEM_ADMIN_ROLE = "system-admin";

	static final Duration MAXIMUM_AGE = Duration.ofSeconds(60);

	private final RoleStore roles;

	private final IdpRoles idpRoles;

	private final Clock clock;

	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	public PermissionResolver(RoleStore roles, IdpRoles idpRoles, Clock clock) {
		this.roles = roles;
		this.idpRoles = idpRoles;
		this.clock = clock;
	}

	/**
	 * One role held by the principal.
	 *
	 * @param source {@code system-admin-account} or {@code idp}
	 * @param mappingId the IdP group mapping for source {@code idp}
	 * @param notEffectiveReason {@code mfa-required} if a privileged role lacks the second factor
	 */
	public record RoleGrant(String roleId, String roleName, boolean privileged, String source, UUID mappingId,
			boolean effective, String notEffectiveReason) {
	}

	private record Cached(List<RoleGrant> grants, Set<String> permissions, Instant at) {
	}

	/** All roles of the principal, effective or not. */
	public List<RoleGrant> grants(OeaPrincipal principal) {
		return resolve(principal).grants();
	}

	/** Effective roles of the principal (role IDs). */
	public List<String> roles(OeaPrincipal principal) {
		return grants(principal).stream().filter(RoleGrant::effective).map(RoleGrant::roleId).distinct().toList();
	}

	public Set<String> permissions(OeaPrincipal principal) {
		return resolve(principal).permissions();
	}

	/** Permissions of one role (approved only). */
	public Set<String> rolePermissions(String roleId) {
		return Set.copyOf(this.roles.approvedPermissionIds(roleId));
	}

	/** Drops the cached roles of one session after its IdP roles were re-evaluated. */
	public void evictSession(UUID sessionId) {
		this.cache.keySet().removeIf((key) -> key.contains(":" + sessionId + ":"));
	}

	/** Drops cached permission sets after changes of roles, mappings or assignments. */
	public void evictAll() {
		this.cache.clear();
	}

	private Cached resolve(OeaPrincipal principal) {
		Instant now = this.clock.instant();
		// IdP-derived roles belong to the session, so persons are cached per session
		String key = principal.type() + ":" + principal.systemAdminAccountId() + ":" + principal.personId() + ":"
				+ principal.sessionId() + ":" + principal.secondFactorVerified();
		Cached cached = this.cache.get(key);
		if (cached != null && cached.at().plus(MAXIMUM_AGE).isAfter(now)) {
			return cached;
		}
		List<RoleGrant> grants = new ArrayList<>();
		if (principal.type() == OeaPrincipal.Type.SYSTEM_ADMIN_ACCOUNT) {
			// the local system admin account is governed by ADR-042, not by SoD-8 of persons
			grants.add(grant(SYSTEM_ADMIN_ROLE, "system-admin-account", null, true));
		}
		else {
			if (principal.sessionId() != null) {
				for (IdpRoles.DerivedRole derived : this.idpRoles.effective(principal.sessionId())) {
					grants.add(grant(derived.roleId(), "idp", derived.mappingId(), principal.secondFactorVerified()));
				}
			}
			if (principal.personId() != null) {
				// local assignments (US-144; design D2 of us-144-local-role-assignments): granted, valid, person active
				for (String roleId : this.roles.effectiveLocalRoleIds(principal.personId(), now)) {
					grants.add(grant(roleId, "local", null, principal.secondFactorVerified()));
				}
			}
		}
		Set<String> permissions = new TreeSet<>();
		for (RoleGrant grant : grants) {
			if (grant.effective()) {
				permissions.addAll(this.roles.approvedPermissionIds(grant.roleId()));
			}
		}
		Cached result = new Cached(List.copyOf(grants), Set.copyOf(permissions), now);
		this.cache.put(key, result);
		return result;
	}

	private RoleGrant grant(String roleId, String source, UUID mappingId, boolean secondFactorVerified) {
		RoleStore.RoleSummary role = this.roles.summary(roleId);
		boolean privileged = role.privileged();
		boolean effective = !privileged || secondFactorVerified || "system-admin-account".equals(source);
		return new RoleGrant(roleId, role.name(), privileged, source, mappingId, effective,
				effective ? null : "mfa-required");
	}

}
