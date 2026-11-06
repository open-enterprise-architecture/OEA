package io.oea.app.catalog;

import io.oea.app.catalog.CatalogColumns.Resolved;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.Condition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live query of a catalog (US-049, REQ-046; design D1, D2 and D4 of us-049-show-catalog-data):
 * resolves the columns, checks the paging and sort parameters and reads one page. The catalog is
 * read through {@link Catalogs#get}, so a foreign personal catalog is answered as not existing.
 */
@Service
public class CatalogQueries {

	/** Key of the entity name column. */
	public static final String NAME_COLUMN = "name";

	static final int MAX_PAGE_SIZE = 200;

	private static final Pattern SORT = Pattern
		.compile("((?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.|source\\.|target\\.)?"
				+ "[a-zA-Z][a-zA-Z0-9_]*):(asc|desc)");

	private final Catalogs catalogs;

	private final CatalogQueryStore store;

	private final CatalogColumns columns;

	CatalogQueries(Catalogs catalogs, CatalogQueryStore store, CatalogColumns columns) {
		this.catalogs = catalogs;
		this.store = store;
		this.columns = columns;
	}

	/**
	 * A column of the result; the key names the value in each row. {@code filterType} is
	 * {@code text}, {@code enum}, {@code boolean}, {@code number} or {@code date};
	 * {@code enumValues} lists the values of an enum column and is {@code null} for other types.
	 */
	public record Column(String key, String label, boolean sortable, String filterType, List<String> enumValues) {
	}

	/**
	 * An entity row; {@code values} holds the text form per column key, {@code null} without value;
	 * {@code joinResults} the results per join ID (US-048).
	 */
	public record Row(int entityId, Map<String, String> values, Map<UUID, List<JoinResult>> joinResults) {
	}

	/** A join result: the target entity and the text form per key of the join's columns. */
	public record JoinResult(int entityId, Map<String, String> values) {
	}

	/** A join of the result with its applied mode ({@code rows} or {@code aggregate}) and columns. */
	public record QueryJoin(UUID id, String name, String mode, List<Column> columns) {
	}

	/** The applied sort. */
	public record Sort(String column, boolean descending) {
	}

	/** One page of the catalog; {@code sort} is {@code null} for the default order. */
	public record Result(List<Column> columns, List<QueryJoin> joins, List<Row> rows, int page, int pageSize,
			long totalElements, int totalPages, Sort sort) {
	}

	/** An available join with its store definition, its result, its columns and whether it expands rows. */
	private record ResolvedJoin(Catalogs.Join join, CatalogQueryStore.Join store, QueryJoin result, boolean rows,
			List<Resolved> columns) {

		/** The join with its results narrowed by the conditions, if any. */
		ResolvedJoin narrowed(List<Condition> conditions, boolean any) {
			if (conditions == null || conditions.isEmpty()) {
				return this;
			}
			return new ResolvedJoin(this.join, new CatalogQueryStore.Join(this.store.connectionMetatypeId(),
					this.store.direction(), this.store.targetMetatypeId(), this.store.columns(), conditions, any),
					this.result, this.rows, this.columns);
		}

	}

	/** One page of the catalog without filters. */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes) {
		return query(principal, catalogId, page, pageSize, sort, joinModes, List.of(), null, null);
	}

	/**
	 * One page of the catalog. Order: visibility of the catalog (404), paging parameters, sort,
	 * join modes and filters (400 naming the parameters). Configured columns apply when one of
	 * them can be shown (design D4 of us-047-configure-columns), else the default columns.
	 * {@code joinModes} maps join IDs to {@code rows} or {@code aggregate} (design D4 of
	 * us-048-catalog-joins). {@code filters} are {@code {key}:{operator}[:{value}]} on the columns
	 * of the result, combined by {@code filterMode} {@code and} (default) or {@code or} (design D2,
	 * D3 of us-051-catalog-filters).
	 */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes, List<String> filters, String filterMode) {
		return query(principal, catalogId, page, pageSize, sort, joinModes, filters, filterMode, null);
	}

	/**
	 * One page of the catalog as above; with {@code view}, the columns are those of the saved view
	 * in its order, and filters and sort resolve against them (design D4 of us-053-saved-views).
	 * An unknown or unavailable view adds {@code view} to the invalid parameters.
	 */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes, List<String> filters, String filterMode, UUID view) {
		Catalogs.Catalog catalog = this.catalogs.get(principal, catalogId);
		List<String> invalid = new ArrayList<>();
		if (page < 0) {
			invalid.add("page");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			invalid.add("pageSize");
		}
		List<Resolved> columns = this.columns.primary(catalog.primaryMetatypeId(), catalog.columns());
		if (view != null) {
			Catalogs.SavedView saved = catalog.savedViews()
				.stream()
				.filter((candidate) -> candidate.id().equals(view) && candidate.available())
				.findFirst()
				.orElse(null);
			if (saved == null) {
				invalid.add("view");
			}
			else {
				Map<String, Resolved> byKey = new LinkedHashMap<>();
				columns.forEach((column) -> byKey.put(column.result().key(), column));
				columns = saved.columnOrder().stream().map(byKey::get).toList();
			}
		}
		List<ResolvedJoin> available = joins(catalog, (joinModes != null) ? joinModes : Map.of(), invalid);
		List<ResolvedJoin> rowsJoins = available.stream().filter(ResolvedJoin::rows).toList();
		Sort applied = null;
		CatalogQueryStore.Sort storeSort = CatalogQueryStore.Sort.DEFAULT;
		if (sort != null && !sort.isEmpty()) {
			Matcher matcher = SORT.matcher(sort);
			CatalogQueryStore.Sort found = matcher.matches() ? sortOf(columns, rowsJoins, matcher.group(1),
					"desc".equals(matcher.group(2))) : null;
			if (found != null) {
				applied = new Sort(matcher.group(1), found.descending());
				storeSort = found;
			}
			else {
				invalid.add("sort");
			}
		}
		else {
			for (int i = 0; i < columns.size(); i++) {
				String direction = columns.get(i).defaultSortDirection();
				if (direction != null && columns.get(i).result().sortable()) {
					applied = new Sort(columns.get(i).result().key(), "desc".equals(direction));
					storeSort = new CatalogQueryStore.Sort(i, applied.descending());
					break;
				}
			}
		}
		boolean or = false;
		if (filterMode != null && !filterMode.isEmpty()) {
			if ("or".equals(filterMode)) {
				or = true;
			}
			else if (!"and".equals(filterMode)) {
				invalid.add("filterMode");
			}
		}
		List<Condition> conditions = new ArrayList<>();
		Map<UUID, List<Condition>> joinConditions = new LinkedHashMap<>();
		if (!conditions(filters, columns, available, conditions, joinConditions)) {
			invalid.add("filter");
		}
		if (!invalid.isEmpty()) {
			throw new InvalidCatalogQueryException(invalid);
		}
		boolean any = or;
		List<ResolvedJoin> joins = available.stream()
			.map((join) -> join.narrowed(joinConditions.get(join.join().id()), any))
			.toList();
		List<Resolved> resolved = columns;
		List<CatalogQueryStore.Join> storeRowsJoins = joins.stream()
			.filter(ResolvedJoin::rows)
			.map(ResolvedJoin::store)
			.toList();
		CatalogQueryStore.Filter filter = new CatalogQueryStore.Filter(conditions,
				joins.stream()
					.filter((join) -> !join.rows() && !join.store().conditions().isEmpty())
					.map(ResolvedJoin::store)
					.toList(),
				or);
		long total = this.store.count(catalog.primaryMetatypeId(), storeRowsJoins, filter);
		List<CatalogQueryStore.Row> page0 = this.store.page(catalog.primaryMetatypeId(),
				resolved.stream().map(Resolved::column).toList(), storeRowsJoins, filter, storeSort,
				(long) page * pageSize, pageSize);
		List<Integer> ids = page0.stream().map(CatalogQueryStore.Row::entityId).distinct().toList();
		Map<UUID, Map<Integer, List<CatalogQueryStore.JoinRow>>> aggregated = new LinkedHashMap<>();
		for (ResolvedJoin join : joins) {
			if (!join.rows()) {
				aggregated.put(join.join().id(), this.store.aggregate(join.store(), ids));
			}
		}
		List<Row> rows = page0.stream().map((row) -> {
			Map<String, String> values = new LinkedHashMap<>();
			for (int i = 0; i < resolved.size(); i++) {
				values.put(resolved.get(i).result().key(), row.values().get(i));
			}
			Map<UUID, List<JoinResult>> results = new LinkedHashMap<>();
			int rowsIndex = 0;
			for (ResolvedJoin join : joins) {
				List<CatalogQueryStore.JoinRow> found;
				if (join.rows()) {
					CatalogQueryStore.JoinRow one = row.joins().get(rowsIndex++);
					found = (one.entityId() != null) ? List.of(one) : List.of();
				}
				else {
					found = aggregated.get(join.join().id()).getOrDefault(row.entityId(), List.of());
				}
				results.put(join.join().id(), found.stream().map((result) -> {
					Map<String, String> joinValues = new LinkedHashMap<>();
					for (int i = 0; i < join.result().columns().size(); i++) {
						joinValues.put(join.result().columns().get(i).key(), result.values().get(i));
					}
					return new JoinResult(result.entityId(), Collections.unmodifiableMap(joinValues));
				}).toList());
			}
			return new Row(row.entityId(), Collections.unmodifiableMap(values), Collections.unmodifiableMap(results));
		}).toList();
		return new Result(resolved.stream().map(Resolved::result).toList(),
				joins.stream().map(ResolvedJoin::result).toList(), rows, page, pageSize, total,
				Math.toIntExact((total + pageSize - 1) / pageSize), applied);
	}

	/**
	 * The available joins with their modes: the override, else the join's default, else the
	 * catalog's. An override for an unknown or unavailable join, or with another value than
	 * {@code rows} or {@code aggregate}, adds {@code joinMode} to {@code invalid}.
	 */
	private List<ResolvedJoin> joins(Catalogs.Catalog catalog, Map<String, String> modes, List<String> invalid) {
		Map<String, String> remaining = new LinkedHashMap<>(modes);
		List<ResolvedJoin> joins = new ArrayList<>();
		for (Catalogs.Join join : catalog.joins()) {
			if (!join.available()) {
				continue;
			}
			List<Resolved> columns = this.columns.configured(join.targetMetatypeId(), join.targetColumns());
			if (columns.isEmpty()) {
				continue;
			}
			String mode = remaining.remove(join.id().toString());
			if (mode != null && !"rows".equals(mode) && !"aggregate".equals(mode)) {
				invalid.add("joinMode");
				mode = null;
			}
			if (mode == null) {
				mode = (join.defaultJoinMode() != null) ? join.defaultJoinMode() : catalog.defaultJoinMode();
			}
			boolean rows = "rows".equals(mode);
			List<Column> results = columns.stream()
				.map((column) -> new Column(column.result().key(), column.result().label(),
						rows && column.result().sortable(), column.result().filterType(),
						column.result().enumValues()))
				.toList();
			joins.add(new ResolvedJoin(join,
					new CatalogQueryStore.Join(join.connectionMetatypeId(),
							CatalogQueryStore.Direction.valueOf(join.direction().toUpperCase(java.util.Locale.ROOT)),
							join.targetMetatypeId(), columns.stream().map(Resolved::column).toList()),
					new QueryJoin(join.id(), join.name(), mode, results), rows, columns));
		}
		if (!remaining.isEmpty() && !invalid.contains("joinMode")) {
			invalid.add("joinMode");
		}
		return joins;
	}

	/**
	 * The conditions of the filters on the primary columns and per join (design D2 of
	 * us-051-catalog-filters); {@code false} if there are more than {@value FilterRules#MAX_FILTERS}
	 * or one names no column of the result, an operator not allowed for it or an invalid value.
	 */
	private static boolean conditions(List<String> filters, List<Resolved> columns, List<ResolvedJoin> joins,
			List<Condition> conditions, Map<UUID, List<Condition>> joinConditions) {
		if (filters == null || filters.isEmpty()) {
			return true;
		}
		if (filters.size() > FilterRules.MAX_FILTERS) {
			return false;
		}
		Map<String, CatalogColumns.Filterable> filterable = new LinkedHashMap<>();
		for (Resolved column : columns) {
			filterable.put(column.result().key(), new CatalogColumns.Filterable(column, null));
		}
		for (ResolvedJoin join : joins) {
			for (Resolved column : join.columns()) {
				filterable.put(join.join().id() + "." + column.result().key(),
						new CatalogColumns.Filterable(column, join.join().id()));
			}
		}
		for (String text : filters) {
			FilterRules.Expression expression = (text != null) ? FilterRules.parse(text) : null;
			CatalogColumns.Filterable column = (expression != null) ? filterable.get(expression.key()) : null;
			if (column == null) {
				return false;
			}
			Condition condition;
			try {
				condition = FilterRules.condition(column.column(), expression.operator(), expression.values());
			}
			catch (FilterRules.InvalidFilterException ex) {
				return false;
			}
			if (column.joinId() == null) {
				conditions.add(condition);
			}
			else {
				joinConditions.computeIfAbsent(column.joinId(), (key) -> new ArrayList<>()).add(condition);
			}
		}
		return true;
	}

	/**
	 * The store sort for a key: a sortable primary column, or {@code {joinId}.{key}} of a sortable
	 * column of a rows join; {@code null} if there is none.
	 */
	private static CatalogQueryStore.Sort sortOf(List<Resolved> columns, List<ResolvedJoin> rowsJoins, String key,
			boolean descending) {
		int dot = key.indexOf('.');
		if (dot < 0 || key.startsWith("source.") || key.startsWith("target.")) {
			Integer index = indexOf(columns, key);
			return (index != null && columns.get(index).result().sortable())
					? new CatalogQueryStore.Sort(index, descending) : null;
		}
		String joinId = key.substring(0, dot);
		String column = key.substring(dot + 1);
		for (int j = 0; j < rowsJoins.size(); j++) {
			if (rowsJoins.get(j).join().id().toString().equals(joinId)) {
				List<Column> joinColumns = rowsJoins.get(j).result().columns();
				for (int i = 0; i < joinColumns.size(); i++) {
					if (joinColumns.get(i).key().equals(column) && joinColumns.get(i).sortable()) {
						return new CatalogQueryStore.Sort(j, i, descending);
					}
				}
			}
		}
		return null;
	}

	private static Integer indexOf(List<Resolved> columns, String key) {
		for (int i = 0; i < columns.size(); i++) {
			if (columns.get(i).result().key().equals(key)) {
				return i;
			}
		}
		return null;
	}

}
