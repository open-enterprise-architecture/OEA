package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.app.shared.InvalidAccessRequestException;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.access.PersonStore;
import io.oea.core.access.RoleStore;
import io.oea.core.access.SessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local role assignments (US-144, ADR-032 §7, DM-4; design of us-144-local-role-assignments):
 * assignment with validity and the SoD rules of D5, revocation that ends the person's sessions
 * (D3) and is guarded by SoD-7 (D6). Effectiveness is decided by {@link PermissionResolver} (D2).
 */
@Service
public class RoleAssignments {

	static final String SYSTEM_ADMIN = "system-admin";

	private static final Set<String> VISIBILITY = Set.of("visibility:configure", "visibility-scope:assign");

	private final PersonStore persons;

	private final RoleStore roles;

	private final SessionStore sessionStore;

	private final AccessCatalog catalog;

	private final AdministrationPaths paths;

	private final PermissionDenials denials;

	private final PermissionResolver permissions;

	private final SetupPhase setupPhase;

	private final AuditWriter audit;

	private final Clock clock;

	/** Granting or revoking principal, for {@code grantedBy}/{@code revokedBy}. */
	public record Actor(boolean systemAdminAccount, Long personId, String displayName) {
	}

	/** An assignment as shown to administrators; {@code status} as in the API (design D1). */
	public record Assignment(UUID id, long personId, String roleId, String roleName, boolean privileged,
			String status, Instant validFrom, Instant validTo, String reason, Actor grantedBy, Instant grantedAt,
			Actor revokedBy, Instant revokedAt, boolean sodConflict) {
	}

	/** Input of an assignment. */
	public record NewAssignment(String roleId, Instant validFrom, Instant validTo, String reason,
			boolean acknowledgeSodConflict) {
	}

