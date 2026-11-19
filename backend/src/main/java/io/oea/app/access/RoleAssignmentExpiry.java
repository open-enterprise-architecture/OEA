package io.oea.app.access;

import io.oea.app.security.PermissionResolver;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.RoleStore;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	private final RoleStore roles;

	private final AuditWriter audit;

	private final PermissionResolver permissions;

	private final Clock clock;

	public RoleAssignmentExpiry(RoleStore roles, AuditWriter audit, PermissionResolver permissions, Clock clock) {
		this.roles = roles;
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
		List<RoleStore.ExpiredAssignment> expired = this.roles.lockUnrecordedExpiries(now);
		for (RoleStore.ExpiredAssignment assignment : expired) {
			this.roles.markExpiryRecorded(assignment.id(), now);
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
