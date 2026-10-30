package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforcement of a second factor at the password login of persons (US-031, US-028, REQ-020,
 * REQ-029; design D1, D2, D5 of us-031-second-factor-enforcement): the policy in
 * {@code system_settings}, the operator floor, and whether a person must enroll a second factor.
 */
@Service
public class SecondFactorPolicies {

	private final JdbcTemplate jdbc;

	private final SecondFactorProperties properties;

	private final AuditWriter audit;

	private final Clock clock;

	public SecondFactorPolicies(JdbcTemplate jdbc, SecondFactorProperties properties, AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	/** The stored policy and the operator floor. */
	public record View(boolean enforced, List<String> exemptRoles, boolean enforcedByOperator) {
	}

	public View view() {
		Stored stored = stored();
		return new View(stored.enforced() || this.properties.enforced(), stored.exemptRoles(),
				this.properties.enforced());
	}

	/** Whether the enforcement is effective: forced by the operator or switched on (D1). */
	public boolean enforced() {
		return this.properties.enforced() || stored().enforced();
	}

	/**
	 * Replaces the policy (D1).
	 * @throws InvalidAccessRequestException naming {@code enforced} or {@code exemptRoles}
	 */
	@Transactional
	public View update(OeaPrincipal principal, boolean enforced, List<String> exemptRoles) {
		Stored old = stored();
		List<String> roles = List.copyOf(new LinkedHashSet<>((exemptRoles != null) ? exemptRoles : List.of()));
		List<String> invalid = new ArrayList<>();
		if (!enforced && this.properties.enforced()) {
			invalid.add("enforced");
		}
		if (!roles.isEmpty()) {
			Integer allowed = this.jdbc.queryForObject("""
					SELECT count(*) FROM authorization_roles r
					WHERE r.id = ANY (?) AND NOT EXISTS (SELECT 1 FROM role_permissions rp
					                                    JOIN permissions p ON p.id = rp.permission_id
					                                    WHERE rp.role_id = r.id AND p.admin_class)""",
					Integer.class, (Object) roles.toArray(String[]::new));
			if (allowed == null || allowed != roles.size()) {
				invalid.add("exemptRoles");
			}
		}
		if (!invalid.isEmpty()) {
			throw new InvalidAccessRequestException(invalid, "The second-factor policy is invalid: an operator floor, "
					+ "an unknown role or a role with an administrative permission (" + String.join(", ", invalid) + ").");
		}
		int updated = this.jdbc.update("""
				UPDATE system_settings SET second_factor_enforced = ?, second_factor_exempt_roles = ?, updated_at = ?,
				       updated_by_person_id = ?, updated_by_admin_id = ?
				WHERE id = 1""", enforced, roles.toArray(String[]::new), Timestamp.from(this.clock.instant()),
				principal.personId(), principal.systemAdminAccountId());
		if (updated != 1) {
			throw new IllegalStateException("the instance has no settings yet");
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("old", Map.of("enforced", old.enforced(), "exemptRoles", old.exemptRoles()));
		details.put("new", Map.of("enforced", enforced, "exemptRoles", roles));
		this.audit.append(new AuditEvent("settings.second-factor-policy-changed", AuditEvent.Category.SECURITY,
				AuditEvent.Severity.HIGH, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"settings", "second-factor-policy", details));
		return view();
	}

	/** D2: a person must enroll a second factor before anything else. */
	public boolean enrollmentRequired(long personId) {
		return enrollmentRequired(personId, null);
	}

	/**
	 * D5: whether a person would have to enroll if the given second factor were gone.
	 * @param ignoredCredential a credential to disregard, or {@code null}
	 */
	public boolean enrollmentRequired(long personId, UUID ignoredCredential) {
		Stored stored = stored();
		if (!this.properties.enforced() && !stored.enforced()) {
			return false;
		}
		Integer factors = this.jdbc.queryForObject("""
				SELECT count(*) FROM local_credentials WHERE person_id = ? AND type IN ('totp', 'passkey')
				  AND status = 'active' AND (?::uuid IS NULL OR id <> ?::uuid)""", Integer.class, personId,
				ignoredCredential, ignoredCredential);
		if (factors != null && factors > 0) {
			return false;
		}
		return !exempt(personId, stored.exemptRoles(), this.clock.instant());
	}

	/** REQ-029: at least one role through a local role assignment, and every such role exempt. */
	private boolean exempt(long personId, List<String> exemptRoles, Instant now) {
		Set<String> roles = new LinkedHashSet<>(this.jdbc.queryForList("""
				SELECT DISTINCT role_id FROM role_assignments
				WHERE person_id = ? AND status = 'granted' AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)""",
				String.class, personId, Timestamp.from(now), Timestamp.from(now)));
		return !roles.isEmpty() && exemptRoles.containsAll(roles);
	}

	private record Stored(boolean enforced, List<String> exemptRoles) {
	}

	private Stored stored() {
		List<Stored> rows = this.jdbc.query(
				"SELECT second_factor_enforced, second_factor_exempt_roles FROM system_settings WHERE id = 1",
				(rs, n) -> {
					Array array = rs.getArray(2);
					List<String> roles = (array != null) ? Arrays.asList((String[]) array.getArray()) : List.of();
					return new Stored(rs.getBoolean(1), List.copyOf(roles));
				});
		return rows.isEmpty() ? new Stored(false, List.of()) : rows.getFirst();
	}

}
