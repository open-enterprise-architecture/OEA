package io.oea.app.access;

import io.oea.app.security.PermissionResolver;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every expired local assignment once as {@code role.assignment-expired} (US-144 AC3;
 * design D4 of us-144-local-role-assignments). Whether a role is effective is decided by
 * {@code valid_to} alone, so a delayed run never extends access.
 */
@Component
public class RoleAssignmentExpiry {

	private final JdbcTemplate jdbc;

	private final AuditWriter audit;

	private final PermissionResolver permissions;

	private final Clock clock;

	public RoleAssignmentExpiry(JdbcTemplate jdbc, AuditWriter audit, PermissionResolver permissions, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.permissions = permissions;
		this.clock = clock;
	}

	/** @return the number of expiries recorded in this run */
	@Scheduled(fixedDelayString = "${oea.role-assignments.expiry-check-interval:PT1M}",
			initialDelayString = "${oea.role-assignments.expiry-check-interval:PT1M}")
	@Transactional
	public int recordExpiries() {
		Instant now = this.clock.instant();
		record Expired(UUID id, long personId, String roleId, Instant validTo) {
		}
		List<Expired> expired = this.jdbc.query("""
				SELECT id, person_id, role_id, valid_to FROM role_assignments
				WHERE status = 'granted' AND valid_to <= ? AND expiry_recorded_at IS NULL
				ORDER BY valid_to, id FOR UPDATE SKIP LOCKED""",
				(rs, n) -> new Expired(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3),
						rs.getTimestamp(4).toInstant()),
				Timestamp.from(now));
		for (Expired assignment : expired) {
			this.jdbc.update("UPDATE role_assignments SET expiry_recorded_at = ? WHERE id = ?", Timestamp.from(now),
					assignment.id());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("personId", assignment.personId());
			details.put("roleId", assignment.roleId());
			details.put("validTo", assignment.validTo().toString());
			this.audit.append(new AuditEvent("role.assignment-expired", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.JOB, AuditEvent.Actor.system(),
					"role-assignment", assignment.id().toString(), details));
		}
		if (!expired.isEmpty()) {
			this.permissions.evictAll();
		}
		return expired.size();
	}

}
