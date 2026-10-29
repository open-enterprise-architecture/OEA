package io.oea.app.access;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Usable paths to {@code system-admin} (ADR-032 SoD-7; design D3 of us-019-admin-safeguards, D6 of
 * us-144-local-role-assignments): the local system admin account unless deactivated, IdP group
 * mappings that have granted the role in a login, and open-ended assignments of active persons.
 * Every change that can remove a path checks the remaining paths under one advisory lock, so
 * concurrent changes cannot both remove the last two paths.
 */
@Service
public class AdministrationPaths {

	/** Key of the transaction-scoped advisory lock that serializes SoD-7 checks. */
	private static final long LOCK_KEY = 0x0EA5_0D07L;

	private final JdbcTemplate jdbc;

	public AdministrationPaths(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The path a change removes; {@code null} fields remove nothing of that kind. */
	public record Removal(boolean removesLocalAccount, UUID mappingId, UUID assignmentId, Long personId) {

		public static Removal localAccount() {
			return new Removal(true, null, null, null);
		}

		public static Removal mapping(UUID mappingId) {
			return new Removal(false, mappingId, null, null);
		}

		public static Removal assignment(UUID assignmentId) {
			return new Removal(false, null, assignmentId, null);
		}

		/** All assignments of a person who leaves {@code active} (us-148-person-lifecycle). */
		public static Removal person(long personId) {
			return new Removal(false, null, null, personId);
		}

	}

	/**
	 * Locks the SoD-7 check for the current transaction and rejects the change if no usable path
	 * would remain.
	 * @throws LastAdminPathException if the removal leaves no usable path
	 */
	public void requireRemainingPath(Removal removal) {
		this.jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs) -> null, LOCK_KEY);
		if (remainingPaths(removal) == 0) {
			throw new LastAdminPathException();
		}
	}

	/** Whether the mapping currently counts as a usable path. */
	public boolean isUsableMapping(UUID mappingId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (" + USABLE_MAPPINGS + " AND m.id = ?)",
				Boolean.class, mappingId));
	}

	/** Whether the person holds a usable path: an open-ended, valid {@code system-admin} assignment while active. */
	public boolean isPersonPath(long personId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM role_assignments a JOIN persons p ON p.id = a.person_id
				               WHERE a.person_id = ? AND a.role_id = 'system-admin' AND a.status = 'granted'
				                 AND a.valid_from <= now() AND a.valid_to IS NULL AND p.status = 'active')""",
				Boolean.class, personId));
	}

	private static final String USABLE_MAPPINGS = """
			SELECT 1 FROM idp_group_mappings m JOIN auth_providers p ON p.id = m.auth_provider_id
			WHERE m.role_id = 'system-admin' AND m.active AND m.approval_status = 'approved'
			  AND m.deleted_at IS NULL AND p.active AND m.last_matched_at IS NOT NULL""";

	private int remainingPaths(Removal removal) {
		Integer local = removal.removesLocalAccount() ? 0 : this.jdbc.queryForObject(
				"SELECT count(*) FROM system_admin_accounts WHERE status <> 'deactivated'", Integer.class);
		Integer mappings = this.jdbc.queryForObject(
				"SELECT count(*) FROM (" + USABLE_MAPPINGS + " AND m.id IS DISTINCT FROM ?) usable", Integer.class,
				removal.mappingId());
		Integer persons = this.jdbc.queryForObject("""
				SELECT count(*) FROM role_assignments a JOIN persons p ON p.id = a.person_id
				WHERE a.role_id = 'system-admin' AND a.status = 'granted' AND a.valid_from <= now()
				  AND a.valid_to IS NULL AND p.status = 'active' AND a.id IS DISTINCT FROM ?
				  AND a.person_id IS DISTINCT FROM ?""", Integer.class, removal.assignmentId(), removal.personId());
		return nullToZero(local) + nullToZero(mappings) + nullToZero(persons);
	}

	private static int nullToZero(Integer value) {
		return (value != null) ? value : 0;
	}

}
