package io.oea.core.metamodel;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The lineage model of the instance (V54, design D3 of us-065-lineage-model-and-query): which
 * metatypes play the parts data object, data flow, carries-data, data input and data output. One
 * row with a version for optimistic locking, like {@code organization_model}.
 */
@Repository
public class LineageModelStore {

	private static final String SELECT = """
			SELECT m.data_object_metatype_id, m.data_flow_metatype_id, m.carries_data_metatype_id,
			       m.data_input_metatype_id, m.data_output_metatype_id, m.version,
			       (SELECT name FROM metatypes WHERE id = m.data_object_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.data_flow_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.carries_data_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.data_input_metatype_id),
			       (SELECT name FROM metatypes WHERE id = m.data_output_metatype_id)
			FROM lineage_model m WHERE m.id = 1""";

	private final JdbcTemplate jdbc;

	public LineageModelStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The five metatype IDs of the parts, each {@code null} while unset. */
	public record Parts(UUID dataObject, UUID dataFlow, UUID carriesData, UUID dataInput, UUID dataOutput) {
	}

	/** The metatype names of the parts, each {@code null} while unset. */
	public record Names(String dataObject, String dataFlow, String carriesData, String dataInput,
			String dataOutput) {
	}

	/** The parts with their names and the version. */
	public record Row(Parts parts, Names names, int version) {
	}

	/** The row. */
	public Row get() {
		return this.jdbc.queryForObject(SELECT, LineageModelStore::row);
	}

	/** The row, locked for a write ({@code FOR UPDATE}). */
	public Row lock() {
		return this.jdbc.queryForObject(SELECT + " FOR UPDATE OF m", LineageModelStore::row);
	}

	/** Stores the parts and raises the version by one; the caller locked the row and checked the version. */
	public Row update(Parts parts, long personId, Instant now) {
		this.jdbc.update("""
				UPDATE lineage_model SET data_object_metatype_id = ?, data_flow_metatype_id = ?,
				       carries_data_metatype_id = ?, data_input_metatype_id = ?, data_output_metatype_id = ?,
				       version = version + 1, updated_at = ?, updated_by = ?
				WHERE id = 1""", parts.dataObject(), parts.dataFlow(), parts.carriesData(), parts.dataInput(),
				parts.dataOutput(), Timestamp.from(now), personId);
		return get();
	}

	/** Stores the parts without a principal, as a package import does, and raises the version. */
	public Row updateBySystem(Parts parts, Instant now) {
		this.jdbc.update("""
				UPDATE lineage_model SET data_object_metatype_id = ?, data_flow_metatype_id = ?,
				       carries_data_metatype_id = ?, data_input_metatype_id = ?, data_output_metatype_id = ?,
				       version = version + 1, updated_at = ?
				WHERE id = 1""", parts.dataObject(), parts.dataFlow(), parts.carriesData(), parts.dataInput(),
				parts.dataOutput(), Timestamp.from(now));
		return get();
	}

	private static Row row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
		return new Row(
				new Parts(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
						rs.getObject(4, UUID.class), rs.getObject(5, UUID.class)),
				new Names(rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)),
				rs.getInt(6));
	}

}
