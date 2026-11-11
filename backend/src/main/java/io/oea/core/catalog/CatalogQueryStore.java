package io.oea.core.catalog;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

	/**
	 * A property shown as a column, with its data type ({@code string}, {@code integer},
	 * {@code boolean}, {@code enum}, {@code reference}, {@code date}, {@code url} or {@code text}).
	 */
	public record PropertyColumn(UUID propertyDefId, String sqlColumnName, String name, String dataType) {
	}

	/** The type of a filtered value, which decides its SQL comparison (design D2 of us-051-catalog-filters). */
	public enum ValueType {

		TEXT, ENUM, BOOLEAN, NUMBER, DATE

	}

	/** The operator of a filter condition. */
	public enum Operator {

		EQ, NEQ, CONTAINS, STARTS_WITH, IN, NOT_IN, IS_NULL, IS_NOT_NULL, GT, LT, GTE, LTE

	}

	/**
	 * A filter condition on a column (design D3 of us-051-catalog-filters): {@code values} holds
	 * the typed comparison values ({@link String}, {@link Long}, {@link LocalDate} or
	 * {@link Boolean}), several for {@code IN} and {@code NOT_IN}, none for {@code IS_NULL} and
	 * {@code IS_NOT_NULL}; text values are compared ignoring case.
	 */
	public record Condition(QueryColumn column, ValueType type, Operator operator, List<Object> values) {

		public Condition {
			values = List.copyOf(values);
		}

	}

	/**
	 * The filter of a query: the conditions on primary columns, the aggregate joins with
	 * conditions (their conditions in {@link Join#conditions}) and whether any condition suffices
	 * ({@code or}) instead of all; rows joins carry their conditions themselves.
	 */
	public record Filter(List<Condition> conditions, List<Join> aggregateJoins, boolean or) {

		/** No filter. */
		public static final Filter NONE = new Filter(List.of(), List.of(), false);

		public Filter {
			conditions = List.copyOf(conditions);
			aggregateJoins = List.copyOf(aggregateJoins);
		}

	}

	/** A field every entity has (US-047), with its text form and its sort expression per alias. */
	public enum SystemField {

		NAME("%1$s.name", "lower(%1$s.name)", KeyType.TEXT),
		DESCRIPTION("nullif(%1$s.description, '')", "lower(nullif(%1$s.description, ''))", KeyType.TEXT),
		EXTERNAL_ID("%1$s.external_id", "lower(%1$s.external_id)", KeyType.TEXT),
		CREATED_AT("to_char(%1$s.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "%1$s.created_at",
				KeyType.TIMESTAMP),
		UPDATED_AT("to_char(%1$s.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')", "%1$s.updated_at",
				KeyType.TIMESTAMP),
		ENTITY_VERSION("%1$s.version::text", "%1$s.version", KeyType.INTEGER);

		private final String text;

		private final String order;

		private final KeyType orderType;

		SystemField(String text, String order, KeyType orderType) {
			this.text = text;
			this.order = order;
			this.orderType = orderType;
		}

	}

	/**
	 * The SQL type of a sort key (design D1 of s9-catalog-query-performance), with the Java type
	 * of its values: {@link String}, {@link Integer}, {@link Long}, {@link LocalDate},
	 * {@link Boolean} or {@link OffsetDateTime}.
	 */
	public enum KeyType {

		TEXT("text"), INTEGER("integer"), BIGINT("bigint"), DATE("date"), BOOLEAN("boolean"),
		TIMESTAMP("timestamptz");

		private final String sql;

		KeyType(String sql) {
			this.sql = sql;
		}

		private Object read(ResultSet rs, int index) throws SQLException {
			return switch (this) {
				case TEXT -> rs.getString(index);
				case INTEGER -> rs.getObject(index, Integer.class);
				case BIGINT -> rs.getObject(index, Long.class);
				case DATE -> rs.getObject(index, LocalDate.class);
				case BOOLEAN -> rs.getObject(index, Boolean.class);
				case TIMESTAMP -> rs.getObject(index, OffsetDateTime.class);
			};
		}

		private Object bind(Object value) {
			return (value instanceof LocalDate date) ? Date.valueOf(date) : value;
		}

	}

	/**
	 * A key of the order of a page (design D1 of s9-catalog-query-performance): its expression,
	 * its type, its direction and whether it can be empty, which sorts it last.
	 */
	private record SortKey(String expression, KeyType type, boolean descending, boolean nullable) {

		String order() {
			return this.expression + (this.descending ? " DESC" : " ASC") + (this.nullable ? " NULLS LAST" : "");
		}

	}

	/**
	 * A column of a page: a system field or a property, at most one of them (neither for a
	 * {@linkplain #redacted() redacted} column), of the entity or, with
	 * {@code endpoint}, of the source or target of the connection (US-080).
	 */
	public record QueryColumn(SystemField field, PropertyColumn property, Endpoint endpoint, ColumnScope scope) {

		public static QueryColumn of(SystemField field) {
			return new QueryColumn(field, null, null, null);
		}

		public static QueryColumn of(PropertyColumn property) {
			return new QueryColumn(null, property, null, null);
		}

		public QueryColumn at(Endpoint side) {
			return new QueryColumn(this.field, this.property, side, this.scope);
		}

		/**
		 * The column with its values shown only for entities in the scope (design D5 of
		 * us-127-connection-scoped-visibility).
		 */
		public QueryColumn scoped(ColumnScope columnScope) {
			return new QueryColumn(this.field, this.property, this.endpoint, columnScope);
		}

		/**
		 * A column whose values the reader may not see (design D2 of
		 * us-126-role-restricted-enforcement): it selects {@code NULL} and reads no value.
		 */
		public static QueryColumn redacted() {
			return new QueryColumn(null, null, null, null);
		}

		public boolean isRedacted() {
			return this.field == null && this.property == null;
		}

	}

	/**
	 * The scope of a connection-scoped column for one caller: the scoping connection metatype and
	 * the caller's element (REQ-127; design D5 of us-127-connection-scoped-visibility).
	 */
	public record ColumnScope(UUID scopingType, int anchor) {
	}

	/** The expression of a column, {@code NULL} for an entity {@code alias} outside the column's scope. */
	private static String scoped(QueryColumn column, String alias, String expression) {
		if (column == null || column.scope() == null) {
			return expression;
		}
		return "CASE WHEN " + ScopeStore.inScope(alias, column.scope().scopingType(), column.scope().anchor()) + " THEN "
				+ expression + " END";
	}

	/**
	 * A side of a connection (design D3 of us-080-connection-catalogs), with the alias of its
	 * entity in {@link #page} and its column in {@code entity_connections}.
	 */
	public enum Endpoint {

		SOURCE("es", "source_entity_id"), TARGET("et", "target_entity_id");

		private final String alias;

		private final String column;

		Endpoint(String alias, String column) {
			this.alias = alias;
			this.column = column;
		}

	}

	/** Direction of a join (US-048): the primary entity as source, as target, or either. */
	public enum Direction {

		OUTBOUND, INBOUND, BOTH

	}

	/**
	 * A join (design D3 of us-048-catalog-joins): the active target entities of exactly the target
	 * metatype, reached through active connections of the connection metatype, with their columns.
	 * The {@code conditions} on target columns narrow the results to the matching targets, all of
	 * them or, with {@code anyCondition}, one of them (design D3 of us-051-catalog-filters).
	 */
	public record Join(UUID connectionMetatypeId, Direction direction, UUID targetMetatypeId, List<QueryColumn> columns,
			List<Condition> conditions, boolean anyCondition) {

		public Join {
			columns = List.copyOf(columns);
			conditions = List.copyOf(conditions);
		}

		public Join(UUID connectionMetatypeId, Direction direction, UUID targetMetatypeId, List<QueryColumn> columns) {
			this(connectionMetatypeId, direction, targetMetatypeId, columns, List.of(), false);
		}

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
	 * An entity row with the text form of each column ({@code null} without value), the result
	 * of each rows join and its values of the sort keys ({@link #keyTypes}), which a cursor to the
	 * next page carries.
	 */
	public record Row(int entityId, String name, List<String> values, List<JoinRow> joins, List<Object> keys) {
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
			.filter((property) -> "public".equals(this.jdbc.queryForObject(
					"SELECT visibility_mode FROM property_definitions WHERE id = ?", String.class, property.propertyDefId())))
			.findFirst();
	}

	/**
	 * The properties a catalog of the metatype can show: mapped to it or one of its base types on
	 * the given date and not retired (US-047), also restricted ones, whose values the query shows
	 * only to readers (design D2 of us-126-role-restricted-enforcement), by ID.
	 */
	public Map<UUID, PropertyColumn> availableProperties(UUID metatypeId, LocalDate date) {
		Map<UUID, PropertyColumn> properties = new LinkedHashMap<>();
		this.jdbc.query("""
				WITH RECURSIVE chain (id) AS (
				    SELECT ?::uuid
				    UNION SELECT m.base_metatype_id FROM metatypes m JOIN chain c ON m.id = c.id
				    WHERE m.base_metatype_id IS NOT NULL)
				SELECT p.id, p.sql_column_name, p.name, p.data_type
				FROM (SELECT DISTINCT ON (property_def_id) property_def_id
				      FROM metatype_property_mappings WHERE metatype_id IN (SELECT id FROM chain) AND valid_from <= ?
				      ORDER BY property_def_id, valid_from DESC) active
				JOIN property_definitions p ON p.id = active.property_def_id
				WHERE p.retired_at IS NULL
				ORDER BY lower(p.name), p.id""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new PropertyColumn(id, rs.getString(2), rs.getString(3), rs.getString(4)));
		}, metatypeId, Date.valueOf(date));
		return properties;
	}

	/**
	 * The properties a column of the source or target ({@code side} {@code source} or
	 * {@code target}) of a connection metatype can show (design D2 of us-080-connection-catalogs):
	 * not retired and mapped on the date to an allowed endpoint metatype of the
	 * side, one of its subtypes or one of their base types; without a constraint for the side, to
	 * any metatype of the connection metatype's configuration. By ID, ordered by name.
	 */
	public Map<UUID, PropertyColumn> endpointProperties(UUID connectionMetatypeId, String side, LocalDate date) {
		Map<UUID, PropertyColumn> properties = new LinkedHashMap<>();
		this.jdbc.query("""
				WITH RECURSIVE seed (id) AS (
				    SELECT allowed_metatype_id FROM metatype_connection_constraints
				    WHERE metatype_id = ? AND direction = ?
				    UNION ALL
				    SELECT m.id FROM metatypes m
				    WHERE m.config_id = (SELECT config_id FROM metatypes WHERE id = ?)
				      AND NOT EXISTS (SELECT 1 FROM metatype_connection_constraints
				                      WHERE metatype_id = ? AND direction = ?)),
				allowed (id) AS (
				    SELECT id FROM seed
				    UNION SELECT m.id FROM metatypes m JOIN allowed a ON m.base_metatype_id = a.id),
				chain (id) AS (
				    SELECT id FROM allowed
				    UNION SELECT m.base_metatype_id FROM metatypes m JOIN chain c ON m.id = c.id
				    WHERE m.base_metatype_id IS NOT NULL)
				SELECT p.id, p.sql_column_name, p.name, p.data_type
				FROM (SELECT DISTINCT ON (metatype_id, property_def_id) property_def_id
				      FROM metatype_property_mappings WHERE metatype_id IN (SELECT id FROM chain) AND valid_from <= ?
				      ORDER BY metatype_id, property_def_id, valid_from DESC) active
				JOIN property_definitions p ON p.id = active.property_def_id
				WHERE p.retired_at IS NULL
				GROUP BY p.id, p.sql_column_name, p.name, p.data_type
				ORDER BY lower(p.name), p.id""", (rs) -> {
			UUID id = rs.getObject(1, UUID.class);
			properties.put(id, new PropertyColumn(id, rs.getString(2), rs.getString(3), rs.getString(4)));
		}, connectionMetatypeId, side, connectionMetatypeId, connectionMetatypeId, side, Date.valueOf(date));
		return properties;
	}

	/** The values of each enum property in the order of the enum; a property without values has no entry. */
	public Map<UUID, List<String>> enumValues(Collection<UUID> propertyDefIds) {
		Map<UUID, List<String>> values = new LinkedHashMap<>();
		if (propertyDefIds.isEmpty()) {
			return values;
		}
		this.jdbc.query("""
				SELECT property_def_id, value FROM property_enum_values WHERE property_def_id = ANY(?)
				ORDER BY property_def_id, sort_order, value""", (rs) -> {
			values.computeIfAbsent(rs.getObject(1, UUID.class), (key) -> new ArrayList<>()).add(rs.getString(2));
		}, (Object) propertyDefIds.toArray(UUID[]::new));
		return values;
	}

	/**
	 * Limits every following statement of the current transaction to the given time (design D4 of
	 * s9-catalog-query-performance); the limit ends with the transaction. The value is a number,
	 * never configured text.
	 */
	public void limitStatements(long milliseconds) {
		if (milliseconds < 1) {
			throw new IllegalArgumentException("limit " + milliseconds);
		}
		this.jdbc.execute("SET LOCAL statement_timeout = " + milliseconds);
	}

	/** Whether the metatype is a connection metatype; {@code false} for an unknown one. */
	public boolean isConnection(UUID metatypeId) {
		return this.jdbc
			.queryForList("SELECT is_connection FROM metatypes WHERE id = ?", Boolean.class, metatypeId)
			.stream()
			.findFirst()
			.orElse(false);
	}

	/**
	 * One page of rows: active entities of the metatype, expanded by the rows joins (their cross
	 * product, an entity without results keeps one row). Rows without a value in the sort column
	 * come last in both directions; enum values follow the order of the enum, values outside it
	 * come after them (#65); ties are ordered by name and ID, then by the join results. Only rows
	 * passing the filter are returned (design D3 of us-051-catalog-filters).
	 */
	public List<Row> page(UUID metatypeId, List<QueryColumn> columns, List<Join> rowsJoins, Filter filter, Sort sort,
			long offset, int limit) {
		return page(metatypeId, columns, rowsJoins, filter, sort, offset, limit, null);
	}

	/**
	 * One page of rows as above. With {@code after}, the values of the sort keys of the last row
	 * of the previous page, the page starts after that row instead of skipping {@code offset}
	 * rows (keyset paging, design D2 of s9-catalog-query-performance); the values have the types
	 * of {@link #keyTypes}.
	 */
	public List<Row> page(UUID metatypeId, List<QueryColumn> columns, List<Join> rowsJoins, Filter filter, Sort sort,
			long offset, int limit, List<Object> after) {
		List<SortKey> keys = sortKeys(columns, rowsJoins, sort);
		if (after != null && after.size() != keys.size()) {
			throw new IllegalArgumentException("expected " + keys.size() + " key values, got " + after.size());
		}
		Statement sql = new Statement("SELECT e.id, e.name");
		for (int i = 0; i < columns.size(); i++) {
			sql.text(", ").text(text(columns.get(i), alias(columns.get(i)), "v" + i));
		}
		for (int j = 0; j < rowsJoins.size(); j++) {
			sql.text(", j%d.id".formatted(j));
			List<QueryColumn> joinColumns = rowsJoins.get(j).columns();
			for (int i = 0; i < joinColumns.size(); i++) {
				sql.text(", ").text(text(joinColumns.get(i), "j" + j, "j" + j + "v" + i));
			}
		}
		for (int k = 0; k < keys.size(); k++) {
			sql.text(", %s AS k%d".formatted(keys.get(k).expression(), k));
		}
		sql.text(" FROM entities e");
		endpoints(sql, columns, filter);
		values(sql, columns, "e", "v");
		for (int j = 0; j < rowsJoins.size(); j++) {
			lateral(sql, rowsJoins.get(j), j);
			values(sql, rowsJoins.get(j).columns(), "j" + j, "j" + j + "v");
		}
		String enumOrder = enumOrderJoin(columns, rowsJoins, sort);
		if (enumOrder != null) {
			sql.text(enumOrder);
		}
		where(sql, metatypeId, rowsJoins, filter);
		if (after != null) {
			sql.text(" AND ");
			after(sql, keys, after);
		}
		sql.text(" ORDER BY ").text(String.join(", ", keys.stream().map(SortKey::order).toList()));
		sql.text(" LIMIT ? OFFSET ?").arg(limit).arg((after != null) ? 0 : offset);
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
			List<Object> keyValues = new ArrayList<>(keys.size());
			for (SortKey key : keys) {
				keyValues.add(key.type().read(rs, index++));
			}
			return new Row(rs.getInt(1), rs.getString(2), values, joins, Collections.unmodifiableList(keyValues));
		}, sql.args());
	}

	/** The types of the sort keys of a page, in order; a cursor carries one value per key. */
	public List<KeyType> keyTypes(List<QueryColumn> columns, List<Join> rowsJoins, Sort sort) {
		return sortKeys(columns, rowsJoins, sort).stream().map(SortKey::type).toList();
	}

	/**
	 * The keys of the order (design D1 of s9-catalog-query-performance): the sort column, then
	 * name and ID of the entity, then name and ID of each rows join. Rows without a value in the
	 * sort column come last in both directions; a property sorts by the order of its enum values,
	 * then by its typed value, only one of which is set (#65).
	 */
	private static List<SortKey> sortKeys(List<QueryColumn> columns, List<Join> rowsJoins, Sort sort) {
		List<SortKey> keys = new ArrayList<>();
		boolean descending = sort.descending();
		QueryColumn sorted = sorted(columns, rowsJoins, sort);
		String sortAlias = (sort.column() == null) ? "e"
				: (sort.join() == null) ? alias(sorted) : "j" + sort.join();
		if (sorted == null || (sort.join() == null && sorted.endpoint() == null && sorted.field() == SystemField.NAME)) {
			keys.add(new SortKey("lower(e.name)", KeyType.TEXT, descending, false));
		}
		else if (sorted.field() != null) {
			keys.add(new SortKey(scoped(sorted, sortAlias, sorted.field().order.formatted(sortAlias)),
					sorted.field().orderType, descending, true));
			keys.add(new SortKey("lower(e.name)", KeyType.TEXT, false, false));
		}
		else {
			// out-of-scope values sort as empty (design D5 of us-127-connection-scoped-visibility)
			String valueAlias = valueAlias(sort);
			keys.add(new SortKey(scoped(sorted, sortAlias, "eo.sort_order"), KeyType.INTEGER, descending, true));
			keys.add(new SortKey(scoped(sorted, sortAlias, valueAlias + ".value_int"), KeyType.BIGINT, descending, true));
			keys.add(new SortKey(scoped(sorted, sortAlias, valueAlias + ".value_date"), KeyType.DATE, descending, true));
			keys.add(new SortKey(scoped(sorted, sortAlias, valueAlias + ".value_bool"), KeyType.BOOLEAN, descending,
					true));
			keys.add(new SortKey(scoped(sorted, sortAlias, valueAlias + ".value_ref"), KeyType.INTEGER, descending,
					true));
			keys.add(new SortKey(scoped(sorted, sortAlias,
					"lower(coalesce(%1$s.value_enum, %1$s.value_text))".formatted(valueAlias)), KeyType.TEXT, descending,
					true));
			keys.add(new SortKey("lower(e.name)", KeyType.TEXT, false, false));
		}
		keys.add(new SortKey("e.id", KeyType.INTEGER, false, false));
		for (int j = 0; j < rowsJoins.size(); j++) {
			keys.add(new SortKey("lower(j%d.name)".formatted(j), KeyType.TEXT, false, true));
			keys.add(new SortKey("j%d.id".formatted(j), KeyType.INTEGER, false, true));
		}
		return keys;
	}

	/** The sorted column, {@code null} for the default order by name. */
	private static QueryColumn sorted(List<QueryColumn> columns, List<Join> rowsJoins, Sort sort) {
		if (sort.column() == null) {
			return null;
		}
		return (sort.join() == null) ? columns.get(sort.column())
				: rowsJoins.get(sort.join()).columns().get(sort.column());
	}

	/** The alias of the property value of the sorted column. */
	private static String valueAlias(Sort sort) {
		return (sort.join() == null) ? "v" + sort.column() : "j" + sort.join() + "v" + sort.column();
	}

	/** The join of the enum order for a sorted property column, {@code null} for other sorts. */
	private static String enumOrderJoin(List<QueryColumn> columns, List<Join> rowsJoins, Sort sort) {
		QueryColumn sorted = sorted(columns, rowsJoins, sort);
		if (sorted == null || sorted.property() == null) {
			return null;
		}
		return " LEFT JOIN property_enum_values eo ON eo.property_def_id = %1$s.property_def_id AND eo.value = %1$s.value_enum"
			.formatted(valueAlias(sort));
	}

	/**
	 * The keyset predicate (design D2 of s9-catalog-query-performance): the rows after the one
	 * with the given key values. For keys {@code k1..kn} it is the {@code OR} over {@code i} of
	 * {@code k1 = v1 AND ... AND k(i-1) = v(i-1) AND ki after vi}; after a value come greater
	 * (ascending) or smaller (descending) values and the empty ones, after an empty value only
	 * empty ones, which are equal. For the default order the row comparison
	 * {@code (lower(e.name), e.id) > (?, ?)} lets PostgreSQL read the page from the index
	 * {@code entities_active_name}.
	 */
	private static void after(Statement sql, List<SortKey> keys, List<Object> values) {
		if (keys.size() == 2 && keys.stream().noneMatch((key) -> key.descending() || key.nullable())
				&& values.stream().allMatch(Objects::nonNull)) {
			sql.text("(%s, %s) > (?::%s, ?::%s)".formatted(keys.get(0).expression(), keys.get(1).expression(),
					keys.get(0).type().sql, keys.get(1).type().sql))
				.arg(keys.get(0).type().bind(values.get(0)))
				.arg(keys.get(1).type().bind(values.get(1)));
			return;
		}
		List<String> alternatives = new ArrayList<>();
		List<Object> args = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			SortKey key = keys.get(i);
			Object value = values.get(i);
			if (value == null) {
				continue; // only empty values follow an empty one, and they are equal to it
			}
			List<String> parts = new ArrayList<>();
			List<Object> partArgs = new ArrayList<>();
			for (int e = 0; e < i; e++) {
				equal(keys.get(e), values.get(e), parts, partArgs);
			}
			String cast = "?::" + key.type().sql;
			String greater = "%s %s %s".formatted(key.expression(), key.descending() ? "<" : ">", cast);
			parts.add(key.nullable() ? "(%s OR %s IS NULL)".formatted(greater, key.expression()) : greater);
			partArgs.add(key.type().bind(value));
			alternatives.add("(" + String.join(" AND ", parts) + ")");
			args.addAll(partArgs);
		}
		if (alternatives.isEmpty()) {
			sql.text("false");
			return;
		}
		sql.text("(" + String.join(" OR ", alternatives) + ")");
		args.forEach(sql::arg);
	}

	private static void equal(SortKey key, Object value, List<String> parts, List<Object> args) {
		if (value == null) {
			parts.add(key.expression() + " IS NULL");
		}
		else {
			parts.add("%s = ?::%s".formatted(key.expression(), key.type().sql));
			args.add(key.type().bind(value));
		}
	}

	/**
	 * The number of rows of {@link #page}: active entities, expanded by the rows joins, that pass
	 * the filter.
	 */
	public long count(UUID metatypeId, List<Join> rowsJoins, Filter filter) {
		Statement sql = new Statement("SELECT count(*) FROM entities e");
		endpoints(sql, List.of(), filter);
		for (int j = 0; j < rowsJoins.size(); j++) {
			lateral(sql, rowsJoins.get(j), j);
		}
		where(sql, metatypeId, rowsJoins, filter);
		Long count = this.jdbc.queryForObject(sql.toString(), Long.class, sql.args());
		return (count != null) ? count : 0;
	}

	/** The number of active entities of the metatype. */
	public long count(UUID metatypeId) {
		return count(metatypeId, List.of(), Filter.NONE);
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
		narrow(sql, join, "t");
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
		if (column.isRedacted()) {
			return "NULL::text";
		}
		return scoped(column, alias,
				(column.field() != null) ? column.field().text.formatted(alias) : TEXT_FORM.formatted(valueAlias));
	}

	/** The alias of the entity whose attribute the primary column shows: {@code e} or an endpoint's. */
	private static String alias(QueryColumn column) {
		return (column.endpoint() != null) ? column.endpoint().alias : "e";
	}

	/**
	 * The property value joins of the columns of the entity {@code alias}, or of its endpoint for
	 * an endpoint column.
	 */
	private static void values(Statement sql, List<QueryColumn> columns, String alias, String prefix) {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).property() != null) {
				String entity = (columns.get(i).endpoint() != null) ? columns.get(i).endpoint().alias : alias;
				sql.text(" LEFT JOIN entity_property_values %1$s%2$d ON %1$s%2$d.entity_id = %3$s.id"
					.formatted(prefix, i, entity) + " AND %1$s%2$d.property_def_id = ?".formatted(prefix, i))
					.arg(columns.get(i).property().propertyDefId());
			}
		}
	}

	/** A rows join as {@code LEFT JOIN LATERAL} with the alias {@code j<n>}: one row per target. */
	private static void lateral(Statement sql, Join join, int n) {
		sql.text(" LEFT JOIN LATERAL (SELECT t.id, t.name, t.description, t.external_id, t.created_at, t.updated_at,"
				+ " t.version FROM (");
		pairs(sql, join, "= e.id", null);
		sql.text(") p JOIN entities t ON t.id = p.tid AND t.metatype_id = ? AND t.deleted_at IS NULL")
			.arg(join.targetMetatypeId());
		narrow(sql, join, "t");
		sql.text(") j%d ON true".formatted(n));
	}

	/** The value joins and the {@code WHERE} of the join's conditions on its target {@code alias}, if any. */
	private static void narrow(Statement sql, Join join, String alias) {
		if (!join.conditions().isEmpty()) {
			values(sql, join.conditions().stream().map(Condition::column).toList(), alias, alias + "f");
			sql.text(" WHERE ");
			predicates(sql, join.conditions(), alias, alias + "f", join.anyCondition());
		}
	}

	/**
	 * The source and target of the connection as {@code es} and {@code et}, if a column shows or
	 * a condition tests one of them.
	 */
	private static void endpoints(Statement sql, List<QueryColumn> columns, Filter filter) {
		if (columns.stream().anyMatch((column) -> column.endpoint() != null)
				|| filter.conditions().stream().anyMatch((condition) -> condition.column().endpoint() != null)) {
			sql.text(" LEFT JOIN entity_connections ec ON ec.entity_id = e.id");
			for (Endpoint side : Endpoint.values()) {
				sql.text(" LEFT JOIN entities %1$s ON %1$s.id = ec.%2$s AND %1$s.deleted_at IS NULL"
					.formatted(side.alias, side.column));
			}
		}
	}

	/**
	 * The property value joins of the filter's primary conditions ({@code f<i>}) and the
	 * {@code WHERE} clause: active entities of the metatype that pass the filter. A rows join with
	 * conditions holds only matching targets, so its condition is having a result
	 * ({@code j<n>.id}); an aggregate join's is an {@code EXISTS} over its matching targets.
	 */
	private static void where(Statement sql, UUID metatypeId, List<Join> rowsJoins, Filter filter) {
		values(sql, filter.conditions().stream().map(Condition::column).toList(), "e", "f");
		sql.text(" WHERE e.metatype_id = ? AND e.deleted_at IS NULL").arg(metatypeId);
		List<Runnable> parts = new ArrayList<>();
		for (int i = 0; i < filter.conditions().size(); i++) {
			Condition condition = filter.conditions().get(i);
			String valueAlias = "f" + i;
			parts.add(() -> predicate(sql, condition, alias(condition.column()), valueAlias));
		}
		for (int j = 0; j < rowsJoins.size(); j++) {
			if (!rowsJoins.get(j).conditions().isEmpty()) {
				String result = "j%d.id IS NOT NULL".formatted(j);
				parts.add(() -> sql.text(result));
			}
		}
		for (int j = 0; j < filter.aggregateJoins().size(); j++) {
			Join join = filter.aggregateJoins().get(j);
			String target = "x" + j;
			parts.add(() -> {
				sql.text("EXISTS (SELECT 1 FROM (");
				pairs(sql, join, "= e.id", null);
				sql.text(") p JOIN entities %1$s ON %1$s.id = p.tid AND %1$s.metatype_id = ? AND %1$s.deleted_at IS NULL"
					.formatted(target)).arg(join.targetMetatypeId());
				narrow(sql, join, target);
				sql.text(")");
			});
		}
		if (parts.isEmpty()) {
			return;
		}
		sql.text(" AND (");
		for (int i = 0; i < parts.size(); i++) {
			sql.text((i == 0) ? "" : filter.or() ? " OR " : " AND ");
			parts.get(i).run();
		}
		sql.text(")");
	}

	/** The conditions on the target {@code alias} of a join, combined with AND or, with {@code any}, OR. */
	private static void predicates(Statement sql, List<Condition> conditions, String alias, String prefix,
			boolean any) {
		sql.text("(");
		for (int i = 0; i < conditions.size(); i++) {
			sql.text((i == 0) ? "" : any ? " OR " : " AND ");
			predicate(sql, conditions.get(i), alias, prefix + i);
		}
		sql.text(")");
	}

	/**
	 * The predicate of a condition on the entity {@code alias} with its property value under
	 * {@code valueAlias} (design D3 of us-051-catalog-filters). The empty tests use the text form;
	 * {@code NEQ} and {@code NOT_IN} also hold for an entity without a value.
	 */
	private static void predicate(Statement sql, Condition condition, String alias, String valueAlias) {
		QueryColumn column = condition.column();
		if (condition.operator() == Operator.IS_NULL || condition.operator() == Operator.IS_NOT_NULL) {
			sql.text("(%s IS %sNULL)".formatted(text(column, alias, valueAlias),
					(condition.operator() == Operator.IS_NULL) ? "" : "NOT "));
			return;
		}
		boolean text = condition.type() == ValueType.TEXT;
		String typed = typed(column, condition.type(), alias, valueAlias);
		String value = text ? "lower(" + typed + ")" : typed;
		List<Object> args = condition.values().stream().map((arg) -> bind(arg, text)).toList();
		switch (condition.operator()) {
			case EQ -> sql.text("(%s = ?)".formatted(value)).arg(args.get(0));
			case NEQ -> sql.text("(%s IS DISTINCT FROM ?)".formatted(value)).arg(args.get(0));
			case GT -> sql.text("(%s > ?)".formatted(value)).arg(args.get(0));
			case LT -> sql.text("(%s < ?)".formatted(value)).arg(args.get(0));
			case GTE -> sql.text("(%s >= ?)".formatted(value)).arg(args.get(0));
			case LTE -> sql.text("(%s <= ?)".formatted(value)).arg(args.get(0));
			case CONTAINS -> sql.text("(%s LIKE '%%' || ? || '%%' ESCAPE '\\')".formatted(value))
				.arg(like((String) args.get(0)));
			case STARTS_WITH -> sql.text("(%s LIKE ? || '%%' ESCAPE '\\')".formatted(value))
				.arg(like((String) args.get(0)));
			case IN -> sql.text("(%s = ANY(?))".formatted(value)).arg(array(args));
			case NOT_IN -> sql.text("(%1$s IS NULL OR %1$s <> ALL(?))".formatted(value)).arg(array(args));
			default -> throw new IllegalArgumentException("operator " + condition.operator());
		}
	}

	/** The typed value of a column: a system field's own column, else the property's typed column. */
	private static String typed(QueryColumn column, ValueType type, String alias, String valueAlias) {
		return scoped(column, alias, unscopedTyped(column, type, alias, valueAlias));
	}

	private static String unscopedTyped(QueryColumn column, ValueType type, String alias, String valueAlias) {
		if (column.field() != null) {
			return switch (column.field()) {
				case NAME -> alias + ".name";
				case DESCRIPTION -> "nullif(" + alias + ".description, '')";
				case EXTERNAL_ID -> alias + ".external_id";
				case CREATED_AT -> "(" + alias + ".created_at AT TIME ZONE 'UTC')::date";
				case UPDATED_AT -> "(" + alias + ".updated_at AT TIME ZONE 'UTC')::date";
				case ENTITY_VERSION -> alias + ".version";
			};
		}
		return switch (type) {
			case TEXT -> valueAlias + ".value_text";
			case ENUM -> valueAlias + ".value_enum";
			case BOOLEAN -> valueAlias + ".value_bool";
			case DATE -> valueAlias + ".value_date";
			case NUMBER -> "reference".equals(column.property().dataType()) ? valueAlias + ".value_ref"
					: valueAlias + ".value_int";
		};
	}

	/** A comparison value as JDBC argument; text in lower case if {@code lower}. */
	private static Object bind(Object value, boolean lower) {
		if (value instanceof String text) {
			return lower ? text.toLowerCase(Locale.ROOT) : text;
		}
		if (value instanceof LocalDate date) {
			return Date.valueOf(date);
		}
		return value;
	}

	/** The values of {@code IN} and {@code NOT_IN} as SQL array argument. */
	private static Object array(List<Object> values) {
		if (values.stream().allMatch(String.class::isInstance)) {
			return values.toArray(String[]::new);
		}
		if (values.stream().allMatch(Long.class::isInstance)) {
			return values.toArray(Long[]::new);
		}
		return values.toArray(Date[]::new);
	}

	/** The value with the wildcards of {@code LIKE} escaped. */
	private static String like(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
