package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code authorization_roles}, {@code role_permissions}, {@code permissions} and
 * {@code role_assignments} (S17 change 3, ADR-051). The visibility columns of the roles stay in
 * the {@code VisibilityStore} of the metamodel.
 */
@Repository
public class RoleStore {

	/** A permission of the catalog. */
	public record PermissionRow(String id, String category, boolean adminClass) {
	}

	/** An approved permission of a role. */
	public record RolePermissionRow(String roleId, String permissionId) {
	}

	/** A live authorization role without its permissions. */
	public record RoleRow(String id, String name, String description, boolean builtin,
			boolean assignableToServiceAccounts, boolean overridesPropertyVisibility) {
	}

	/** The name of a role and whether it holds an administrative permission. */
	public record RoleSummary(String name, boolean privileged) {
	}

	/**
	 * A role assignment with its granting and revoking principals as stored. {@code grantedByAdminId} and
	 * {@code revokedByAdminId} are set for the system admin account, {@code revokedAt} only after a revocation.
	 */
	public record AssignmentRow(UUID id, long personId, String roleId, String roleName, boolean privileged,
			String status, Instant validFrom, Instant validTo, String reason, Instant grantedAt, boolean sodConflict,
			long grantedByPersonId, String grantedByPersonName, UUID grantedByAdminId, String grantedByAdminName,
			Instant revokedAt, long revokedByPersonId, String revokedByPersonName, UUID revokedByAdminId,
			String revokedByAdminName) {
	}

	/** An assignment whose expiry is not recorded yet. */
	public record ExpiredAssignment(UUID id, long personId, String roleId, Instant validTo) {
	}

