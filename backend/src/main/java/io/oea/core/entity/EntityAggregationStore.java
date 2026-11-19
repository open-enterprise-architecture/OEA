package io.oea.core.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregates over the entities of the catalog for the KPI and chart widgets (design D3 of
 * us-056-kpi-and-chart-widgets). The statement is built from typed arguments only: the grouping
 * and the value column are enums that select fixed SQL snippets inside this class, IDs are bind
 * parameters, and no SQL text comes from the caller (ADR-051). Entities that are deleted are not
 * counted; deltas of solutions are not part of the catalog.
 */
@Repository
public class EntityAggregationStore {

	private final JdbcTemplate jdbc;

	public EntityAggregationStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** The typed column a property grouping reads its key from. */
	public enum ValueColumn {

		/** {@code value_text}, for string properties. */
		TEXT("g.value_text"),
		/** {@code value_bool}, as the key {@code true} or {@code false}. */
		BOOL("g.value_bool::text"),
		/** {@code value_ref}, as the ID of the referenced entity; its name is the group name. */
		REF("g.value_ref::text"),
		/** {@code value_enum}, the stored enumeration value. */
		ENUM("g.value_enum");

		private final String key;

		ValueColumn(String key) {
			this.key = key;
		}

	}

	/** How the entities are grouped: not at all, by metatype, or by the value of a property. */
	public record Grouping(Kind kind, UUID propertyDefId, ValueColumn column) {

		/** The kind of grouping. */
		public enum Kind {

			NONE, METATYPE, PROPERTY

		}

		public static Grouping none() {
			return new Grouping(Kind.NONE, null, null);
		}

		public static Grouping byMetatype() {
			return new Grouping(Kind.METATYPE, null, null);
		}

		public static Grouping byProperty(UUID propertyDefId, ValueColumn column) {
			return new Grouping(Kind.PROPERTY, propertyDefId, column);
		}

	}

	/**
	 * The partial aggregates of one group: {@code key} is {@code null} for entities without a
	 * value of the grouping property and for no grouping; {@code name} is the name of the
	 * referenced entity for a reference grouping. {@code count} is the number of entities (with a
	 * value of the aggregated property, if one is given); {@code sum}, {@code min} and
	 * {@code max} are {@code null} without an aggregated property.
	 */
	public record GroupRow(String key, String name, long count, Long sum, Long min, Long max) {
	}

	/**
	 * The groups of the entities of the metatypes that are not deleted.
	 *
	 * @param valuePropertyDefId the integer property to aggregate, or {@code null} to count every
	 *     entity; with a property, only entities that have a value of it are aggregated ({@code count} counts those, so it also works for properties that are not integers)
	 * @return one row per group; for no grouping one row, also over no entities (count 0)
	 */
	public List<GroupRow> aggregate(Collection<UUID> metatypeIds, UUID valuePropertyDefId, Grouping grouping) {
		if (metatypeIds.isEmpty() && grouping.kind() == Grouping.Kind.NONE) {
			return List.of(new GroupRow(null, null, 0, null, null, null));
		}
		if (metatypeIds.isEmpty()) {
			return List.of();
		}
		String[] metatypes = metatypeIds.stream().map(UUID::toString).toArray(String[]::new);
		boolean property = grouping.kind() == Grouping.Kind.PROPERTY;
		boolean reference = property && grouping.column() == ValueColumn.REF;
		String key = switch (grouping.kind()) {
			case NONE -> "NULL::text";
			case METATYPE -> "e.metatype_id::text";
			case PROPERTY -> grouping.column().key;
		};
		String name = reference ? "r.name" : "NULL::text";
		boolean valued = valuePropertyDefId != null;
		String values = valued ? "sum(v.value_int)::numeric, min(v.value_int), max(v.value_int)"
				: "NULL::numeric, NULL::int8, NULL::int8";
		StringBuilder sql = new StringBuilder("SELECT ").append(key)
			.append(" AS group_key, ")
			.append(name)
			.append(" AS group_name, count(*), ")
			.append(values)
			.append(" FROM entities e");
		if (valued) {
			sql.append(" JOIN entity_property_values v ON v.entity_id = e.id AND v.property_def_id = ?")
				.append(" AND num_nonnulls(v.value_text, v.value_int, v.value_bool, v.value_date, v.value_ref, v.value_enum) > 0");
		}
		if (property) {
			sql.append(" LEFT JOIN entity_property_values g ON g.entity_id = e.id AND g.property_def_id = ?");
		}
		if (reference) {
			sql.append(" LEFT JOIN entities r ON r.id = g.value_ref");
		}
		sql.append(" WHERE e.deleted_at IS NULL AND e.metatype_id = ANY (?::uuid[])");
		if (grouping.kind() != Grouping.Kind.NONE) {
			sql.append(" GROUP BY ").append((reference) ? "group_key, group_name" : "group_key");
		}
		List<Object> arguments = new ArrayList<>();
		if (valued) {
			arguments.add(valuePropertyDefId);
		}
		if (property) {
			arguments.add(grouping.propertyDefId());
		}
		arguments.add(metatypes);
		return this.jdbc.query(sql.toString(), (rs, n) -> {
			BigDecimal sum = rs.getBigDecimal(4);
			return new GroupRow(rs.getString(1), rs.getString(2), rs.getLong(3),
					(sum != null) ? sum.longValueExact() : null, (Long) rs.getObject(5), (Long) rs.getObject(6));
		}, arguments.toArray());
	}

