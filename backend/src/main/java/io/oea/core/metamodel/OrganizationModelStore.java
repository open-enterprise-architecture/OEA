package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The organization model of the instance (V53, design D2 of us-075-lane-assignments): which
 * metatypes play the parts organizational unit, role, person, belongs-to, performs-role and
 * responsible. One row with a version for optimistic locking, like {@code diagram_settings}.
 */
@Repository
public class OrganizationModelStore {

	private static final String SELECT = """
			SELECT m.org_unit_metatype_id, m.role_metatype_id, m.person_metatype_id, m.belongs_to_metatype_id,
			       m.performs_role_metatype_id, m.responsible_metatype_id, m.version,
			       (SELECT name FROM metatypes WHERE id = m.org_unit_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.role_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.person_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.belongs_to_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.performs_role_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.responsible_metatype_id)
			FROM organization_model m WHERE m.id = 1""";

	private final JdbcTemplate jdbc;

	public OrganizationModelStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The six metatype IDs of the parts, each {@code null} while unset. */
	public record Parts(UUID orgUnit, UUID role, UUID person, UUID belongsTo, UUID performsRole, UUID responsible) {
	}

	/** The metatype names of the parts, each {@code null} while unset. */
	public record Names(String orgUnit, String role, String person, String belongsTo, String performsRole,
			String responsible) {
	}

	/** The parts with their names and the version. */
	public record Row(Parts parts, Names names, int version) {
	}

	/** The row. */
	public Row get() {
		return this.jdbc.queryForObject(SELECT, OrganizationModelStore::row);
	}

	/** The row, locked for a write ({@code FOR UPDATE}). */
	public Row lock() {
		return this.jdbc.queryForObject(SELECT + " FOR UPDATE OF m", OrganizationModelStore::row);
	}

	/** Stores the parts and raises the version by one; the caller locked the row and checked the version. */
	public Row update(Parts parts, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE organization_model SET org_unit_metatype_id = ?, role_metatype_id = ?, person_metatype_id = ?,
				       belongs_to_metatype_id = ?, performs_role_metatype_id = ?, responsible_metatype_id = ?,
				       version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = 1""", parts.orgUnit(), parts.role(), parts.person(), parts.belongsTo(), parts.performsRole(),
				parts.responsible(), Timestamp.from(now), personId);
		return get();
	}

	/** Stores the parts without a principal, as a package import does, and raises the version. */
	public Row updateBySystem(Parts parts, Instant now) {
		this.jdbc.update("""
				UPDATE organization_model SET org_unit_metatype_id = ?, role_metatype_id = ?, person_metatype_id = ?,
				       belongs_to_metatype_id = ?, performs_role_metatype_id = ?, responsible_metatype_id = ?,
				       version = version + 1, updated_at = ?
				WHERE id = 1""", parts.orgUnit(), parts.role(), parts.person(), parts.belongsTo(), parts.performsRole(),
				parts.responsible(), Timestamp.from(now));
		return get();
	}

	private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new Row(
				new Parts(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
						rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class)),
				new Names(rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
						rs.getString(13)),
				rs.getInt(7));
	}

}
