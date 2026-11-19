package io.oea.core.access;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code sessions} (revocations and group reads) and {@code session_idp_roles} of the
 * identity packages (S17 change 3, ADR-051); the JPA {@code LoginSessionRepository} stays.
 */
@Repository
public class SessionStore {

	/** A role of a session derived from an IdP group mapping. */
	public record DerivedRole(UUID mappingId, String roleId) {
	}

	private final JdbcTemplate jdbc;

	public SessionStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Revokes every open session of the person and returns their IDs. */
	public List<UUID> revokeAllOfPersonReturningIds(long personId, Instant at, String reason, Long byPersonId,
			UUID byAdminId) {
		return this.jdbc.queryForList("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = ?, revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE person_id = ? AND revoked_at IS NULL RETURNING id""", UUID.class, Timestamp.from(at), reason,
				byPersonId, byAdminId, personId);
	}

	/** Revokes every open session of the person; returns their number. */
	public int revokeAllOfPerson(long personId, Instant at, String reason, Long byPersonId, UUID byAdminId) {
		return this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = ?, revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE person_id = ? AND revoked_at IS NULL""", Timestamp.from(at), reason, byPersonId, byAdminId,
				personId);
	}

	/** Revokes every open session of the principal except the current one; returns their number. */
	public int revokeOthers(UUID currentSessionId, Long personId, UUID systemAdminAccountId, Instant at, String reason,
			Long byPersonId, UUID byAdminId) {
		return this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = ?, revoked_by_person_id = ?,
				       revoked_by_admin_id = ?
				WHERE id <> ? AND revoked_at IS NULL AND (person_id = ? OR system_admin_account_id = ?)""",
				Timestamp.from(at), reason, byPersonId, byAdminId, currentSessionId, personId, systemAdminAccountId);
	}

	/** Revokes every open session of the system admin account; returns their number. */
	public int revokeAllOfAdmin(UUID systemAdminAccountId, Instant at, String reason, UUID byAdminId,
			Long byPersonId) {
		return this.jdbc.update("""
				UPDATE sessions SET revoked_at = ?, revoked_reason = ?, revoked_by_admin_id = ?,
				       revoked_by_person_id = ?
				WHERE system_admin_account_id = ? AND revoked_at IS NULL""", Timestamp.from(at), reason, byAdminId,
				byPersonId, systemAdminAccountId);
	}

	/** Whether an open session of the provider carries the group in its groups claim. */
	public boolean hasOpenSessionWithGroup(UUID authProviderId, Instant now, String groupIdentifier) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM sessions
				               WHERE auth_provider_id = ? AND revoked_at IS NULL AND absolute_expires_at > ?
				                 AND ? = ANY (idp_group_identifiers))""", Boolean.class, authProviderId,
				Timestamp.from(now), groupIdentifier));
	}

	/** Whether the group is in the groups claim of the session at the issuer. */
	public boolean sessionHasGroup(String groupIdentifier, UUID sessionId, String issuer) {
		return Boolean.TRUE.equals(this.jdbc.query("""
				SELECT ? = ANY (s.idp_group_identifiers)
				FROM sessions s JOIN auth_providers p ON p.id = s.auth_provider_id
				WHERE s.id = ? AND p.issuer = ?""", (rs) -> rs.next() && rs.getBoolean(1), groupIdentifier, sessionId,
				issuer));
	}

	/** The roles derived for the session, ordered by role. */
	public List<DerivedRole> derivedRoles(UUID sessionId) {
		return this.jdbc.query(
				"SELECT idp_group_mapping_id, role_id FROM session_idp_roles WHERE session_id = ? ORDER BY role_id, "
						+ "idp_group_mapping_id",
				(rs, n) -> new DerivedRole(rs.getObject(1, UUID.class), rs.getString(2)), sessionId);
	}

	/** The derived roles of the session whose mappings still apply. */
	public List<DerivedRole> effectiveDerivedRoles(UUID sessionId) {
		return this.jdbc.query("""
				SELECT r.idp_group_mapping_id, r.role_id FROM session_idp_roles r
				JOIN idp_group_mappings m ON m.id = r.idp_group_mapping_id
				WHERE r.session_id = ? AND m.active AND m.deleted_at IS NULL ORDER BY r.role_id""",
				(rs, n) -> new DerivedRole(rs.getObject(1, UUID.class), rs.getString(2)), sessionId);
	}

	/** Deletes the derived roles of the session. */
	public void deleteDerivedRoles(UUID sessionId) {
		this.jdbc.update("DELETE FROM session_idp_roles WHERE session_id = ?", sessionId);
	}

	/** Stores one derived role of the session. */
	public void insertDerivedRole(UUID sessionId, UUID mappingId, String roleId, Instant derivedAt) {
		this.jdbc.update("""
				INSERT INTO session_idp_roles (session_id, idp_group_mapping_id, role_id, derived_at)
				VALUES (?, ?, ?, ?)""", sessionId, mappingId, roleId, Timestamp.from(derivedAt));
	}

}
