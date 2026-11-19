package io.oea.core.access;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The reads behind the usable paths to {@code system-admin} (ADR-032 SoD-7): the advisory lock and
 * the counts over the local account, IdP group mappings and role assignments (S17 change 3).
 */
@Repository
public class AdministrationPathStore {

	private static final String USABLE_MAPPINGS = """
			SELECT 1 FROM idp_group_mappings m JOIN auth_providers p ON p.id = m.auth_provider_id
			WHERE m.role_id = 'system-admin' AND m.active AND m.approval_status = 'approved'
			  AND m.deleted_at IS NULL AND p.active AND m.last_matched_at IS NOT NULL""";

	private final JdbcTemplate jdbc;

	public AdministrationPathStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Takes the transaction-scoped advisory lock with the key; the first statement of a check. */
	public void lockPaths(long key) {
		this.jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs) -> null, key);
	}

	/** Whether the mapping currently counts as a usable path. */
	public boolean isUsableMapping(UUID mappingId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (" + USABLE_MAPPINGS + " AND m.id = ?)",
				Boolean.class, mappingId));
	}

	/** Whether the person holds an open-ended, valid {@code system-admin} assignment while active. */
	public boolean isPersonPath(long personId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments a JOIN persons p ON p.id = a.person_id
				               WHERE a.person_id = ? AND a.role_id = 'system-admin' AND a.status = 'granted'
				                 AND a.valid_from <= now() AND a.valid_to IS NULL AND p.status = 'active')""",
				Boolean.class, personId));
	}

	/** The number of local accounts that are not deactivated. */
	public int localAccountPaths() {
		return nullToZero(this.jdbc.queryForObject(
				"SELECT count(*) FROM system_admin_accounts WHERE status <> 'deactivated'", Integer.class));
	}

	/** The number of usable mappings except the removed one ({@code null} removes none). */
	public int mappingPaths(UUID removedMappingId) {
		return nullToZero(this.jdbc.queryForObject(
				"SELECT count(*) FROM (" + USABLE_MAPPINGS + " AND m.id IS DISTINCT FROM ?) usable", Integer.class,
				removedMappingId));
	}

	/** The number of person paths except the removed assignment and person ({@code null} removes none). */
	public int personPaths(UUID removedAssignmentId, Long removedPersonId) {
		return nullToZero(this.jdbc.queryForObject("""
				SELECT count(*) FROM role_assignments a JOIN persons p ON p.id = a.person_id
				WHERE a.role_id = 'system-admin' AND a.status = 'granted' AND a.valid_from <= now()
				  AND a.valid_to IS NULL AND p.status = 'active' AND a.id IS DISTINCT FROM ?
				  AND a.person_id IS DISTINCT FROM ?""", Integer.class, removedAssignmentId, removedPersonId));
	}

	private static int nullToZero(Integer value) {
		return (value != null) ? value : 0;
	}

}
