package io.oea.app.security;

import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.domain.auth.LoginSession;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles derived from IdP group mappings (US-147, ADR-032 §7, DM-10; design D7). The groups
 * claim of the login's issuer is matched against the active mappings of that issuer; the
 * result replaces the rows of {@code session_idp_roles}. A missing or truncated groups claim
 * derives nothing (fail closed) and is audited as a warning.
 */
@Service
public class IdpRoles {

	/** Maximum age of an evaluation (owner decision Q16, REQ-165 AC5). */
	public static final java.time.Duration MAXIMUM_AGE = java.time.Duration.ofMinutes(15);

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	private final Clock clock;

	public IdpRoles(JdbcTemplate jdbc, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** Classified groups claim of an ID token. */
	public record GroupsClaim(String status, List<String> identifiers) {

		/** {@code groups} as list; Entra ID overage indicators count as truncated. */
		public static GroupsClaim of(Map<String, Object> claims) {
			Object groups = claims.get("groups");
			if (groups instanceof Collection<?> values) {
				List<String> identifiers = new ArrayList<>();
				values.forEach((value) -> identifiers.add(String.valueOf(value)));
				return new GroupsClaim("present", List.copyOf(identifiers));
			}
			Object claimNames = claims.get("_claim_names");
			if (Boolean.TRUE.equals(claims.get("hasgroups"))
					|| (claimNames instanceof Map<?, ?> names && names.containsKey("groups"))) {
				return new GroupsClaim("truncated", List.of());
			}
			return new GroupsClaim("missing", List.of());
		}

	}

	/** One derived role. */
	public record DerivedRole(UUID mappingId, String roleId) {
	}

	/**
	 * Evaluates the groups claim for the session and replaces its derived roles.
	 *
	 * @param trigger {@code login} or {@code refresh}
	 */
	@Transactional
	public List<DerivedRole> evaluate(LoginSession session, String issuer, GroupsClaim claim,
			boolean secondFactorVerified, String trigger) {
		Instant now = this.clock.instant();
		List<DerivedRole> previous = derived(session.getId());
		List<DerivedRole> derived = "present".equals(claim.status()) && !claim.identifiers().isEmpty()
				? this.jdbc.query("""
						SELECT id, role_id FROM idp_group_mappings
						WHERE issuer = ? AND active AND deleted_at IS NULL AND approval_status = 'approved'
						  AND group_identifier = ANY (?) ORDER BY role_id, id""",
						(rs, n) -> new DerivedRole(rs.getObject(1, UUID.class), rs.getString(2)), issuer,
						claim.identifiers().toArray(String[]::new))
				: List.of();
		replace(session.getId(), derived, now);
		recordMatches(derived, secondFactorVerified, now);
		session.recordIdpEvaluation(claim.status(), "present".equals(claim.status()) ? claim.identifiers() : null,
				secondFactorVerified, now);
		if (!"present".equals(claim.status())) {
			unusable(session, claim.status());
		}
		if ("login".equals(trigger) || !previous.equals(derived)) {
			this.audit.append(new AuditEvent("auth.idp-roles-derived", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(session),
					"session", session.getId().toString(),
					Map.of("trigger", trigger, "roles", derived.stream().map(DerivedRole::roleId).toList(),
							"mappings", derived.stream().map((role) -> role.mappingId().toString()).toList(),
							"groupsClaim", claim.status())));
		}
		return derived;
	}

	/** The IdP could not be asked (unreachable, no refresh token): no derived role applies (Q17). */
	@Transactional
	public void withdraw(LoginSession session, String reason) {
		List<DerivedRole> previous = derived(session.getId());
		replace(session.getId(), List.of(), this.clock.instant());
		unusable(session, reason);
		if (!previous.isEmpty()) {
			this.audit.append(new AuditEvent("auth.idp-roles-derived", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, actor(session),
					"session", session.getId().toString(),
					Map.of("trigger", "refresh", "roles", List.of(), "mappings", List.of(), "groupsClaim", reason)));
		}
	}

	/** Derived roles of a session whose mappings still apply (deleted or deactivated ones do not). */
	@Transactional(readOnly = true)
	public List<DerivedRole> effective(UUID sessionId) {
		return this.jdbc.query("""
				SELECT r.idp_group_mapping_id, r.role_id FROM session_idp_roles r
				JOIN idp_group_mappings m ON m.id = r.idp_group_mapping_id
				WHERE r.session_id = ? AND m.active AND m.deleted_at IS NULL ORDER BY r.role_id""",
				(rs, n) -> new DerivedRole(rs.getObject(1, UUID.class), rs.getString(2)), sessionId);
	}

	private List<DerivedRole> derived(UUID sessionId) {
		return this.jdbc.query(
				"SELECT idp_group_mapping_id, role_id FROM session_idp_roles WHERE session_id = ? ORDER BY role_id, "
						+ "idp_group_mapping_id",
				(rs, n) -> new DerivedRole(rs.getObject(1, UUID.class), rs.getString(2)), sessionId);
	}

	private void replace(UUID sessionId, List<DerivedRole> roles, Instant now) {
		this.jdbc.update("DELETE FROM session_idp_roles WHERE session_id = ?", sessionId);
		for (DerivedRole role : roles) {
			this.jdbc.update("""
					INSERT INTO session_idp_roles (session_id, idp_group_mapping_id, role_id, derived_at)
					VALUES (?, ?, ?, ?)""", sessionId, role.mappingId(), role.roleId(), Timestamp.from(now));
		}
	}

	/**
	 * Records the last match of every mapping whose role is effective (a privileged role only
	 * with a second factor, as in PermissionResolver), at most once per hour (design D2 of
	 * us-019-admin-safeguards).
	 */
	private void recordMatches(List<DerivedRole> derived, boolean secondFactorVerified, Instant now) {
		for (DerivedRole role : derived) {
			this.jdbc.update("""
					UPDATE idp_group_mappings m SET last_matched_at = ?
					WHERE m.id = ? AND (m.last_matched_at IS NULL OR m.last_matched_at < ?)
					  AND (? OR NOT EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
					                        WHERE rp.role_id = m.role_id AND p.admin_class))""",
					Timestamp.from(now), role.mappingId(), Timestamp.from(now.minus(java.time.Duration.ofHours(1))),
					secondFactorVerified);
		}
	}

	private void unusable(LoginSession session, String reason) {
		this.audit.append(new AuditEvent("auth.idp-groups-unusable", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API, actor(session),
				"session", session.getId().toString(), Map.of("reason", reason)));
	}

	private static AuditEvent.Actor actor(LoginSession session) {
		return new AuditEvent.Actor("person", session.getPersonId(), null);
	}

}