	/**
	 * The aggregate of the entities of the metatypes as they were at {@code at} (design D2 of
	 * us-056-kpi-previous-value), without grouping. An entity existed at {@code at} if it was
	 * created at or before it and not deleted at or before it. Its value of the property is the
	 * old value of its first history row changed after {@code at} (a history row holds the value
	 * before the change), or the current value if it has not changed since. The metatype and the
	 * metamodel are the current ones.
	 *
	 * @param valuePropertyDefId the property to aggregate, or {@code null} to count every entity
	 *     that existed; with a property, only entities that had a value of it then are aggregated
	 * @return one row (key and name {@code null}), also over no entities (count 0)
	 */
	public GroupRow aggregateAt(Collection<UUID> metatypeIds, UUID valuePropertyDefId, Instant at) {
		if (metatypeIds.isEmpty()) {
			return new GroupRow(null, null, 0, null, null, null);
		}
		String[] metatypes = metatypeIds.stream().map(UUID::toString).toArray(String[]::new);
		Timestamp time = Timestamp.from(at);
		boolean valued = valuePropertyDefId != null;
		StringBuilder sql = new StringBuilder();
		List<Object> arguments = new ArrayList<>();
		if (valued) {
			sql.append("SELECT count(*), sum(x.value_int)::numeric, min(x.value_int), max(x.value_int) FROM (")
				.append("SELECT CASE WHEN h.id IS NOT NULL THEN h.value_text ELSE v.value_text END AS value_text,")
				.append(" CASE WHEN h.id IS NOT NULL THEN h.value_int ELSE v.value_int END AS value_int,")
				.append(" CASE WHEN h.id IS NOT NULL THEN h.value_bool ELSE v.value_bool END AS value_bool,")
				.append(" CASE WHEN h.id IS NOT NULL THEN h.value_date ELSE v.value_date END AS value_date,")
				.append(" CASE WHEN h.id IS NOT NULL THEN h.value_ref ELSE v.value_ref END AS value_ref,")
				.append(" CASE WHEN h.id IS NOT NULL THEN h.value_enum ELSE v.value_enum END AS value_enum")
				.append(" FROM entities e LEFT JOIN LATERAL (")
				.append("SELECT h.id, h.value_text, h.value_int, h.value_bool, h.value_date, h.value_ref, h.value_enum")
				.append(" FROM entity_property_value_history h")
				.append(" WHERE h.entity_id = e.id AND h.property_def_id = ? AND h.changed_at > ?")
				.append(" ORDER BY h.entity_version LIMIT 1) h ON true")
				.append(" LEFT JOIN entity_property_values v ON v.entity_id = e.id AND v.property_def_id = ?")
				.append(" WHERE e.created_at <= ? AND (e.deleted_at IS NULL OR e.deleted_at > ?)")
				.append(" AND e.metatype_id = ANY (?::uuid[])) x")
				.append(" WHERE num_nonnulls(x.value_text, x.value_int, x.value_bool, x.value_date, x.value_ref, x.value_enum) > 0");
			arguments.addAll(List.of(valuePropertyDefId, time, valuePropertyDefId, time, time));
		}
		else {
			sql.append("SELECT count(*), NULL::numeric, NULL::int8, NULL::int8 FROM entities e")
				.append(" WHERE e.created_at <= ? AND (e.deleted_at IS NULL OR e.deleted_at > ?)")
				.append(" AND e.metatype_id = ANY (?::uuid[])");
			arguments.addAll(List.of(time, time));
		}
		arguments.add(metatypes);
		return this.jdbc.queryForObject(sql.toString(), (rs, n) -> {
			BigDecimal sum = rs.getBigDecimal(2);
			return new GroupRow(null, null, rs.getLong(1), (sum != null) ? sum.longValueExact() : null,
					(Long) rs.getObject(3), (Long) rs.getObject(4));
		}, arguments.toArray());
	}

}
