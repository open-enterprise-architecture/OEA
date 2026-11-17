package io.oea.core.solution;

import io.oea.core.entity.TypedValue;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The content of the basis of a solution (design D4 of us-084-plateaus; US-155): the catalog with
 * the deltas of the active solutions of its layers applied. The layers of a solution that targets
 * a plateau with the basis {@code predecessor} are the predecessor plateaus up to the baseline or
 * up to a plateau with the basis {@code catalog}, farthest first; a solution without plateaus, or
 * one whose plateau has the basis {@code catalog}, has none, and its content is the catalog.
 *
 * <p>
 * Every query starts with the CTE {@link #CONTENT}, whose only parameter is the array of layers.
 * The deltas are ranked by the index of their layer, then by their last change; for a field the
 * last ranked value wins, an entity with a ranked {@code retiring} delta is retiring, and a delta
 * {@code new} gives a proposed row.
 */
@Repository
public class PlateauContentStore {

	/** The CTEs {@code layer}, {@code ranked} and {@code content}; one parameter, the layers. */
	static final String CONTENT = """
			WITH layer (plateau_id, idx) AS (SELECT * FROM unnest(?::uuid[]) WITH ORDINALITY),
			ranked AS (
			    SELECT d.id AS delta_id, d.delta_type, d.entity_id, d.metatype_id, d.name, d.solution_id,
			           s.name AS solution_name, l.idx, coalesce(d.updated_at, d.created_at) AS changed_at
			    FROM entity_deltas d
			    JOIN solutions s ON s.id = d.solution_id
			    JOIN layer l ON l.plateau_id = s.to_plateau_id
			    WHERE s.status NOT IN ('implemented', 'archived')),
			named AS (
			    SELECT DISTINCT ON (r.entity_id) r.entity_id, v.after_value_text AS name
			    FROM ranked r JOIN entity_delta_property_values v ON v.delta_id = r.delta_id AND v.system_field = 'name'
			    WHERE r.delta_type = 'modified'
			    ORDER BY r.entity_id, r.idx DESC, r.changed_at DESC),
			retired AS (
			    SELECT DISTINCT ON (r.entity_id) r.entity_id, r.solution_id, r.solution_name
			    FROM ranked r WHERE r.delta_type = 'retiring'
			    ORDER BY r.entity_id, r.idx DESC, r.changed_at DESC),
			touched AS (
			    SELECT DISTINCT ON (r.entity_id) r.entity_id, r.solution_id, r.solution_name
			    FROM ranked r WHERE r.delta_type = 'modified'
			    ORDER BY r.entity_id, r.idx DESC, r.changed_at DESC),
			content AS (
			    SELECT e.id AS entity_id, coalesce(n.name, e.name) AS name, e.metatype_id, e.version,
			           CASE WHEN t.entity_id IS NOT NULL THEN 'retiring' ELSE 'active' END AS lifecycle,
			           coalesce(t.solution_id, c.solution_id) AS changed_by,
			           coalesce(t.solution_name, c.solution_name) AS changed_by_name,
			           NULL::uuid AS delta_id, 'e' || lpad(e.id::text, 10, '0') AS sort_key
			    FROM entities e
			    LEFT JOIN named n ON n.entity_id = e.id
			    LEFT JOIN retired t ON t.entity_id = e.id
			    LEFT JOIN touched c ON c.entity_id = e.id
			    WHERE e.deleted_at IS NULL
			    UNION ALL
			    SELECT NULL, r.name, r.metatype_id, 0, 'proposed', r.solution_id, r.solution_name, r.delta_id,
			           'p' || r.delta_id::text
			    FROM ranked r WHERE r.delta_type = 'new')
			""";

	private static final String FROM = " FROM content c JOIN metatypes m ON m.id = c.metatype_id"
			+ " LEFT JOIN entity_deltas d ON d.entity_id = c.entity_id AND d.solution_id = ?"
			+ " WHERE NOT m.is_connection";

	private static final String COLUMNS = """
			SELECT c.entity_id, c.name, m.id, m.name, c.version, d.delta_type, m.show_parallel_changes, c.lifecycle,
			       c.changed_by, c.changed_by_name, c.delta_id, lower(c.name), c.sort_key""";

	private final JdbcTemplate jdbc;

	private final PlateauStore plateaus;

	public PlateauContentStore(JdbcTemplate jdbc, PlateauStore plateaus) {
		this.jdbc = jdbc;
		this.plateaus = plateaus;
	}

	/** An entry of the content; {@code entityId} is {@code null} exactly for a proposed one. */
	public record Row(Integer entityId, String name, UUID metatypeId, String metatypeName, int version,
			String deltaType, boolean showParallelChanges, String lifecycle, UUID changedBy, String changedByName,
			UUID deltaId, String sortName, String sortKey) {
	}

	/** A filter of the content. */
	public record Filter(UUID metatypeId, String search) {
	}

	/**
	 * A value of an entity in the content of a layer: the last ranked after value of a property, or
	 * of the system field {@code description}; {@code value} is {@code null} for a removed one.
	 */
	public record Overlay(String systemField, UUID propertyDefId, String propertyName, String dataType,
			TypedValue value, String referenceName, String referenceAbbreviation) {
	}

	/** The layers of the solution, farthest first; empty without a plateau or for the basis catalog. */
	public List<UUID> layers(UUID solutionId) {
		UUID target = this.jdbc
			.queryForList("SELECT to_plateau_id FROM solutions WHERE id = ?", UUID.class, solutionId)
			.stream()
			.filter(java.util.Objects::nonNull)
			.findFirst()
			.orElse(null);
		return (target != null) ? layersOfPlateau(target) : List.of();
	}

	/** The layers of the basis of the plateau, farthest first. */
	public List<UUID> layersOfPlateau(UUID plateauId) {
		List<PlateauStore.PlateauRow> chain = this.plateaus.chain(plateauId);
		List<UUID> layers = new ArrayList<>();
		for (int i = 0; i + 1 < chain.size(); i++) {
			PlateauStore.PlateauRow predecessor = chain.get(i + 1);
			if (!"predecessor".equals(chain.get(i).basis()) || "baseline".equals(predecessor.status())
					|| "realized".equals(predecessor.status())) {
				break;
			}
			layers.add(0, predecessor.id());
		}
		return layers;
	}

	/** The number of entries matching the filter. */
	public long count(List<UUID> layers, UUID solutionId, Filter filter) {
		List<Object> args = new ArrayList<>(List.of(array(layers), solutionId));
		String where = where(filter, args);
		return this.jdbc.queryForObject(CONTENT + "SELECT count(*)" + FROM + where, Long.class, args.toArray());
	}

	/**
	 * One page of entries matching the filter, ordered by name case-insensitively, then by the sort
	 * key; {@code after} is the name and key of the last entry of the previous page, or {@code null}
	 * for paging by offset.
	 */
	public List<Row> page(List<UUID> layers, UUID solutionId, Filter filter, List<Object> after, int limit,
			long offset) {
		List<Object> args = new ArrayList<>(List.of(array(layers), solutionId));
		StringBuilder sql = new StringBuilder(CONTENT).append(COLUMNS).append(FROM).append(where(filter, args));
		if (after != null) {
			sql.append(" AND (lower(c.name), c.sort_key COLLATE \"C\") > (?, ? COLLATE \"C\")");
			args.addAll(after);
		}
		sql.append(" ORDER BY lower(c.name), c.sort_key COLLATE \"C\" LIMIT ?");
		args.add(limit);
		if (after == null) {
			sql.append(" OFFSET ?");
			args.add(offset);
		}
		return this.jdbc.query(sql.toString(), PlateauContentStore::row, args.toArray());
	}

	/** One entity of the content, unless it is deleted or a connection. */
	public Optional<Row> entity(List<UUID> layers, UUID solutionId, int entityId) {
		return this.jdbc
			.query(CONTENT + COLUMNS + FROM + " AND c.entity_id = ?", PlateauContentStore::row, array(layers),
					solutionId, entityId)
			.stream()
			.findFirst();
	}

	/** The description of the entity in the catalog. */
	public String description(int entityId) {
		return this.jdbc.queryForObject("SELECT description FROM entities WHERE id = ?", String.class, entityId);
	}

	/** The values the layers set on the entity: the description and the properties, each the last ranked. */
	public List<Overlay> overlay(List<UUID> layers, int entityId) {
		if (layers.isEmpty()) {
			return List.of();
		}
		return this.jdbc.query(CONTENT + """
				SELECT DISTINCT ON (coalesce(v.system_field, v.property_def_id::text))
				       v.system_field, v.property_def_id, p.name, p.data_type, v.after_value_text, v.after_value_int,
				       v.after_value_bool, v.after_value_date, v.after_value_ref, v.after_value_enum, re.name,
				       rm.abbreviation
				FROM ranked r
				JOIN entity_delta_property_values v ON v.delta_id = r.delta_id
				LEFT JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities re ON re.id = v.after_value_ref
				LEFT JOIN metatypes rm ON rm.id = re.metatype_id
				WHERE r.delta_type = 'modified' AND r.entity_id = ? AND coalesce(v.system_field, '') <> 'name'
				ORDER BY coalesce(v.system_field, v.property_def_id::text), r.idx DESC, r.changed_at DESC""",
				(rs, row) -> new Overlay(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3),
						rs.getString(4), typed(rs, 5), rs.getString(11), rs.getString(12)),
				array(layers), entityId);
	}

	private static String where(Filter filter, List<Object> args) {
		StringBuilder where = new StringBuilder();
		if (filter.metatypeId() != null) {
			where.append(" AND c.metatype_id = ?");
			args.add(filter.metatypeId());
		}
		if (filter.search() != null) {
			where.append(" AND strpos(lower(c.name), lower(?)) > 0");
			args.add(filter.search());
		}
		return where.toString();
	}

	private static String array(List<UUID> layers) {
		return "{" + String.join(",", layers.stream().map(UUID::toString).toList()) + "}";
	}

	private static TypedValue typed(ResultSet rs, int first) throws SQLException {
		String text = rs.getString(first);
		Long integer = rs.getObject(first + 1, Long.class);
		Boolean bool = rs.getObject(first + 2, Boolean.class);
		Date date = rs.getDate(first + 3);
		Integer reference = rs.getObject(first + 4, Integer.class);
		String enumValue = rs.getString(first + 5);
		if (text == null && integer == null && bool == null && date == null && reference == null && enumValue == null) {
			return null;
		}
		return new TypedValue(text, integer, bool, (date != null) ? date.toLocalDate() : null, reference, enumValue);
	}

	private static Row row(ResultSet rs, int row) throws SQLException {
		return new Row(rs.getObject(1, Integer.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
				rs.getInt(5), rs.getString(6), rs.getBoolean(7), rs.getString(8), rs.getObject(9, UUID.class),
				rs.getString(10), rs.getObject(11, UUID.class), rs.getString(12), rs.getString(13));
	}

}
