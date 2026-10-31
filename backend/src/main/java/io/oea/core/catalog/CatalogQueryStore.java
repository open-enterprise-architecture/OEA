package io.oea.core.catalog;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

	/**
	 * The sort of a page: {@code property} is the index into the property columns, or
	 * {@code null} for the entity name.
	 */
	public record Sort(Integer property, boolean descending) {

		/** Name ascending, the default order. */
		public static final Sort DEFAULT = new Sort(null, false);

	}

	/** An entity row with the text form of each property column ({@code null} without value). */
	public record Row(int entityId, String name, List<String> values) {
	}

	/**
	 * The property with this name (case-insensitive) if the metatype or one of its base types maps
	 * it on the given date (latest {@code valid_from} not after the date, ADR-022) and its
	 * visibility is {@code public}.
	 */
	public Optional<PropertyColumn> mappedPublicProperty(UUID metatypeId, String name, LocalDate date) {
		return this.jdbc.query("""
				WITH RECURSIVE chain (id) AS (
				    SELECT ?::uuid
				    UNION SELECT m.base_metatype_id FROM metatypes m JOIN chain c ON m.id = c.id
				    WHERE m.base_metatype_id IS NOT NULL)
				SELECT p.id, p.sql_column_name, p.name
				FROM (SELECT DISTINCT ON (property_def_id) property_def_id
				      FROM metatype_property_mappings WHERE metatype_id IN (SELECT id FROM chain) AND valid_from <= ?
				      ORDER BY property_def_id, valid_from DESC) active
				JOIN property_definitions p ON p.id = active.property_def_id
				WHERE lower(p.name) = lower(?) AND p.visibility_mode = 'public'""",
				(rs, rowNum) -> new PropertyColumn(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
				metatypeId, Date.valueOf(date), name)
			.stream()
			.findFirst();
	}

	/**
	 * One page of active entities of the metatype. Rows without a value in the sort column come
	 * last in both directions; ties are ordered by name and ID.
	 */
	public List<Row> page(UUID metatypeId, List<PropertyColumn> columns, Sort sort, long offset, int limit) {
		StringBuilder sql = new StringBuilder("SELECT e.id, e.name");
		for (int i = 0; i < columns.size(); i++) {
			sql.append(", ").append(TEXT_FORM.formatted(i));
		}
		sql.append(" FROM entities e");
		List<Object> args = new ArrayList<>();
		for (int i = 0; i < columns.size(); i++) {
			sql.append(" LEFT JOIN entity_property_values v%1$d ON v%1$d.entity_id = e.id AND v%1$d.property_def_id = ?"
				.formatted(i));
			args.add(columns.get(i).propertyDefId());
		}
		sql.append(" WHERE e.metatype_id = ? AND e.deleted_at IS NULL ORDER BY ");
		args.add(metatypeId);
		String direction = sort.descending() ? "DESC" : "ASC";
		if (sort.property() != null) {
			// typed columns first, so that numbers and dates sort by value; only one of them is set
			int i = sort.property();
			for (String column : List.of("value_int", "value_date", "value_bool", "value_ref")) {
				sql.append("v%d.%s %s NULLS LAST, ".formatted(i, column, direction));
			}
			sql.append("lower(coalesce(v%1$d.value_enum, v%1$d.value_text)) %2$s NULLS LAST, lower(e.name), e.id"
				.formatted(i, direction));
		}
		else {
			sql.append("lower(e.name) %s, e.id".formatted(direction));
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
