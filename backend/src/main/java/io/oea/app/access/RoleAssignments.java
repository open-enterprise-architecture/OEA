package io.oea.app.access;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDenials;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.security.SetupPhase;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

	private final JdbcTemplate jdbc;

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

	public RoleAssignments(JdbcTemplate jdbc, AccessCatalog catalog, AdministrationPaths paths,
			PermissionDenials denials, PermissionResolver permissions, SetupPhase setupPhase, AuditWriter audit,
			Clock clock) {
		this.jdbc = jdbc;
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
		return this.jdbc.query(SELECT + " WHERE a.person_id = ? ORDER BY a.granted_at, a.id",
				(rs, n) -> assignment(rs, now), personId)
			.stream()
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
		Boolean assigned = this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments WHERE person_id = ? AND role_id = ? AND status = 'granted'
				               AND (valid_to IS NULL OR valid_to > ?))""", Boolean.class, personId, role.id(),
				Timestamp.from(now));
		if (Boolean.TRUE.equals(assigned)) {
			throw new AccessConflictException("The role is already assigned to the person.");
		}
		boolean sodConflict = combinesMetamodelAndVisibility(personId, role, now);
		if (sodConflict && !request.acknowledgeSodConflict()) {
			throw new SodConflictException();
		}
		this.setupPhase.requireSecondFactorFor("role-assignment", principal);
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO role_assignments (id, person_id, role_id, valid_from, valid_to, reason, granted_at,
				                              granted_by_person_id, granted_by_admin_id, sod_conflict)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, personId, role.id(), Timestamp.from(validFrom),
				(request.validTo() != null) ? Timestamp.from(request.validTo()) : null, request.reason(),
				Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(), sodConflict);
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
		this.jdbc.update("""
				UPDATE role_assignments SET status = 'revoked', revoked_at = ?, revoked_by_person_id = ?,
				       revoked_by_admin_id = ?, revocation_reason = ?
				WHERE id = ?""", Timestamp.from(now), principal.personId(), principal.systemAdminAccountId(), reason,
				assignmentId);
		// REQ-164: the person logs in again and gets only the remaining roles
		int sessions = this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = 'role-change', revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE person_id = ? AND revoked_at IS NULL""", Timestamp.from(now), principal.personId(),
				principal.systemAdminAccountId(), personId);
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

	private Assignment get(long personId, UUID assignmentId) {
		Instant now = this.clock.instant();
		List<Assignment> found = this.jdbc.query(SELECT + " WHERE a.person_id = ? AND a.id = ?",
				(rs, n) -> assignment(rs, now), personId, assignmentId);
		if (found.isEmpty()) {
			throw new NoSuchElementException("no assignment " + assignmentId + " of person " + personId);
		}
		return found.getFirst();
	}

	private String personType(long personId) {
		List<String> types = this.jdbc.queryForList("SELECT person_type FROM persons WHERE id = ?", String.class,
				personId);
		if (types.isEmpty()) {
			throw new NoSuchElementException("no person " + personId);
		}
		return types.getFirst();
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
		List<String> held = new ArrayList<>(this.jdbc.queryForList("""
				SELECT DISTINCT rp.permission_id FROM role_assignments a
				JOIN role_permissions rp ON rp.role_id = a.role_id AND rp.approval_status = 'approved'
				WHERE a.person_id = ? AND a.status = 'granted' AND (a.valid_to IS NULL OR a.valid_to > ?)""",
				String.class, personId, Timestamp.from(now)));
		held.addAll(role.permissions());
		return held.contains("metamodel:write") && held.stream().anyMatch(VISIBILITY::contains);
	}

	private void append(OeaPrincipal principal, String eventType, AuditEvent.Severity severity, UUID assignmentId,
			Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.SECURITY, severity, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "role-assignment", assignmentId.toString(), details));
	}

	private static final String SELECT = """
			SELECT a.id, a.person_id, a.role_id, r.name AS role_name,
			       EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
			               WHERE rp.role_id = a.role_id AND p.admin_class) AS privileged,
			       a.status, a.valid_from, a.valid_to, a.reason, a.granted_at, a.sod_conflict,
			       a.granted_by_person_id, gp.name AS granted_by_person, ga.login_name AS granted_by_admin,
			       a.granted_by_admin_id,
			       a.revoked_at, a.revoked_by_person_id, rpn.name AS revoked_by_person, ra.login_name AS revoked_by_admin,
			       a.revoked_by_admin_id
			FROM role_assignments a
			JOIN authorization_roles r ON r.id = a.role_id
			LEFT JOIN persons gp ON gp.id = a.granted_by_person_id
			LEFT JOIN system_admin_accounts ga ON ga.id = a.granted_by_admin_id
			LEFT JOIN persons rpn ON rpn.id = a.revoked_by_person_id
			LEFT JOIN system_admin_accounts ra ON ra.id = a.revoked_by_admin_id""";

	private static Assignment assignment(ResultSet rs, Instant now) throws SQLException {
		Instant validFrom = rs.getTimestamp("valid_from").toInstant();
		Timestamp validToValue = rs.getTimestamp("valid_to");
		Instant validTo = (validToValue != null) ? validToValue.toInstant() : null;
		Timestamp revokedAtValue = rs.getTimestamp("revoked_at");
		String status;
		if ("revoked".equals(rs.getString("status"))) {
			status = "revoked";
		}
		else if (validTo != null && !validTo.isAfter(now)) {
			status = "expired";
		}
		else if (validFrom.isAfter(now)) {
			status = "scheduled";
		}
		else {
			status = "active";
		}
		return new Assignment(rs.getObject("id", UUID.class), rs.getLong("person_id"), rs.getString("role_id"),
				rs.getString("role_name"), rs.getBoolean("privileged"), status, validFrom, validTo,
				rs.getString("reason"),
				actor(rs, "granted_by_person_id", "granted_by_person", "granted_by_admin_id", "granted_by_admin"),
				rs.getTimestamp("granted_at").toInstant(),
				(revokedAtValue != null)
						? actor(rs, "revoked_by_person_id", "revoked_by_person", "revoked_by_admin_id", "revoked_by_admin")
						: null,
				(revokedAtValue != null) ? revokedAtValue.toInstant() : null, rs.getBoolean("sod_conflict"));
	}

	private static Actor actor(ResultSet rs, String personIdColumn, String personNameColumn, String adminIdColumn,
			String adminNameColumn) throws SQLException {
		if (rs.getObject(adminIdColumn) != null) {
			return new Actor(true, null, rs.getString(adminNameColumn));
		}
		long personId = rs.getLong(personIdColumn);
		return new Actor(false, personId, rs.getString(personNameColumn));
	}

}
