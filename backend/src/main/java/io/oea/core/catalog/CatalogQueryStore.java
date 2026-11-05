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
	private static final String TEXT_FORM = "coalesce(v%1$d.value_enum, v%1$d.value_text, v%1$d.value_int::text, "
			+ "v%1$d.value_bool::text, v%1$d.value_date::text, v%1$d.value_ref::text)";

	private final JdbcTemplate jdbc;

	public CatalogQueryStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A property shown as a column. */
	public record PropertyColumn(UUID propertyDefId, String sqlColumnName, String name) {
	}

	/** A field every entity has (US-047), with its text form and its sort expression. */
	public enum SystemField {

		NAME("e.name", "lower(e.name)"),
		DESCRIPTION("nullif(e.description, '')", "lower(nullif(e.description, ''))"),
		EXTERNAL_ID("e.external_id", "lower(e.external_id)"),
		CREATED_AT("to_char(e.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "e.created_at"),
		UPDATED_AT("to_char(e.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "e.updated_at"),
		ENTITY_VERSION("e.version::text", "e.version");

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

	/**
	 * The sort of a page: {@code column} is the index into the columns, or {@code null} for the
	 * entity name.
	 */
	public record Sort(Integer column, boolean descending) {

		/** Name ascending, the default order. */
		public static final Sort DEFAULT = new Sort(null, false);

	}

	/** An entity row with the text form of each column ({@code null} without value). */
	public record Row(int entityId, String name, List<String> values) {
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
	 * One page of active entities of the metatype. Rows without a value in the sort column come
	 * last in both directions; enum values follow the order of the enum, values outside it come
	 * after them (#65); ties are ordered by name and ID.
	 */
	public List<Row> page(UUID metatypeId, List<QueryColumn> columns, Sort sort, long offset, int limit) {
		StringBuilder sql = new StringBuilder("SELECT e.id, e.name");
		for (int i = 0; i < columns.size(); i++) {
			QueryColumn column = columns.get(i);
			sql.append(", ").append((column.field() != null) ? column.field().text : TEXT_FORM.formatted(i));
		}
		sql.append(" FROM entities e");
		List<Object> args = new ArrayList<>();
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).property() != null) {
				sql.append(" LEFT JOIN entity_property_values v%1$d ON v%1$d.entity_id = e.id AND v%1$d.property_def_id = ?"
					.formatted(i));
				args.add(columns.get(i).property().propertyDefId());
			}
		}
		Integer sorted = sort.column();
		if (sorted != null && columns.get(sorted).property() != null) {
			sql.append(" LEFT JOIN property_enum_values eo ON eo.property_def_id = v%1$d.property_def_id"
				.formatted(sorted) + " AND eo.value = v%1$d.value_enum".formatted(sorted));
		}
		sql.append(" WHERE e.metatype_id = ? AND e.deleted_at IS NULL ORDER BY ");
		args.add(metatypeId);
		String direction = sort.descending() ? "DESC" : "ASC";
		if (sorted == null || columns.get(sorted).field() == SystemField.NAME) {
			sql.append("lower(e.name) %s, e.id".formatted(direction));
		}
		else if (columns.get(sorted).field() != null) {
			sql.append("%s %s NULLS LAST, lower(e.name), e.id".formatted(columns.get(sorted).field().order, direction));
		}
		else {
			// typed columns first, so that numbers and dates sort by value; only one of them is set
			int i = sorted;
			sql.append("eo.sort_order %s NULLS LAST, ".formatted(direction));
			for (String column : List.of("value_int", "value_date", "value_bool", "value_ref")) {
				sql.append("v%d.%s %s NULLS LAST, ".formatted(i, column, direction));
			}
			sql.append("lower(coalesce(v%1$d.value_enum, v%1$d.value_text)) %2$s NULLS LAST, lower(e.name), e.id"
				.formatted(i, direction));
		}
		sql.append(" LIMIT ? OFFSET ?");
		args.add(limit);
		args.add(offset);
		return this.jdbc.query(sql.toString(), (rs, rowNum) -> {
			List<String> values = new ArrayList<>(columns.size());
			for (int i = 0; i < columns.size(); i++) {
				values.add(rs.getString(3 + i));
			}
			return new Row(rs.getInt(1), rs.getString(2), values);
		}, args.toArray());
	}

	/** The number of active entities of the metatype. */
	public long count(UUID metatypeId) {
		Long count = this.jdbc.queryForObject(
				"SELECT count(*) FROM entities WHERE metatype_id = ? AND deleted_at IS NULL", Long.class, metatypeId);
		return (count != null) ? count : 0;
	}

}
