package io.oea.core.catalog;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The live query of a catalog (design D3 of us-049-show-catalog-data): the active entities of a
 * metatype (index {@code entities_active}, ADR-019) with the text form of their property values
 * (ADR-022), one page per statement plus one count. Property columns reach the SQL only as
 * property definition IDs and positional aliases, never as names.
 */
@Repository
public class CatalogQueryStore {

	// one value per entity and property definition (entity_property_values_unique)
	private static final String TEXT_FORM = "coalesce(%1$s.value_enum, %1$s.value_text, %1$s.value_int::text, "
			+ "%1$s.value_bool::text, %1$s.value_date::text, %1$s.value_ref::text)";

	private final JdbcTemplate jdbc;

	public CatalogQueryStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A property shown as a column. */
	public record PropertyColumn(UUID propertyDefId, String sqlColumnName, String name) {
	}

	/** A field every entity has (US-047), with its text form and its sort expression per alias. */
	public enum SystemField {

		NAME("%1$s.name", "lower(%1$s.name)"),
		DESCRIPTION("nullif(%1$s.description, '')", "lower(nullif(%1$s.description, ''))"),
		EXTERNAL_ID("%1$s.external_id", "lower(%1$s.external_id)"),
		CREATED_AT("to_char(%1$s.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "%1$s.created_at"),
		UPDATED_AT("to_char(%1$s.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "%1$s.updated_at"),
		ENTITY_VERSION("%1$s.version::text", "%1$s.version");

		private final String text;

		private final String order;

		SystemField(String text, String order) {
			this.text = text;
			this.order = order;
		}

	}

	/** A column of a page: a system field or a property, exactly one of them. */
	public record QueryColumn(SystemField field, PropertyColumn property) {

		public static QueryColumn of(SystemField field) {
			return new QueryColumn(field, null);
		}

		public static QueryColumn of(PropertyColumn property) {
			return new QueryColumn(null, property);
		}

	}

	/** Direction of a join (US-048): the primary entity as source, as target, or either. */
	public enum Direction {

		OUTBOUND, INBOUND, BOTH

	}

	/**
	 * A join (design D3 of us-048-catalog-joins): the active target entities of exactly the target
	 * metatype, reached through active connections of the connection metatype, with their columns.
	 */
	public record Join(UUID connectionMetatypeId, Direction direction, UUID targetMetatypeId,
			List<QueryColumn> columns) {
	}

	/**
	 * The sort of a page: {@code column} is the index into the primary columns, or into the
	 * columns of the rows join {@code join}; {@code column == null} sorts by the entity name.
	 */
	public record Sort(Integer join, Integer column, boolean descending) {

		/** Name ascending, the default order. */
		public static final Sort DEFAULT = new Sort(null, null, false);

		public Sort(Integer column, boolean descending) {
			this(null, column, descending);
		}

	}

	/** A join result: the target entity ({@code null} for none in a rows join) and its values. */
	public record JoinRow(Integer entityId, List<String> values) {
	}

	/**
	 * An entity row with the text form of each column ({@code null} without value) and the result
	 * of each rows join.
	 */
	public record Row(int entityId, String name, List<String> values, List<JoinRow> joins) {
	}

	/**
	 * The property with this name (case-insensitive) if the metatype or one of its base types maps
	 * it on the given date (latest {@code valid_from} not after the date, ADR-022) and its
	 * visibility is {@code public}.
	 */
	public Optional<PropertyColumn> mappedPublicProperty(UUID metatypeId, String name, LocalDate date) {
		return availableProperties(metatypeId, date).values()
			.stream()
			.filter((property) -> property.name().equalsIgnoreCase(name))
			.findFirst();
	}

	/**
	 * The properties a catalog of the metatype can show: mapped to it or one of its base types on
	 * the given date, not retired and {@code public} (US-047), by ID.
	 */
	public Map<UUID, PropertyColumn> availableProperties(UUID metatypeId, LocalDate date) {
		Map<UUID, PropertyColumn> properties = new LinkedHashMap<>();
		this.jdbc.query("""
				WITH RECURSIVE chain (id) AS (
				    SELECT ?::uuid
				    UNION SELECT m.base_metatype_id FROM metatypes m JOIN chain c ON m.id = c.id
				    WHERE m.base_metatype_id IS NOT NULL)
				SELECT p.id, p.sql_column_name, p.name
				FROM (SELECT DISTINCT ON (property_def_id) property_def_id
				      FROM metatype_property_mappings WHERE metatype_id IN (SELECT id FROM chain) AND valid_from <= ?
				      ORDER BY property_def_id, valid_from DESC) active
				JOIN property_definitions p ON p.id = active.property_def_id
				WHERE p.visibility_mode = 'public' AND p.retired_at IS NULL
				ORDER BY lower(p.name), p.id""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new PropertyColumn(id, rs.getString(2), rs.getString(3)));
		}, metatypeId, Date.valueOf(date));
		return properties;
	}

	/**
	 * One page of rows: active entities of the metatype, expanded by the rows joins (their cross
	 * product, an entity without results keeps one row). Rows without a value in the sort column
	 * come last in both directions; enum values follow the order of the enum, values outside it
	 * come after them (#65); ties are ordered by name and ID, then by the join results.
	 */
	public List<Row> page(UUID metatypeId, List<QueryColumn> columns, List<Join> rowsJoins, Sort sort, long offset,
			int limit) {
		Statement sql = new Statement("SELECT e.id, e.name");
		for (int i = 0; i < columns.size(); i++) {
			sql.text(", ").text(text(columns.get(i), "e", "v" + i));
		}
		for (int j = 0; j < rowsJoins.size(); j++) {
			sql.text(", j%d.id".formatted(j));
			List<QueryColumn> joinColumns = rowsJoins.get(j).columns();
			for (int i = 0; i < joinColumns.size(); i++) {
				sql.text(", ").text(text(joinColumns.get(i), "j" + j, "j" + j + "v" + i));
			}
		}
		sql.text(" FROM entities e");
		values(sql, columns, "e", "v");
		for (int j = 0; j < rowsJoins.size(); j++) {
			lateral(sql, rowsJoins.get(j), j);
			values(sql, rowsJoins.get(j).columns(), "j" + j, "j" + j + "v");
		}
		QueryColumn sorted = null;
		String sortAlias = "e";
		String valueAlias = null;
		if (sort.column() != null) {
			if (sort.join() == null) {
				sorted = columns.get(sort.column());
				valueAlias = "v" + sort.column();
			}
			else {
				sorted = rowsJoins.get(sort.join()).columns().get(sort.column());
				sortAlias = "j" + sort.join();
				valueAlias = sortAlias + "v" + sort.column();
			}
		}
		if (sorted != null && sorted.property() != null) {
			sql.text(" LEFT JOIN property_enum_values eo ON eo.property_def_id = %1$s.property_def_id"
				.formatted(valueAlias) + " AND eo.value = %1$s.value_enum".formatted(valueAlias));
		}
		sql.text(" WHERE e.metatype_id = ? AND e.deleted_at IS NULL ORDER BY ").arg(metatypeId);
		String direction = sort.descending() ? "DESC" : "ASC";
		if (sorted == null || (sort.join() == null && sorted.field() == SystemField.NAME)) {
			sql.text("lower(e.name) %s, e.id".formatted(direction));
		}
		else if (sorted.field() != null) {
			sql.text("%s %s NULLS LAST, lower(e.name), e.id".formatted(sorted.field().order.formatted(sortAlias),
					direction));
		}
		else {
			// typed columns first, so that numbers and dates sort by value; only one of them is set
			sql.text("eo.sort_order %s NULLS LAST, ".formatted(direction));
			for (String column : List.of("value_int", "value_date", "value_bool", "value_ref")) {
				sql.text("%s.%s %s NULLS LAST, ".formatted(valueAlias, column, direction));
			}
			sql.text("lower(coalesce(%1$s.value_enum, %1$s.value_text)) %2$s NULLS LAST, lower(e.name), e.id"
				.formatted(valueAlias, direction));
		}
		for (int j = 0; j < rowsJoins.size(); j++) {
			sql.text(", lower(j%1$d.name), j%1$d.id".formatted(j));
		}
		sql.text(" LIMIT ? OFFSET ?").arg(limit).arg(offset);
		return this.jdbc.query(sql.toString(), (rs, rowNum) -> {
			List<String> values = new ArrayList<>(columns.size());
			for (int i = 0; i < columns.size(); i++) {
				values.add(rs.getString(3 + i));
			}
			List<JoinRow> joins = new ArrayList<>(rowsJoins.size());
			int index = 3 + columns.size();
			for (Join join : rowsJoins) {
				Integer target = (Integer) rs.getObject(index++);
				List<String> joinValues = new ArrayList<>(join.columns().size());
				for (int i = 0; i < join.columns().size(); i++) {
					joinValues.add(rs.getString(index++));
				}
				joins.add(new JoinRow(target, joinValues));
			}
			return new Row(rs.getInt(1), rs.getString(2), values, joins);
		}, sql.args());
	}

	/** The number of rows of {@link #page}: active entities, expanded by the rows joins. */
	public long count(UUID metatypeId, List<Join> rowsJoins) {
		Statement sql = new Statement("SELECT count(*) FROM entities e");
		for (int j = 0; j < rowsJoins.size(); j++) {
			lateral(sql, rowsJoins.get(j), j);
		}
		sql.text(" WHERE e.metatype_id = ? AND e.deleted_at IS NULL").arg(metatypeId);
		Long count = this.jdbc.queryForObject(sql.toString(), Long.class, sql.args());
		return (count != null) ? count : 0;
	}

	/** The number of active entities of the metatype. */
	public long count(UUID metatypeId) {
		return count(metatypeId, List.of());
	}

	/**
	 * The results of an aggregate join for the given primary entities, ordered by target name and
	 * ID; an entity without results has no entry.
	 */
	public Map<Integer, List<JoinRow>> aggregate(Join join, List<Integer> entityIds) {
		Map<Integer, List<JoinRow>> results = new LinkedHashMap<>();
		if (entityIds.isEmpty()) {
			return results;
		}
		Statement sql = new Statement("SELECT p.pid, t.id");
		for (int i = 0; i < join.columns().size(); i++) {
			sql.text(", ").text(text(join.columns().get(i), "t", "tv" + i));
		}
		sql.text(" FROM (");
		pairs(sql, join, "= ANY(?)", entityIds.toArray(Integer[]::new));
		sql.text(") p JOIN entities t ON t.id = p.tid AND t.metatype_id = ? AND t.deleted_at IS NULL")
			.arg(join.targetMetatypeId());
		values(sql, join.columns(), "t", "tv");
		sql.text(" ORDER BY p.pid, lower(t.name), t.id");
		this.jdbc.query(sql.toString(), (rs) -> {
			List<String> values = new ArrayList<>(join.columns().size());
			for (int i = 0; i < join.columns().size(); i++) {
				values.add(rs.getString(3 + i));
			}
			results.computeIfAbsent(rs.getInt(1), (k) -> new ArrayList<>()).add(new JoinRow(rs.getInt(2), values));
		}, sql.args());
		return results;
	}

	/** The text form of a column of the entity {@code alias}; property values under {@code valueAlias}. */
	private static String text(QueryColumn column, String alias, String valueAlias) {
		return (column.field() != null) ? column.field().text.formatted(alias) : TEXT_FORM.formatted(valueAlias);
	}

	/** The property value joins of the columns of the entity {@code alias}. */
	private static void values(Statement sql, List<QueryColumn> columns, String alias, String prefix) {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).property() != null) {
				sql.text(" LEFT JOIN entity_property_values %1$s%2$d ON %1$s%2$d.entity_id = %3$s.id"
					.formatted(prefix, i, alias) + " AND %1$s%2$d.property_def_id = ?".formatted(prefix, i))
					.arg(columns.get(i).property().propertyDefId());
			}
		}
	}

	/** A rows join as {@code LEFT JOIN LATERAL} with the alias {@code j<n>}: one row per target. */
	private static void lateral(Statement sql, Join join, int n) {
		sql.text(" LEFT JOIN LATERAL (SELECT t.id, t.name, t.description, t.external_id, t.created_at, t.updated_at,"
				+ " t.version FROM (");
		pairs(sql, join, "= e.id", null);
		sql.text(") p JOIN entities t ON t.id = p.tid AND t.metatype_id = ? AND t.deleted_at IS NULL) j%d ON true"
			.formatted(n)).arg(join.targetMetatypeId());
	}

	/**
	 * The distinct pairs (primary {@code pid}, target {@code tid}) of active connections of the
	 * join's metatype; {@code filter} restricts the primary side, with {@code filterArg} if not
	 * {@code null}.
	 */
	private static void pairs(Statement sql, Join join, String filter, Object filterArg) {
		List<String> sides = switch (join.direction()) {
			case OUTBOUND -> List.of("source");
			case INBOUND -> List.of("target");
			case BOTH -> List.of("source", "target");
		};
		for (int i = 0; i < sides.size(); i++) {
			String primary = sides.get(i);
			String target = "source".equals(primary) ? "target" : "source";
			sql.text((i == 0) ? "" : " UNION ")
				.text("SELECT DISTINCT ec.%1$s_entity_id AS pid, ec.%2$s_entity_id AS tid FROM entity_connections ec"
					.formatted(primary, target))
				.text(" JOIN entities c ON c.id = ec.entity_id AND c.metatype_id = ? AND c.deleted_at IS NULL")
				.arg(join.connectionMetatypeId())
				.text(" WHERE ec.%s_entity_id %s".formatted(primary, filter));
			if (filterArg != null) {
				sql.arg(filterArg);
			}
		}
	}

	/** SQL text with its positional arguments, appended in text order. */
	private static final class Statement {

		private final StringBuilder text;

		private final List<Object> args = new ArrayList<>();

		Statement(String start) {
			this.text = new StringBuilder(start);
		}

		Statement text(String part) {
			this.text.append(part);
			return this;
		}

		Statement arg(Object arg) {
			this.args.add(arg);
			return this;
		}

		Object[] args() {
			return this.args.toArray();
		}

		@Override
		public String toString() {
			return this.text.toString();
		}

	}

}
