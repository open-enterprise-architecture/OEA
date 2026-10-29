package io.oea.app.security;

import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

	private final JdbcTemplate jdbc;

	private final IdpRoles idpRoles;

	private final Clock clock;

	private final Map<String, Cached> cache = new ConcurrentHashMap<>();

	public PermissionResolver(JdbcTemplate jdbc, IdpRoles idpRoles, Clock clock) {
		this.jdbc = jdbc;
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
		return Set.copyOf(this.jdbc.queryForList(
				"SELECT permission_id FROM role_permissions WHERE role_id = ? AND approval_status = 'approved'",
				String.class, roleId));
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
				for (String roleId : this.jdbc.queryForList("""
						SELECT a.role_id FROM role_assignments a JOIN persons p ON p.id = a.person_id
						WHERE a.person_id = ? AND a.status = 'granted' AND a.valid_from <= ?
						  AND (a.valid_to IS NULL OR a.valid_to > ?) AND p.status = 'active'
						ORDER BY a.role_id""", String.class, principal.personId(), Timestamp.from(now),
						Timestamp.from(now))) {
					grants.add(grant(roleId, "local", null, principal.secondFactorVerified()));
				}
			}
		}
		Set<String> permissions = new TreeSet<>();
		for (RoleGrant grant : grants) {
			if (grant.effective()) {
				permissions.addAll(this.jdbc.queryForList("""
						SELECT permission_id FROM role_permissions
						WHERE role_id = ? AND approval_status = 'approved'""", String.class, grant.roleId()));
			}
		}
		Cached result = new Cached(List.copyOf(grants), Set.copyOf(permissions), now);
		this.cache.put(key, result);
		return result;
	}

	private RoleGrant grant(String roleId, String source, UUID mappingId, boolean secondFactorVerified) {
		Map<String, Object> role = this.jdbc.queryForMap("""
				SELECT r.name, EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
				                       WHERE rp.role_id = r.id AND p.admin_class) AS privileged
				FROM authorization_roles r WHERE r.id = ?""", roleId);
		boolean privileged = Boolean.TRUE.equals(role.get("privileged"));
		boolean effective = !privileged || secondFactorVerified || "system-admin-account".equals(source);
		return new RoleGrant(roleId, (String) role.get("name"), privileged, source, mappingId, effective,
				effective ? null : "mfa-required");
	}

}
