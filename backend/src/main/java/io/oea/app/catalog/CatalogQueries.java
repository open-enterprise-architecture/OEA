package io.oea.app.catalog;

import io.oea.app.catalog.CatalogColumns.Resolved;
import io.oea.app.metamodel.OverrideAccessAudit;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogQueryStore.Condition;
import io.oea.core.catalog.ScopeStore;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The live query of a catalog (US-049, REQ-046; design D1, D2 and D4 of us-049-show-catalog-data):
 * resolves the columns, checks the paging and sort parameters and reads one page. The catalog is
 * read through {@link Catalogs#get}, so a foreign personal catalog is answered as not existing.
 * Every query runs in a read-only transaction with the time limit {@code oea.catalog.query-timeout},
 * is measured as {@code oea.catalog.query} and leads to the next page through a cursor (design
 * D2–D5 of s9-catalog-query-performance).
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

	private final CatalogCursors cursors;

	private final CatalogQueryMetrics metrics;

	private final AuditWriter audit;

	private final OverrideAccessAudit overrideAudit;

	private final TransactionTemplate transactions;

	private final long timeoutMillis;

	private final PropertyAccess access;

	private final ScopeStore scopes;

	CatalogQueries(Catalogs catalogs, CatalogQueryStore store, CatalogColumns columns, CatalogCursors cursors,
			CatalogQueryMetrics metrics, AuditWriter audit, PlatformTransactionManager transactionManager,
			CatalogQueryProperties properties, PropertyAccess access, ScopeStore scopes) {
		this.access = access;
		this.scopes = scopes;
		this.catalogs = catalogs;
		this.store = store;
		this.columns = columns;
		this.cursors = cursors;
		this.metrics = metrics;
		this.audit = audit;
		this.overrideAudit = new OverrideAccessAudit(audit);
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setReadOnly(true);
		this.timeoutMillis = properties.queryTimeout().toMillis();
	}

	/** The channel of a query, a tag of the metric and part of the cursor's fingerprint. */
	public enum Channel {

		CATALOG, PORTAL;

		String tag() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * The parameters of a query: paging, sort, join modes, filters, view and the cursor of the
	 * previous page, each {@code null} or empty when not given.
	 */
	public record Request(int page, int pageSize, String sort, Map<String, String> joinModes, List<String> filters,
			String filterMode, UUID view, String cursor) {
	}

	/**
	 * A column of the result; the key names the value in each row. {@code filterType} is
	 * {@code text}, {@code enum}, {@code boolean}, {@code number} or {@code date};
	 * {@code enumValues} lists the values of an enum column and is {@code null} for other types.
	 */
	public record Column(String key, String label, boolean sortable, String filterType, List<String> enumValues,
			boolean readable) {

		public Column(String key, String label, boolean sortable, String filterType, List<String> enumValues) {
			this(key, label, sortable, filterType, enumValues, true);
		}

		/** The column for a reader who may not see its values: not readable and not sortable. */
		Column redacted() {
			return new Column(this.key, this.label, false, this.filterType, this.enumValues, false);
		}

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

	/**
	 * One page of the catalog; {@code sort} is {@code null} for the default order, and
	 * {@code nextCursor} leads to the following page, {@code null} on the last one.
	 */
	public record Result(List<Column> columns, List<QueryJoin> joins, List<Row> rows, int page, int pageSize,
			long totalElements, int totalPages, Sort sort, String nextCursor) {
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
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes, List<String> filters, String filterMode) {
		return query(principal, catalogId, page, pageSize, sort, joinModes, filters, filterMode, null);
	}

	/**
	 * One page of the catalog as above; with {@code view}, the columns are those of the saved view
	 * in its order, and filters and sort resolve against them (design D4 of us-053-saved-views).
	 * An unknown or unavailable view adds {@code view} to the invalid parameters.
	 */
	public Result query(OeaPrincipal principal, UUID catalogId, int page, int pageSize, String sort,
			Map<String, String> joinModes, List<String> filters, String filterMode, UUID view) {
		return query(principal, catalogId,
				new Request(page, pageSize, sort, joinModes, filters, filterMode, view, null), Channel.CATALOG);
	}

	/**
	 * One page of the catalog as above, through the channel. With {@code cursor}, the page starts
	 * after the last row of the previous page (design D2, D3 of s9-catalog-query-performance); a
	 * cursor of another query, of another page or one that does not parse adds {@code cursor} to
	 * the invalid parameters. A query above the time limit is cancelled, audited and answered with
	 * {@link CatalogQueryTimeoutException} (design D4). The time is recorded as
	 * {@code oea.catalog.query} with the outcome {@code success}, {@code rejected} (invalid
	 * parameters or a catalog that is not visible) or {@code timeout} (design D5).
	 */
	public Result query(OeaPrincipal principal, UUID catalogId, Request request, Channel channel) {
		long started = this.metrics.start();
		try {
			Result result = this.transactions.execute((status) -> {
				this.store.limitStatements(this.timeoutMillis);
				return read(principal, catalogId, request, channel);
			});
			this.metrics.record(started, channel, "success");
			return result;
		}
		catch (InvalidCatalogQueryException | NoSuchElementException ex) {
			this.metrics.record(started, channel, "rejected");
			throw ex;
		}
		catch (DataAccessException ex) {
			if (!cancelled(ex)) {
				throw ex;
			}
			this.metrics.record(started, channel, "timeout");
			this.audit.appendIndependently(new AuditEvent("catalog.query-timeout", AuditEvent.Category.SECURITY,
					AuditEvent.Severity.WARNING, AuditEvent.Outcome.FAILURE, AuditEvent.Source.API,
					Sessions.actor(principal), "catalog", catalogId.toString(),
					Map.of("channel", channel.tag(), "limitMillis", this.timeoutMillis)));
			throw new CatalogQueryTimeoutException(ex);
		}
	}

	/** Whether the database cancelled a statement ({@code 57014 query_canceled}), e.g. by its time limit. */
	static boolean cancelled(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && "57014".equals(sql.getSQLState())) {
				return true;
			}
		}
		return false;
	}

	private Result read(OeaPrincipal principal, UUID catalogId, Request request, Channel channel) {
		int page = request.page();
		int pageSize = request.pageSize();
		String sort = request.sort();
		Map<String, String> joinModes = request.joinModes();
		List<String> filters = request.filters();
		String filterMode = request.filterMode();
		UUID view = request.view();
		Catalogs.Catalog catalog = this.catalogs.get(principal, catalogId);
		PropertyAccess.Snapshot access = this.access.of(principal);
		List<String> invalid = new ArrayList<>();
		if (page < 0) {
			invalid.add("page");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			invalid.add("pageSize");
		}
		List<Resolved> columns = this.columns.primary(catalog.primaryMetatypeId(), catalog.columns())
			.stream()
			.map((column) -> readable(column, access))
			.toList();
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
		List<ResolvedJoin> available = joins(catalog, (joinModes != null) ? joinModes : Map.of(), invalid, access);
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
		List<CatalogQueryStore.QueryColumn> queryColumns = resolved.stream().map(Resolved::column).toList();
		String fingerprint = fingerprint(catalog, channel, resolved, view, applied, joins, filters, or, pageSize);
		List<Object> after = null;
		if (request.cursor() != null && !request.cursor().isEmpty()) {
			after = this.cursors
				.decode(request.cursor(), fingerprint, page,
						this.store.keyTypes(queryColumns, storeRowsJoins, storeSort))
				.orElseThrow(() -> new InvalidCatalogQueryException(List.of("cursor")));
		}
		long total = this.store.count(catalog.primaryMetatypeId(), storeRowsJoins, filter);
		List<CatalogQueryStore.Row> page0 = this.store.page(catalog.primaryMetatypeId(), queryColumns, storeRowsJoins,
				filter, storeSort, (long) page * pageSize, pageSize, after);
		String nextCursor = (!page0.isEmpty() && (long) (page + 1) * pageSize < total)
				? this.cursors.encode(fingerprint, page + 1, page0.get(page0.size() - 1).keys()) : null;
		if (nextCursor != null && nextCursor.length() > CatalogCursors.MAX_LENGTH) {
			nextCursor = null; // sort values too long for a cursor, e.g. long descriptions: page applies
		}
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
		auditOverride(principal, catalogId, channel, page, access, resolved, joins, rows);
		return new Result(resolved.stream().map(Resolved::result).toList(),
				joins.stream().map(ResolvedJoin::result).toList(), rows, page, pageSize, total,
				Math.toIntExact((total + pageSize - 1) / pageSize), applied, nextCursor);
	}

	/**
	 * The fingerprint of a query (design D3 of s9-catalog-query-performance): catalog and its last
	 * change, channel, the resolved columns in order, view, sort, join modes, filters and filter
	 * mode, and page size. The filters count without their order, which does not change the result.
	 */
	private static String fingerprint(Catalogs.Catalog catalog, Channel channel, List<Resolved> columns, UUID view,
			Sort sort, List<ResolvedJoin> joins, List<String> filters, boolean or, int pageSize) {
		List<String> parts = new ArrayList<>();
		parts.add(catalog.id().toString());
		parts.add(String.valueOf(catalog.lastModifiedAt()));
		parts.add(channel.tag());
		parts.add(String.join(",", columns.stream().map((column) -> column.result().key()).toList()));
		parts.add(String.valueOf(view));
		parts.add((sort != null) ? sort.column() + ":" + (sort.descending() ? "desc" : "asc") : "default");
		parts.add(String.join(",", joins.stream().map((join) -> join.join().id() + "=" + join.result().mode()).toList()));
		List<String> sorted = new ArrayList<>((filters != null) ? filters : List.of());
		Collections.sort(sorted);
		parts.add(String.valueOf(sorted.size()));
		parts.addAll(sorted);
		parts.add(or ? "or" : "and");
		parts.add(String.valueOf(pageSize));
		return CatalogCursors.fingerprint(parts);
	}

	/**
	 * The available joins with their modes: the override, else the join's default, else the
	 * catalog's. An override for an unknown or unavailable join, or with another value than
	 * {@code rows} or {@code aggregate}, adds {@code joinMode} to {@code invalid}.
	 */
	private List<ResolvedJoin> joins(Catalogs.Catalog catalog, Map<String, String> modes, List<String> invalid,
			PropertyAccess.Snapshot access) {
		Map<String, String> remaining = new LinkedHashMap<>(modes);
		List<ResolvedJoin> joins = new ArrayList<>();
		for (Catalogs.Join join : catalog.joins()) {
			if (!join.available()) {
				continue;
			}
			List<Resolved> columns = this.columns.configured(join.targetMetatypeId(), join.targetColumns())
				.stream()
				.map((column) -> readable(column, access))
				.toList();
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
						column.result().enumValues(), column.result().readable()))
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
			if (column == null || !column.column().result().readable()) {
				// no filter on a column the caller may not read (design D2 of us-126-role-restricted-enforcement)
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
	 * The column as the caller may see it (design D2 of us-126-role-restricted-enforcement): a
	 * property the caller may not read becomes a redacted column without values that cannot be
	 * sorted or filtered.
	 */
	private static Resolved readable(Resolved column, PropertyAccess.Snapshot access) {
		CatalogQueryStore.QueryColumn query = column.column();
		if (query.property() == null) {
			return column;
		}
		// a connection-scoped column shows its values per row in the caller's scope (us-127 D5);
		// a holder of an override role reads all of them (us-128 D3)
		java.util.Optional<PropertyAccess.Scope> scope = access.scope(query.property().propertyDefId());
		if (scope.isPresent() && !access.override()) {
			return new Resolved(query.scoped(new CatalogQueryStore.ColumnScope(scope.get().scopingType(),
					scope.get().anchor())), column.result(), column.defaultSortDirection());
		}
		if (access.readable(query.property().propertyDefId())) {
			return column;
		}
		return new Resolved(CatalogQueryStore.QueryColumn.redacted(), column.result().redacted(), null);
	}

	/**
	 * Audits the values of the page that the caller reads only through the override role list
	 * (REQ-128 AC2; design D3 of us-128-override-role-list): one {@code property-override-access}
	 * event per query naming, per property, the entities whose value was returned through the
	 * override. Values a read role or the scope grants do not count; a page without such values
	 * writes nothing. The event is written in its own transaction, as the query is read-only.
	 */
	private void auditOverride(OeaPrincipal principal, UUID catalogId, Channel channel, int page,
			PropertyAccess.Snapshot access, List<Resolved> columns, List<ResolvedJoin> joins, List<Row> rows) {
		if (!access.override() || rows.isEmpty()) {
			return;
		}
		Map<UUID, String> names = new LinkedHashMap<>();
		Map<UUID, Set<Integer>> entities = new LinkedHashMap<>();
		Map<Integer, int[]> endpoints = null;
		for (Resolved column : columns) {
			CatalogQueryStore.QueryColumn query = column.column();
			if (query.property() == null || !overridden(query, access)) {
				continue;
			}
			if (query.endpoint() != null && endpoints == null) {
				endpoints = this.scopes.endpoints(rows.stream().map(Row::entityId).distinct().toList());
			}
			Set<Integer> found = new TreeSet<>();
			for (Row row : rows) {
				if (row.values().get(column.result().key()) == null) {
					continue;
				}
				if (query.endpoint() == null) {
					found.add(row.entityId());
				}
				else if (endpoints.containsKey(row.entityId())) {
					found.add(endpoints.get(row.entityId())[(query.endpoint() == CatalogQueryStore.Endpoint.SOURCE) ? 0 : 1]);
				}
			}
			collect(query, access, found, names, entities);
		}
		for (ResolvedJoin join : joins) {
			for (Resolved column : join.columns()) {
				CatalogQueryStore.QueryColumn query = column.column();
				if (query.property() == null || !overridden(query, access)) {
					continue;
				}
				Set<Integer> found = new TreeSet<>();
				for (Row row : rows) {
					for (JoinResult result : row.joinResults().getOrDefault(join.join().id(), List.of())) {
						if (result.values().get(column.result().key()) != null) {
							found.add(result.entityId());
						}
					}
				}
				collect(query, access, found, names, entities);
			}
		}
		this.overrideAudit.append(principal, channel.tag(), page, "catalog", catalogId.toString(), names, entities);
	}

	/** Whether the column's values are read through the override, for all or (scoped) some entities. */
	private static boolean overridden(CatalogQueryStore.QueryColumn query, PropertyAccess.Snapshot access) {
		UUID id = query.property().propertyDefId();
		return access.throughOverride(id) || (access.connectionScoped(id) && access.scope(id).isPresent());
	}

	/** Adds the entities with a value of the column, minus those in the caller's scope. */
	private void collect(CatalogQueryStore.QueryColumn query, PropertyAccess.Snapshot access, Set<Integer> found,
			Map<UUID, String> names, Map<UUID, Set<Integer>> entities) {
		UUID id = query.property().propertyDefId();
		java.util.Optional<PropertyAccess.Scope> scope = access.throughOverride(id) ? java.util.Optional.empty()
				: access.scope(id);
		if (scope.isPresent()) {
			found.removeAll(this.scopes.inScope(scope.get().anchor(), scope.get().scopingType(), found));
		}
		if (found.isEmpty()) {
			return;
		}
		names.put(id, query.property().name());
		entities.computeIfAbsent(id, (key) -> new TreeSet<>()).addAll(found);
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