	public RoleAssignments(PersonStore persons, RoleStore roles, SessionStore sessionStore, AccessCatalog catalog, AdministrationPaths paths,
			PermissionDenials denials, PermissionResolver permissions, SetupPhase setupPhase, AuditWriter audit,
			Clock clock) {
		this.persons = persons;
		this.roles = roles;
		this.sessionStore = sessionStore;
		this.catalog = catalog;
		this.paths = paths;
		this.denials = denials;
		this.permissions = permissions;
		this.setupPhase = setupPhase;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Assignments of a person with one of the statuses ({@code scheduled}, {@code active},
	 * {@code expired}, {@code revoked}).
	 * @throws NoSuchElementException for an unknown person
	 */
	@Transactional(readOnly = true)
	public List<Assignment> list(long personId, Set<String> statuses) {
		personType(personId);
		Instant now = this.clock.instant();
		return this.roles.assignmentsOf(personId)
			.stream()
			.map((row) -> assignment(row, now))
			.filter((assignment) -> statuses.contains(assignment.status()))
			.toList();
	}

	/**
	 * Creates a local assignment.
	 * @throws NoSuchElementException for an unknown person
	 * @throws InvalidAccessRequestException for an unknown role, an invalid validity or a role
	 * not assignable to a service account (SoD-4)
	 * @throws SelfAdministrationException for an own assignment (SoD-1)
	 * @throws SodConflictException for an unacknowledged SoD-3 combination
	 * @throws AccessConflictException if the role is already assigned and not expired
	 */
	@Transactional
	public Assignment create(OeaPrincipal principal, long personId, NewAssignment request) {
		String personType = personType(personId);
		rejectSelfAdministration(principal, personId, "createRoleAssignment");
		AccessCatalog.RoleEntry role;
		try {
			role = this.catalog.role(request.roleId());
		}
		catch (NoSuchElementException ex) {
			throw new InvalidAccessRequestException("roleId", "The role does not exist.");
		}
		// PostgreSQL keeps microseconds and rounds; a default validFrom must not end up after now
		Instant now = this.clock.instant().truncatedTo(ChronoUnit.MICROS);
		Instant validFrom = (request.validFrom() != null) ? request.validFrom() : now;
		if (request.validTo() != null && !request.validTo().isAfter(validFrom)) {
			throw new InvalidAccessRequestException("validTo", "validTo must be after validFrom.");
		}
		if ("service-account".equals(personType) && !role.assignableToServiceAccounts()) {
			// SoD-4: service accounts never hold privileged roles
			throw new InvalidAccessRequestException("roleId", "The role cannot be assigned to a service account.");
		}
		if (this.roles.isAssigned(personId, role.id(), now)) {
			throw new AccessConflictException("The role is already assigned to the person.");
		}
		boolean sodConflict = combinesMetamodelAndVisibility(personId, role, now);
		if (sodConflict && !request.acknowledgeSodConflict()) {
			throw new SodConflictException();
		}
		this.setupPhase.requireSecondFactorFor("role-assignment", principal);
		UUID id = UUID.randomUUID();
		this.roles.insertAssignment(id, personId, role.id(), validFrom, request.validTo(), request.reason(), now,
				principal.personId(), principal.systemAdminAccountId(), sodConflict);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		details.put("roleId", role.id());
		details.put("validFrom", validFrom.toString());
		if (request.validTo() != null) {
			details.put("validTo", request.validTo().toString());
		}
		if (request.reason() != null) {
			details.put("reason", request.reason());
		}
		details.put("sodConflict", sodConflict);
		append(principal, "role.assigned", role.privileged() ? AuditEvent.Severity.HIGH : AuditEvent.Severity.INFO,
				id, details);
		this.permissions.evictAll();
		return get(personId, id);
	}

	/**
	 * Revokes a granted assignment and all sessions of the person.
	 * @throws NoSuchElementException for an unknown person or assignment
	 * @throws SelfAdministrationException for an own assignment (SoD-1)
	 * @throws AccessConflictException if the assignment is already revoked or expired
	 * @throws LastAdminPathException if it is the last usable path to {@code system-admin} (SoD-7)
	 */
	@Transactional
	public Assignment revoke(OeaPrincipal principal, long personId, UUID assignmentId, String reason) {
		personType(personId);
		rejectSelfAdministration(principal, personId, "revokeRoleAssignment");
		Assignment assignment = get(personId, assignmentId);
		if (!"active".equals(assignment.status()) && !"scheduled".equals(assignment.status())) {
			throw new AccessConflictException("The assignment is already " + assignment.status() + ".");
		}
		if (SYSTEM_ADMIN.equals(assignment.roleId())) {
			this.paths.requireRemainingPath(AdministrationPaths.Removal.assignment(assignmentId));
		}
		Instant now = this.clock.instant();
		this.roles.revokeAssignment(assignmentId, now, principal.personId(), principal.systemAdminAccountId(), reason);
		// REQ-164: the person logs in again and gets only the remaining roles
		int sessions = this.sessionStore.revokeAllOfPerson(personId, now, "role-change", principal.personId(),
				principal.systemAdminAccountId());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("personId", personId);
		details.put("roleId", assignment.roleId());
		if (reason != null) {
			details.put("reason", reason);
		}
		details.put("sessionsRevoked", sessions);
		append(principal, "role.revoked", assignment.privileged() ? AuditEvent.Severity.HIGH : AuditEvent.Severity.INFO,
				assignmentId, details);
		this.permissions.evictAll();
		return get(personId, assignmentId);
	}

	/**
	 * Offboarding (US-148 AC2; design D3 of us-148-person-lifecycle): revokes every granted
	 * assignment of the person with the reason "offboarding". The caller has checked SoD-7 for
	 * the person and revokes the sessions.
	 * @return the number of revoked assignments
	 */
	@Transactional
	public int revokeAllForOffboarding(OeaPrincipal principal, long personId) {
		Instant now = this.clock.instant();
		List<Assignment> granted = this.roles.grantedAssignmentsOf(personId)
			.stream()
			.map((row) -> assignment(row, now))
			// expired assignments keep their history unchanged
			.filter((assignment) -> !"expired".equals(assignment.status()))
			.toList();
		for (Assignment assignment : granted) {
			this.roles.revokeAssignment(assignment.id(), now, principal.personId(), principal.systemAdminAccountId(),
					"offboarding");
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("personId", personId);
			details.put("roleId", assignment.roleId());
			details.put("reason", "offboarding");
			append(principal, "role.revoked",
					assignment.privileged() ? AuditEvent.Severity.HIGH : AuditEvent.Severity.INFO, assignment.id(),
					details);
		}
		this.permissions.evictAll();
		return granted.size();
	}

	private Assignment get(long personId, UUID assignmentId) {
		Instant now = this.clock.instant();
		return this.roles.assignment(personId, assignmentId)
			.map((row) -> assignment(row, now))
			.orElseThrow(() -> new NoSuchElementException("no assignment " + assignmentId + " of person " + personId));
	}

	private String personType(long personId) {
		return this.persons.personType(personId)
			.orElseThrow(() -> new NoSuchElementException("no person " + personId));
	}

	/** SoD-1 (ADR-032): no principal administers its own role assignments. */
	private void rejectSelfAdministration(OeaPrincipal principal, long personId, String operation) {
		if (principal.personId() != null && principal.personId() == personId) {
			this.denials.record(principal, operation, "person", Long.toString(personId),
					PermissionDenials.SELF_ADMINISTRATION);
			throw new SelfAdministrationException();
		}
	}

	/** SoD-3 (ADR-032): the person's local roles plus the new one combine metamodel and visibility administration. */
	private boolean combinesMetamodelAndVisibility(long personId, AccessCatalog.RoleEntry role, Instant now) {
		List<String> held = new ArrayList<>(this.roles.heldPermissionIds(personId, now));
		held.addAll(role.permissions());
		return held.contains("metamodel:write") && held.stream().anyMatch(VISIBILITY::contains);
	}

	private void append(OeaPrincipal principal, String eventType, AuditEvent.Severity severity, UUID assignmentId,
			Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.SECURITY, severity, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "role-assignment", assignmentId.toString(), details));
	}

	private static Assignment assignment(RoleStore.AssignmentRow row, Instant now) {
		Instant validTo = row.validTo();
		String status;
		if ("revoked".equals(row.status())) {
			status = "revoked";
		}
		else if (validTo != null && !validTo.isAfter(now)) {
			status = "expired";
		}
		else if (row.validFrom().isAfter(now)) {
			status = "scheduled";
		}
		else {
			status = "active";
		}
		return new Assignment(row.id(), row.personId(), row.roleId(), row.roleName(), row.privileged(), status,
				row.validFrom(), validTo, row.reason(),
				actor(row.grantedByAdminId(), row.grantedByPersonId(), row.grantedByPersonName(),
						row.grantedByAdminName()),
				row.grantedAt(),
				(row.revokedAt() != null) ? actor(row.revokedByAdminId(), row.revokedByPersonId(),
						row.revokedByPersonName(), row.revokedByAdminName()) : null,
				row.revokedAt(), row.sodConflict());
	}

	private static Actor actor(UUID adminId, long personId, String personName, String adminName) {
		if (adminId != null) {
			return new Actor(true, null, adminName);
		}
		return new Actor(false, personId, personName);
	}

}
