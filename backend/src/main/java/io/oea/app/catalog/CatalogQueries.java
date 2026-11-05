package io.oea.app.catalog;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.PropertyColumn;
import io.oea.core.catalog.CatalogQueryStore.QueryColumn;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
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

	/** Name of the property shown as default column besides the name (design D2). */
	static final String STATUS_PROPERTY = "Status";

	static final int MAX_PAGE_SIZE = 200;

	private static final Pattern SORT = Pattern
		.compile("((?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.|source\\.|target\\.)?"
				+ "[a-zA-Z][a-zA-Z0-9_]*):(asc|desc)");

	private final Catalogs catalogs;

	private final CatalogQueryStore store;

	private final Clock clock;

	public CatalogQueries(Catalogs catalogs, CatalogQueryStore store, Clock clock) {
		this.catalogs = catalogs;
		this.store = store;
		this.clock = clock;
	}

	/** A column of the result; the key names the value in each row. */
	public record Column(String key, String label, boolean sortable) {
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

	/** An available join with its store definition, its result and whether it expands rows. */
	private record ResolvedJoin(Catalogs.Join join, CatalogQueryStore.Join store, QueryJoin result, boolean rows) {
	}

	/** A resolved column with the store column, its key and label, and the configured default sort. */
	private record Resolved(QueryColumn column, Column result, String defaultSortDirection) {
	}

	/**
	 * One page of the catalog. Order: visibility of the catalog (404), paging parameters, sort and
	 * join modes (400 naming the parameters). Configured columns apply when one of them can be
	 * shown (design D4 of us-047-configure-columns), else the default columns. {@code joinModes}
	 * maps join IDs to {@code rows} or {@code aggregate} (design D4 of us-048-catalog-joins).
	 */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes) {
		Catalogs.Catalog catalog = this.catalogs.get(principal, catalogId);
		List<String> invalid = new ArrayList<>();
		if (page < 0) {
			invalid.add("page");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			invalid.add("pageSize");
		}
		List<Resolved> columns = configured(catalog.primaryMetatypeId(), catalog.columns());
		if (columns.isEmpty()) {
			columns = defaults(catalog.primaryMetatypeId());
		}
		List<ResolvedJoin> joins = joins(catalog, (joinModes != null) ? joinModes : Map.of(), invalid);
		List<ResolvedJoin> rowsJoins = joins.stream().filter(ResolvedJoin::rows).toList();
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
		if (!invalid.isEmpty()) {
			throw new InvalidCatalogQueryException(invalid);
		}
		List<Resolved> resolved = columns;
		List<CatalogQueryStore.Join> storeRowsJoins = rowsJoins.stream().map(ResolvedJoin::store).toList();
		long total = this.store.count(catalog.primaryMetatypeId(), storeRowsJoins);
		List<CatalogQueryStore.Row> page0 = this.store.page(catalog.primaryMetatypeId(),
				resolved.stream().map(Resolved::column).toList(), storeRowsJoins, storeSort, (long) page * pageSize,
				pageSize);
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
			List<Resolved> columns = configured(join.targetMetatypeId(), join.targetColumns());
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
						rows && column.result().sortable()))
				.toList();
			joins.add(new ResolvedJoin(join,
					new CatalogQueryStore.Join(join.connectionMetatypeId(),
							CatalogQueryStore.Direction.valueOf(join.direction().toUpperCase(java.util.Locale.ROOT)),
							join.targetMetatypeId(), columns.stream().map(Resolved::column).toList()),
					new QueryJoin(join.id(), join.name(), mode, results), rows));
		}
		if (!remaining.isEmpty() && !invalid.contains("joinMode")) {
			invalid.add("joinMode");
		}
		return joins;
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

	/** The visible configured columns that can be shown for the metatype, in their order. */
	private List<Resolved> configured(UUID metatypeId, List<Catalogs.Column> configured) {
		if (configured.isEmpty()) {
			return List.of();
		}
		Map<UUID, PropertyColumn> available = this.store.availableProperties(metatypeId, LocalDate.now(this.clock));
		Map<Catalogs.Endpoint, Map<UUID, PropertyColumn>> offered = new EnumMap<>(Catalogs.Endpoint.class);
		List<Resolved> columns = new ArrayList<>();
		for (Catalogs.Column column : configured) {
			if (!column.visible()) {
				continue;
			}
			Catalogs.Endpoint side = column.endpoint();
			Map<UUID, PropertyColumn> properties = (side == null) ? available : offered.computeIfAbsent(side,
					(key) -> this.store.endpointProperties(metatypeId, key.key(), LocalDate.now(this.clock)));
			QueryColumn query;
			String key;
			String name;
			if (column.field() != null) {
				query = QueryColumn.of(storeField(column.field()));
				key = column.field().key();
				name = column.field().label();
			}
			else if (properties.containsKey(column.propertyDefId())) {
				PropertyColumn property = properties.get(column.propertyDefId());
				query = QueryColumn.of(property);
				key = property.sqlColumnName();
				name = property.name();
			}
			else {
				continue;
			}
			if (side != null) {
				query = query.at(CatalogQueryStore.Endpoint.valueOf(side.name()));
				name = endpointLabel(side, key, name);
				key = side.key() + "." + key;
			}
			columns.add(new Resolved(query,
					new Column(key, (column.displayLabel() != null) ? column.displayLabel() : name, column.sortable()),
					column.defaultSortDirection()));
		}
		return columns;
	}

	/**
	 * The columns of a catalog without configured columns (US-049): the name and the property
	 * {@value #STATUS_PROPERTY} if the metatype maps it today with visibility {@code public}.
	 */
	private List<Resolved> defaults(UUID metatypeId) {
		List<Resolved> columns = new ArrayList<>();
		boolean connection = this.store.isConnection(metatypeId);
		if (connection) {
			columns.add(endpointName(Catalogs.Endpoint.SOURCE));
		}
		columns.add(new Resolved(QueryColumn.of(CatalogQueryStore.SystemField.NAME),
				new Column(NAME_COLUMN, "Name", true), null));
		if (connection) {
			columns.add(endpointName(Catalogs.Endpoint.TARGET));
		}
		this.store.mappedPublicProperty(metatypeId, STATUS_PROPERTY, LocalDate.now(this.clock))
			.ifPresent((property) -> columns.add(new Resolved(QueryColumn.of(property),
					new Column(property.sqlColumnName(), property.name(), true), null)));
		return columns;
	}

	/** The name of the source or target as a default column of a connection catalog (US-080). */
	private static Resolved endpointName(Catalogs.Endpoint side) {
		return new Resolved(
				QueryColumn.of(CatalogQueryStore.SystemField.NAME).at(CatalogQueryStore.Endpoint.valueOf(side.name())),
				new Column(side.key() + "." + NAME_COLUMN, side.label(), true), null);
	}

	/** "Source" or "Target" for the name, else "Source: {attribute}" or "Target: {attribute}". */
	private static String endpointLabel(Catalogs.Endpoint side, String key, String name) {
		return NAME_COLUMN.equals(key) ? side.label() : side.label() + ": " + name;
	}

	private static CatalogQueryStore.SystemField storeField(Catalogs.SystemField field) {
		return CatalogQueryStore.SystemField.valueOf(field.name());
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