	private static final String SELECT_ASSIGNMENT = """
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

	private final JdbcTemplate jdbc;

	public RoleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The permission catalog in its display order. */
	public List<PermissionRow> permissions() {
		return this.jdbc.query("SELECT id, category, admin_class FROM permissions ORDER BY sort_order, id",
				(rs, n) -> new PermissionRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)));
	}

	/** The IDs of the administrative permissions. */
	public List<String> adminClassPermissionIds() {
		return this.jdbc.queryForList("SELECT id FROM permissions WHERE admin_class", String.class);
	}

	/** The approved permissions of all roles. */
	public List<RolePermissionRow> approvedRolePermissions() {
		return this.jdbc.query("SELECT role_id, permission_id FROM role_permissions WHERE approval_status = 'approved' "
				+ "ORDER BY role_id, permission_id", (rs, n) -> new RolePermissionRow(rs.getString(1), rs.getString(2)));
	}

	/** The live roles, built-in ones first. */
	public List<RoleRow> liveRoles() {
		return this.jdbc.query("""
				SELECT id, name, description, is_builtin, assignable_to_service_accounts, overrides_property_visibility
				FROM authorization_roles WHERE deleted_at IS NULL ORDER BY is_builtin DESC, id""",
				(rs, n) -> new RoleRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
						rs.getBoolean(5), rs.getBoolean(6)));
	}

	/** The approved permission IDs of a role. */
	public List<String> approvedPermissionIds(String roleId) {
		return this.jdbc.queryForList(
				"SELECT permission_id FROM role_permissions WHERE role_id = ? AND approval_status = 'approved'",
				String.class, roleId);
	}

	/** The name and the privilege of a role; fails for an unknown role. */
	public RoleSummary summary(String roleId) {
		return this.jdbc.queryForObject("""
				SELECT r.name, EXISTS (SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
				                       WHERE rp.role_id = r.id AND p.admin_class) AS privileged
				FROM authorization_roles r WHERE r.id = ?""",
				(rs, n) -> new RoleSummary(rs.getString(1), rs.getBoolean(2)), roleId);
	}

	/** Whether a live role has the ID. */
	public boolean existsLiveRole(String roleId) {
		Integer roles = this.jdbc.queryForObject(
				"SELECT count(*) FROM authorization_roles WHERE id = ? AND deleted_at IS NULL", Integer.class, roleId);
		return roles != null && roles != 0;
	}

	/** The number of the given roles that exist and hold no administrative permission. */
	public int countRolesWithoutAdminPermission(List<String> roleIds) {
		Integer allowed = this.jdbc.queryForObject("""
				SELECT count(*) FROM authorization_roles r
				WHERE r.id = ANY (?) AND NOT EXISTS (SELECT 1 FROM role_permissions rp
				                                    JOIN permissions p ON p.id = rp.permission_id
				                                    WHERE rp.role_id = r.id AND p.admin_class)""", Integer.class,
				(Object) roleIds.toArray(String[]::new));
		return (allowed != null) ? allowed : 0;
	}

	/** Inserts a custom role; returns 0 if the ID is taken. */
	public int insertRole(String id, String name, String description, boolean assignableToServiceAccounts,
			Long createdByPersonId, UUID createdByAdminId) {
		return this.jdbc.update("""
				INSERT INTO authorization_roles (id, name, description, is_builtin, assignable_to_service_accounts,
					created_by_person_id, created_by_admin_id)
				VALUES (?, ?, ?, false, ?, ?, ?) ON CONFLICT (id) DO NOTHING""", id, name, description,
				assignableToServiceAccounts, createdByPersonId, createdByAdminId);
	}

	/** Grants an approved permission to a role. */
	public void insertApprovedPermission(String roleId, String permissionId, Long addedByPersonId,
			UUID addedByAdminId) {
		this.jdbc.update("""
				INSERT INTO role_permissions (role_id, permission_id, added_by_person_id, added_by_admin_id,
					approval_status) VALUES (?, ?, ?, ?, 'approved')""", roleId, permissionId, addedByPersonId,
				addedByAdminId);
	}

	/** All assignments of the person, in the order of their granting. */
	public List<AssignmentRow> assignmentsOf(long personId) {
		return this.jdbc.query(SELECT_ASSIGNMENT + " WHERE a.person_id = ? ORDER BY a.granted_at, a.id",
				(rs, n) -> assignment(rs), personId);
	}

	/** The granted assignments of the person, in the order of their granting. */
	public List<AssignmentRow> grantedAssignmentsOf(long personId) {
		return this.jdbc.query(
				SELECT_ASSIGNMENT + " WHERE a.person_id = ? AND a.status = 'granted' ORDER BY a.granted_at, a.id",
				(rs, n) -> assignment(rs), personId);
	}

	/** One assignment of the person. */
	public Optional<AssignmentRow> assignment(long personId, UUID assignmentId) {
		return this.jdbc.query(SELECT_ASSIGNMENT + " WHERE a.person_id = ? AND a.id = ?", (rs, n) -> assignment(rs),
				personId, assignmentId)
			.stream()
			.findFirst();
	}

	/** Whether the person holds the role as granted assignment that has not ended at the time. */
	public boolean isAssigned(long personId, String roleId, Instant now) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments WHERE person_id = ? AND role_id = ? AND status = 'granted'
				               AND (valid_to IS NULL OR valid_to > ?))""", Boolean.class, personId, roleId,
				Timestamp.from(now)));
	}

	/** The permissions the person holds through granted assignments that have not ended at the time. */
	public List<String> heldPermissionIds(long personId, Instant now) {
		return this.jdbc.queryForList("""
				SELECT DISTINCT rp.permission_id FROM role_assignments a
				JOIN role_permissions rp ON rp.role_id = a.role_id AND rp.approval_status = 'approved'
				WHERE a.person_id = ? AND a.status = 'granted' AND (a.valid_to IS NULL OR a.valid_to > ?)""",
				String.class, personId, Timestamp.from(now));
	}

	/** Whether a granted assignment of the person that has not ended holds an administrative permission. */
	public boolean holdsAdminClassPermission(long personId, Instant now) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments a
				               JOIN role_permissions rp ON rp.role_id = a.role_id
				               JOIN permissions p ON p.id = rp.permission_id
				               WHERE a.person_id = ? AND a.status = 'granted' AND p.admin_class
				                 AND (a.valid_to IS NULL OR a.valid_to > ?))""", Boolean.class, personId,
				Timestamp.from(now)));
	}

	/** The roles of the person through granted, valid assignments while the person is active. */
	public List<String> effectiveLocalRoleIds(long personId, Instant now) {
		return this.jdbc.queryForList("""
				SELECT a.role_id FROM role_assignments a JOIN persons p ON p.id = a.person_id
				WHERE a.person_id = ? AND a.status = 'granted' AND a.valid_from <= ?
				  AND (a.valid_to IS NULL OR a.valid_to > ?) AND p.status = 'active'
				ORDER BY a.role_id""", String.class, personId, Timestamp.from(now), Timestamp.from(now));
	}

	/** The distinct roles of the person through granted assignments valid at the time. */
	public List<String> grantedRoleIds(long personId, Instant now) {
		return this.jdbc.queryForList("""
				SELECT DISTINCT role_id FROM role_assignments
				WHERE person_id = ? AND status = 'granted' AND valid_from <= ? AND (valid_to IS NULL OR valid_to > ?)""",
				String.class, personId, Timestamp.from(now), Timestamp.from(now));
	}

	/** Inserts an assignment. */
	public void insertAssignment(UUID id, long personId, String roleId, Instant validFrom, Instant validTo,
			String reason, Instant grantedAt, Long grantedByPersonId, UUID grantedByAdminId, boolean sodConflict) {
		this.jdbc.update("""
				INSERT INTO role_assignments (id, person_id, role_id, valid_from, valid_to, reason, granted_at,
				                              granted_by_person_id, granted_by_admin_id, sod_conflict)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", id, personId, roleId, Timestamp.from(validFrom),
				(validTo != null) ? Timestamp.from(validTo) : null, reason, Timestamp.from(grantedAt),
				grantedByPersonId, grantedByAdminId, sodConflict);
	}

	/** Revokes an assignment. */
	public void revokeAssignment(UUID id, Instant at, Long byPersonId, UUID byAdminId, String reason) {
		this.jdbc.update("""
				UPDATE role_assignments SET status = 'revoked', revoked_at = ?, revoked_by_person_id = ?,
				       revoked_by_admin_id = ?, revocation_reason = ?
				WHERE id = ?""", Timestamp.from(at), byPersonId, byAdminId, reason, id);
	}

	/** The granted assignments whose expiry is due and not recorded, locked ({@code FOR UPDATE SKIP LOCKED}). */
	public List<ExpiredAssignment> lockUnrecordedExpiries(Instant now) {
		return this.jdbc.query("""
				SELECT id, person_id, role_id, valid_to FROM role_assignments
				WHERE status = 'granted' AND valid_to <= ? AND expiry_recorded_at IS NULL
				ORDER BY valid_to, id FOR UPDATE SKIP LOCKED""",
				(rs, n) -> new ExpiredAssignment(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3),
						rs.getTimestamp(4).toInstant()),
				Timestamp.from(now));
	}

	/** Records that the expiry of the assignment was audited. */
	public void markExpiryRecorded(UUID id, Instant at) {
		this.jdbc.update("UPDATE role_assignments SET expiry_recorded_at = ? WHERE id = ?", Timestamp.from(at), id);
	}

	private static AssignmentRow assignment(java.sql.ResultSet rs) throws java.sql.SQLException {
		Timestamp validTo = rs.getTimestamp("valid_to");
		Timestamp revokedAt = rs.getTimestamp("revoked_at");
		return new AssignmentRow(rs.getObject("id", UUID.class), rs.getLong("person_id"), rs.getString("role_id"),
				rs.getString("role_name"), rs.getBoolean("privileged"), rs.getString("status"),
				rs.getTimestamp("valid_from").toInstant(), (validTo != null) ? validTo.toInstant() : null,
				rs.getString("reason"), rs.getTimestamp("granted_at").toInstant(), rs.getBoolean("sod_conflict"),
				rs.getLong("granted_by_person_id"), rs.getString("granted_by_person"),
				rs.getObject("granted_by_admin_id", UUID.class), rs.getString("granted_by_admin"),
				(revokedAt != null) ? revokedAt.toInstant() : null, rs.getLong("revoked_by_person_id"),
				rs.getString("revoked_by_person"), rs.getObject("revoked_by_admin_id", UUID.class),
				rs.getString("revoked_by_admin"));
	}

}
