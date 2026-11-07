package io.oea.app.catalog;

import io.oea.app.metamodel.AllowedConnections;
import io.oea.app.metamodel.PackageImporter;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.catalog.CatalogStore.StoredCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Creating and reading catalogs (US-046, REQ-043; design D3–D6 of us-046-create-catalog). A
 * catalog with scope {@code instance} needs {@code catalog:manage}; a personal catalog is visible
 * only to its creator and answered as not existing for everybody else.
 */
@Service
public class Catalogs {

	/** Permission for shared catalogs, the contract condition of {@code createCatalog}. */
	public static final String MANAGE_PERMISSION = "catalog:manage";

	static final int NAME_MAX_LENGTH = 255;

	static final int DESCRIPTION_MAX_LENGTH = 1000;

	static final int LABEL_MAX_LENGTH = 100;

	static final int MAX_COLUMNS = 100;

	static final int MAX_JOINS = 10;

	static final int MAX_TARGET_COLUMNS = 20;

	static final int JOIN_NAME_MAX_LENGTH = 100;

	static final int MAX_SAVED_FILTERS = 50;

	static final int FILTER_NAME_MAX_LENGTH = 100;

	static final int MAX_SAVED_VIEWS = 20;

	static final int MAX_VIEW_COLUMNS = 100;

	private final CatalogStore store;

	private final CatalogQueryStore queries;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final AllowedConnections connections;

	private final CatalogColumns columns;

	private final Clock clock;

	Catalogs(CatalogStore store, CatalogQueryStore queries, PermissionResolver permissions, AuditWriter audit,
			AllowedConnections connections, CatalogColumns columns, Clock clock) {
		this.store = store;
		this.queries = queries;
		this.permissions = permissions;
		this.audit = audit;
		this.connections = connections;
		this.columns = columns;
		this.clock = clock;
	}

	/** Visibility of a catalog. */
	public enum Scope {

		INSTANCE, PERSONAL;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** Join mode of a catalog. */
	public enum JoinMode {

		AGGREGATE, ROWS;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * A catalog with the current name of its primary metatype, its creator, its columns, joins and
	 * saved filters.
	 */
	public record Catalog(UUID id, String name, String description, UUID primaryMetatypeId, String primaryMetatypeName,
			String defaultJoinMode, String scope, long createdById, String createdByName, Instant createdAt,
			Instant lastModifiedAt, List<Column> columns, List<Join> joins, List<SavedFilter> savedFilters,
			List<SavedView> savedViews) {
	}

	/**
	 * A saved view (US-053, design D2 of us-053-saved-views), a snapshot of the view state:
	 * {@code columnOrder} holds column keys of the primary columns, {@code filterMode} is
	 * {@code and} or {@code or}, {@code sort} may be {@code null}; {@code available} is false if
	 * the view names a column, a join or a sort column the catalog no longer has.
	 */
	public record SavedView(UUID id, String name, String description, List<String> columnOrder,
			List<FilterExpression> filters, String filterMode, List<JoinModeOverride> joinModeOverrides, ViewSort sort,
			boolean isDefault, boolean available) {
	}

	/** Input of a saved view, as {@link SavedView} without ID and availability. */
	public record SavedViewInput(String name, String description, List<String> columnOrder,
			List<FilterExpression> filters, String filterMode, List<JoinModeOverride> joinModeOverrides, ViewSort sort,
			boolean isDefault) {
	}

	/** The mode of a join in a view: {@code rows} or {@code aggregate}. */
	public record JoinModeOverride(UUID joinDefinitionId, String joinMode) {
	}

	/** The sort of a view: a column key and {@code asc} or {@code desc}. */
	public record ViewSort(String column, String direction) {
	}

	/**
	 * A saved filter (US-052, design D4 of us-051-catalog-filters): {@code logicalOperator} is
	 * {@code and} or {@code or}; {@code available} is false if an expression names a column the
	 * query no longer returns.
	 */
	public record SavedFilter(UUID id, String name, String description, String logicalOperator,
			List<FilterExpression> expressions, boolean available) {
	}

	/**
	 * An expression of a saved filter: a column key of the query result, an operator key such as
	 * {@code startsWith} and the value, a {@link String}, a list of strings for {@code in} and
	 * {@code notIn}, or {@code null} for {@code isNull} and {@code isNotNull}.
	 */
	public record FilterExpression(String attributePath, String operator, Object value) {
	}

	/** Input of a saved filter, as {@link SavedFilter} without ID and availability. */
	public record SavedFilterInput(String name, String description, String logicalOperator,
			List<FilterExpression> expressions) {
	}

	/**
	 * A join (US-048, design D1 of us-048-catalog-joins): {@code direction} is {@code outbound},
	 * {@code inbound} or {@code both}; {@code defaultJoinMode} is {@code rows}, {@code aggregate} or
	 * {@code null} for the catalog's; {@code available} is false if the catalog can no longer show it.
	 */
	public record Join(UUID id, String name, UUID connectionMetatypeId, String direction, UUID targetMetatypeId,
			List<Column> targetColumns, String defaultJoinMode, boolean available) {
	}

	/** Input of a join, as {@link Join} without ID and availability. */
	public record JoinInput(String name, UUID connectionMetatypeId, String direction, UUID targetMetatypeId,
			List<Column> targetColumns, String defaultJoinMode) {
	}

	/**
	 * A configured column (US-047, design D1 of us-047-configure-columns): exactly one of
	 * {@code field} and {@code propertyDefId}; {@code defaultSortDirection} is {@code asc},
	 * {@code desc} or {@code null}; {@code available} is false for a property the catalog can no
	 * longer show; {@code endpoint} is {@code null} for the primary entity, else the side of the
	 * connection whose attribute the column shows (US-080, design D2 of us-080-connection-catalogs).
	 */
	public record Column(SystemField field, UUID propertyDefId, String displayLabel, boolean visible, boolean sortable,
			String defaultSortDirection, boolean available, Endpoint endpoint) {

		public Column(SystemField field, UUID propertyDefId, String displayLabel, boolean visible, boolean sortable,
				String defaultSortDirection, boolean available) {
			this(field, propertyDefId, displayLabel, visible, sortable, defaultSortDirection, available, null);
		}

	}

	/** The side of a connection whose attribute a column shows, with its key and label (US-080). */
	public enum Endpoint {

		SOURCE("source", "Source"), TARGET("target", "Target");

		private final String key;

		private final String label;

		Endpoint(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return this.key;
		}

		public String label() {
			return this.label;
		}

		public static Endpoint ofKey(String key) {
			for (Endpoint endpoint : values()) {
				if (endpoint.key.equals(key)) {
					return endpoint;
				}
			}
			return null;
		}

	}

	/** The system fields of a column with their keys in the contract and the query. */
	public enum SystemField {

		NAME("name", "Name"), DESCRIPTION("description", "Description"), EXTERNAL_ID("externalId", "External ID"),
		CREATED_AT("createdAt", "Created"), UPDATED_AT("updatedAt", "Updated"),
		ENTITY_VERSION("entityVersion", "Version");

		private final String key;

		private final String label;

		SystemField(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return this.key;
		}

		public String label() {
			return this.label;
		}

		public static SystemField ofKey(String key) {
			for (SystemField field : values()) {
				if (field.key.equals(key)) {
					return field;
				}
			}
			return null;
		}

	}

	/** Input of a new catalog; a {@code null} join mode means {@code aggregate}. */
	public record NewCatalog(String name, String description, UUID primaryMetatypeId, Scope scope,
			JoinMode defaultJoinMode) {
	}

	/**
	 * Creates the catalog and audits it. Order: scope permission, fields, primary metatype, name
	 * (by the unique index, so concurrent creations cannot both succeed).
	 */
	@Transactional
	public Catalog create(OeaPrincipal principal, NewCatalog input) {
		long personId = person(principal);
		if (input.scope() == Scope.INSTANCE && !this.permissions.permissions(principal).contains(MANAGE_PERMISSION)) {
			throw new PermissionDeniedException();
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("name", "The name must have 1 to 255 characters.");
		}
		String description = (input.description() != null) ? input.description() : "";
		if (description.length() > DESCRIPTION_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("description", "The description must have at most 1000 characters.");
		}
		if (input.primaryMetatypeId() == null
				|| !this.store.metatypeExists(input.primaryMetatypeId(), PackageImporter.INSTANCE_CONFIGURATION)) {
			throw InvalidCatalogException.invalid("primaryMetatypeId",
					"The primary entity type is not defined in the metamodel.");
		}
		JoinMode joinMode = (input.defaultJoinMode() != null) ? input.defaultJoinMode() : JoinMode.AGGREGATE;
		UUID id;
		try {
			id = this.store.create(new CatalogStore.NewCatalog(name, description, input.primaryMetatypeId(),
					joinMode.dbValue(), input.scope().dbValue()), personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(CatalogStore.NAME_INDEX)) {
				throw InvalidCatalogException.nameTaken();
			}
			throw ex;
		}
		this.audit.append(new AuditEvent("catalog.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				Map.of("name", name, "primaryMetatypeId", input.primaryMetatypeId().toString(), "scope",
						input.scope().dbValue())));
		return catalog(this.store.findVisible(id, personId).orElseThrow());
	}

	/** Instance catalogs and the caller's personal catalogs, ordered by name. */
	@Transactional(readOnly = true)
	public List<Catalog> list(OeaPrincipal principal) {
		return this.store.visibleTo(person(principal)).stream().map(this::catalog).toList();
	}

	/** The catalogs with scope {@code instance}, ordered by name (portal, s9-portal-catalogs D1). */
	@Transactional(readOnly = true)
	public List<Catalog> listShared() {
		return this.store.shared().stream().map(this::catalog).toList();
	}

	/** The catalog; not existing and another person's personal catalog are answered alike. */
	@Transactional(readOnly = true)
	public Catalog get(OeaPrincipal principal, UUID id) {
		return this.store.findVisible(id, person(principal))
			.map(this::catalog)
			.orElseThrow(() -> new NoSuchElementException("catalog " + id));
	}

	/**
	 * Replaces the columns of the catalog (design D2, D3 of us-047-configure-columns). Without
	 * {@code catalog:manage}, only one's own personal catalog can be changed and every other ID is
	 * answered with 403, so that other personal catalogs stay hidden; with it, an unknown ID and
	 * another person's personal catalog are answered with 404.
	 */
	@Transactional
	public Catalog updateColumns(OeaPrincipal principal, UUID id, List<Column> columns) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		List<Column> checked = validate(stored.primaryMetatypeId(), (columns != null) ? columns : List.of(), "columns",
				MAX_COLUMNS, true, this.store.isConnection(stored.primaryMetatypeId(),
						PackageImporter.INSTANCE_CONFIGURATION).orElse(false));
		this.store.updateColumns(id, columnsJson(checked).toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("columns", checked.size());
		details.put("visibleColumns", checked.stream().filter(Column::visible).count());
		this.audit.append(new AuditEvent("catalog.columns-changed", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"catalog", id.toString(), details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/**
	 * Adds a join (design D2 of us-048-catalog-joins); permissions as for {@link #updateColumns}.
	 */
	@Transactional
	public Catalog addJoin(OeaPrincipal principal, UUID id, JoinInput input) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode joins = array(stored.joinsJson());
		Join checked = validate(stored, input, UUID.randomUUID());
		if (joins.size() >= MAX_JOINS) {
			throw InvalidCatalogException.invalid("joinDefinitions", "A catalog has at most 10 joins.");
		}
		joins.add(joinJson(checked));
		this.store.updateJoins(id, joins.toString(), array(stored.savedViewsJson()).toString(),
				array(stored.savedFiltersJson()).toString(), personId, this.clock.instant());
		auditJoin(principal, "catalog.join-added", id, checked);
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Replaces the definition of a join and keeps its ID; 404 for an unknown join. */
	@Transactional
	public Catalog updateJoin(OeaPrincipal principal, UUID id, UUID joinId, JoinInput input) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode joins = array(stored.joinsJson());
		int index = indexOfJoin(joins, joinId);
		Join checked = validate(stored, input, joinId);
		joins.set(index, joinJson(checked));
		this.store.updateJoins(id, joins.toString(), array(stored.savedViewsJson()).toString(),
				array(stored.savedFiltersJson()).toString(), personId, this.clock.instant());
		auditJoin(principal, "catalog.join-changed", id, checked);
		return catalog(this.store.find(id).orElseThrow());
	}

	/**
	 * Removes a join; from the saved views the join mode overrides (US-048 AC6), filters and sort
	 * that refer to it (design D3 of us-053-saved-views); the saved-filter expressions on its
	 * columns, with the saved filters left without expressions (BR-04, design D4 of
	 * us-051-catalog-filters). 404 for an unknown join.
	 */
	@Transactional
	public Catalog removeJoin(OeaPrincipal principal, UUID id, UUID joinId) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode joins = array(stored.joinsJson());
		int index = indexOfJoin(joins, joinId);
		String name = joins.get(index).path("name").asString("");
		joins.remove(index);
		ArrayNode views = array(stored.savedViewsJson());
		String prefix = joinId + ".";
		for (JsonNode view : views) {
			if (view.get("joinModeOverrides") instanceof ArrayNode overrides) {
				for (int i = overrides.size() - 1; i >= 0; i--) {
					if (joinId.toString().equals(overrides.get(i).path("joinDefinitionId").asString(null))) {
						overrides.remove(i);
					}
				}
			}
			if (view.get("filters") instanceof ArrayNode viewFilters) {
				for (int i = viewFilters.size() - 1; i >= 0; i--) {
					if (viewFilters.get(i).path("attributePath").asString("").startsWith(prefix)) {
						viewFilters.remove(i);
					}
				}
			}
			if (view.path("sort").path("column").asString("").startsWith(prefix)) {
				((ObjectNode) view).putNull("sort");
			}
		}
		ArrayNode filters = array(stored.savedFiltersJson());
		for (int f = filters.size() - 1; f >= 0; f--) {
			if (filters.get(f).get("expressions") instanceof ArrayNode expressions) {
				for (int i = expressions.size() - 1; i >= 0; i--) {
					if (expressions.get(i).path("attributePath").asString("").startsWith(prefix)) {
						expressions.remove(i);
					}
				}
				if (expressions.isEmpty()) {
					filters.remove(f);
				}
			}
		}
		this.store.updateJoins(id, joins.toString(), views.toString(), filters.toString(), personId,
				this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("joinId", joinId.toString());
		details.put("name", name);
		this.audit.append(new AuditEvent("catalog.join-removed", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/**
	 * Saves a filter (US-052, design D4 of us-051-catalog-filters); permissions as for
	 * {@link #updateColumns}. Order: name, description, operator, expressions (each against the
	 * columns of the query, design D2), the name's uniqueness, the limit of
	 * {@value #MAX_SAVED_FILTERS} filters.
	 */
	@Transactional
	public Catalog addFilter(OeaPrincipal principal, UUID id, SavedFilterInput input) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > FILTER_NAME_MAX_LENGTH
				|| name.chars().anyMatch(Character::isISOControl)) {
			throw InvalidCatalogException.invalid("name",
					"The name must have 1 to 100 characters and no control characters.");
		}
		String description = (input.description() != null && !input.description().isBlank())
				? input.description().strip() : null;
		if (description != null && (description.length() > DESCRIPTION_MAX_LENGTH || description.indexOf(0) >= 0)) {
			throw InvalidCatalogException.invalid("description",
					"The description must have at most 1000 characters and no NUL character.");
		}
		String operator = input.logicalOperator();
		if (!"and".equals(operator) && !"or".equals(operator)) {
			throw InvalidCatalogException.invalid("logicalOperator", "The operator is and or or.");
		}
		List<FilterExpression> expressions = (input.expressions() != null) ? input.expressions() : List.of();
		if (expressions.isEmpty() || expressions.size() > FilterRules.MAX_FILTERS) {
			throw InvalidCatalogException.invalid("expressions", "A saved filter has 1 to 20 expressions.");
		}
		Catalog catalog = catalog(stored);
		Map<String, CatalogColumns.Filterable> filterable = this.columns.filterable(catalog.primaryMetatypeId(),
				catalog.columns(), catalog.joins());
		List<FilterExpression> checked = new ArrayList<>();
		for (int i = 0; i < expressions.size(); i++) {
			checked.add(check(filterable, expressions.get(i), "expressions[" + i + "]"));
		}
		ArrayNode filters = array(stored.savedFiltersJson());
		for (JsonNode filter : filters) {
			if (filter.path("name").asString("").strip().equalsIgnoreCase(name)) {
				throw InvalidCatalogException.invalid("name", "A saved filter of the catalog already has this name.");
			}
		}
		if (filters.size() >= MAX_SAVED_FILTERS) {
			throw InvalidCatalogException.invalid("savedFilters", "A catalog has at most 50 saved filters.");
		}
		UUID filterId = UUID.randomUUID();
		ObjectNode node = filters.addObject();
		node.put("id", filterId.toString());
		node.put("name", name);
		node.put("description", description);
		node.put("logicalOperator", operator);
		node.set("expressions", expressionsJson(checked));
		this.store.updateSavedFilters(id, filters.toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("filterId", filterId.toString());
		details.put("name", name);
		details.put("logicalOperator", operator);
		details.put("expressions", checked.size());
		this.audit.append(new AuditEvent("catalog.filter-added", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Removes a saved filter; permissions as for {@link #addFilter}, 404 for an unknown filter. */
	@Transactional
	public Catalog removeFilter(OeaPrincipal principal, UUID id, UUID filterId) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode filters = array(stored.savedFiltersJson());
		int index = -1;
		for (int i = 0; i < filters.size(); i++) {
			if (filterId.toString().equals(filters.get(i).path("id").asString(null))) {
				index = i;
			}
		}
		if (index < 0) {
			throw new NoSuchElementException("saved filter " + filterId);
		}
		String name = filters.get(index).path("name").asString("");
		filters.remove(index);
		this.store.updateSavedFilters(id, filters.toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("filterId", filterId.toString());
		details.put("name", name);
		this.audit.append(new AuditEvent("catalog.filter-removed", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"catalog", id.toString(), details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/**
	 * An expression checked against the columns of the query (design D2 of
	 * us-051-catalog-filters), with its value normalized: a list for {@code in} and {@code notIn},
	 * else a string or, for {@code isNull} and {@code isNotNull}, none. 422 names the field under
	 * {@code at}.
	 */
	private static FilterExpression check(Map<String, CatalogColumns.Filterable> filterable,
			FilterExpression expression, String at) {
		CatalogColumns.Filterable column = (expression.attributePath() != null)
				? filterable.get(expression.attributePath()) : null;
		if (column == null) {
			throw InvalidCatalogException.invalid(at + ".attributePath", "The catalog shows no such column.");
		}
		boolean list = "in".equals(expression.operator()) || "notIn".equals(expression.operator());
		List<String> values = new ArrayList<>();
		Object value = expression.value();
		if (list && value instanceof List<?> items) {
			for (Object item : items) {
				if (!(item instanceof String text) || text.indexOf(',') >= 0) {
					throw InvalidCatalogException.invalid(at + ".value",
							"The values are strings without commas, as the query separates them by commas.");
				}
				values.add(text.strip());
			}
		}
		else if (value instanceof List<?>) {
			throw InvalidCatalogException.invalid(at + ".value", "Only in and notIn take a list of values.");
		}
		else if (list && value != null) {
			throw InvalidCatalogException.invalid(at + ".value", "The operator takes a list of values.");
		}
		else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			values.add(String.valueOf(value));
		}
		else if (value != null) {
			throw InvalidCatalogException.invalid(at + ".value", "The value is a string.");
		}
		if ((expression.attributePath() + ":" + expression.operator() + ":" + String.join(",", values))
			.length() > FilterRules.MAX_FILTER_LENGTH) {
			throw InvalidCatalogException.invalid(at + ".value",
					"The expression has at most 2000 characters as a query filter.");
		}
		try {
			FilterRules.condition(column.column(), expression.operator(), values);
		}
		catch (FilterRules.InvalidFilterException ex) {
			throw InvalidCatalogException.invalid(at + "." + ex.part().field(), ex.getMessage());
		}
		return new FilterExpression(expression.attributePath(), expression.operator(),
				list ? List.copyOf(values) : values.isEmpty() ? null : values.get(0));
	}

	/**
	 * Saves a view (US-053, design D2 and D3 of us-053-saved-views); permissions as for
	 * {@link #updateColumns}. With {@code isDefault}, every other view loses its default (BR-06).
	 */
	@Transactional
	public Catalog addView(OeaPrincipal principal, UUID id, SavedViewInput input) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode views = array(stored.savedViewsJson());
		UUID viewId = UUID.randomUUID();
		SavedViewInput checked = checkView(stored, views, input, null);
		if (views.size() >= MAX_SAVED_VIEWS) {
			throw InvalidCatalogException.invalid("savedViews", "A catalog has at most 20 views.");
		}
		views.add(viewJson(viewId, checked));
		writeViews(principal, "catalog.view-added", stored, views, viewId, checked, personId);
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Replaces a view and keeps its ID; validation as for {@link #addView}, 404 for an unknown view. */
	@Transactional
	public Catalog updateView(OeaPrincipal principal, UUID id, UUID viewId, SavedViewInput input) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode views = array(stored.savedViewsJson());
		int index = indexOfView(views, viewId);
		SavedViewInput checked = checkView(stored, views, input, viewId);
		views.set(index, viewJson(viewId, checked));
		writeViews(principal, "catalog.view-changed", stored, views, viewId, checked, personId);
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Removes a view; the catalog then has no default if it was the default. 404 for an unknown view. */
	@Transactional
	public Catalog removeView(OeaPrincipal principal, UUID id, UUID viewId) {
		long personId = person(principal);
		StoredCatalog stored = writable(principal, id);
		ArrayNode views = array(stored.savedViewsJson());
		int index = indexOfView(views, viewId);
		String name = views.get(index).path("name").asString("");
		views.remove(index);
		this.store.updateSavedViews(id, views.toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("viewId", viewId.toString());
		details.put("name", name);
		this.audit.append(new AuditEvent("catalog.view-removed", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Stores the views, clearing every other default for a default view, and audits the change. */
	private void writeViews(OeaPrincipal principal, String action, StoredCatalog stored, ArrayNode views, UUID viewId,
			SavedViewInput view, long personId) {
		if (view.isDefault()) {
			for (JsonNode node : views) {
				if (!viewId.toString().equals(node.path("id").asString(null))) {
					((ObjectNode) node).put("isDefault", false);
				}
			}
		}
		this.store.updateSavedViews(stored.id(), views.toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("viewId", viewId.toString());
		details.put("name", view.name());
		details.put("isDefault", view.isDefault());
		details.put("columns", view.columnOrder().size());
		details.put("filters", view.filters().size());
		this.audit.append(new AuditEvent(action, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog",
				stored.id().toString(), details));
	}

	/**
	 * Checks a view in the order of design D2 of us-053-saved-views: name (unique except for the
	 * view {@code self}), description, then the view state ({@link #checkState}). Returns it with
	 * the trimmed name and description and the normalized filters.
	 */
	private SavedViewInput checkView(StoredCatalog stored, ArrayNode views, SavedViewInput input, UUID self) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > FILTER_NAME_MAX_LENGTH
				|| name.chars().anyMatch(Character::isISOControl)) {
			throw InvalidCatalogException.invalid("name",
					"The name must have 1 to 100 characters and no control characters.");
		}
		String description = (input.description() != null && !input.description().isBlank())
				? input.description().strip() : null;
		if (description != null && (description.length() > DESCRIPTION_MAX_LENGTH || description.indexOf(0) >= 0)) {
			throw InvalidCatalogException.invalid("description",
					"The description must have at most 1000 characters and no NUL character.");
		}
		for (JsonNode view : views) {
			if (!view.path("id").asString("").equals(String.valueOf(self))
					&& view.path("name").asString("").strip().equalsIgnoreCase(name)) {
				throw InvalidCatalogException.invalid("name", "A view of the catalog already has this name.");
			}
		}
		Catalog catalog = catalog(stored);
		SavedViewInput normalized = new SavedViewInput(name, description,
				(input.columnOrder() != null) ? List.copyOf(input.columnOrder()) : List.of(),
				(input.filters() != null) ? input.filters() : List.of(),
				(input.filterMode() != null) ? input.filterMode() : "and",
				(input.joinModeOverrides() != null) ? List.copyOf(input.joinModeOverrides()) : List.of(), input.sort(),
				input.isDefault());
		List<FilterExpression> filters = checkState(this.columns.viewColumns(catalog.primaryMetatypeId(),
				catalog.columns(), catalog.joins(), catalog.defaultJoinMode()), normalized);
		return new SavedViewInput(name, description, normalized.columnOrder(), filters, normalized.filterMode(),
				normalized.joinModeOverrides(), normalized.sort(), normalized.isDefault());
	}

	/**
	 * Checks the view state against the columns of the catalog (design D2 of us-053-saved-views):
	 * columns, join mode overrides, filters on the view's columns and join columns, the sort on a
	 * sortable column of the view or of a join in rows mode. Returns the normalized filters; 422
	 * names the field.
	 */
	private static List<FilterExpression> checkState(CatalogColumns.ViewColumns available, SavedViewInput view) {
		List<String> columnOrder = view.columnOrder();
		if (columnOrder.isEmpty() || columnOrder.size() > MAX_VIEW_COLUMNS) {
			throw InvalidCatalogException.invalid("columnOrder", "A view shows 1 to 100 columns.");
		}
		Map<String, CatalogColumns.Filterable> filterable = new LinkedHashMap<>();
		for (int i = 0; i < columnOrder.size(); i++) {
			CatalogColumns.Resolved column = available.primary().get(columnOrder.get(i));
			if (column == null || filterable.containsKey(columnOrder.get(i))) {
				throw InvalidCatalogException.invalid("columnOrder[" + i + "]",
						"The catalog shows no such column, or the view lists it twice.");
			}
			filterable.put(columnOrder.get(i), new CatalogColumns.Filterable(column, null));
		}
		Map<UUID, String> modes = new LinkedHashMap<>();
		available.joins().forEach((joinId, join) -> modes.put(joinId, join.defaultMode()));
		Set<UUID> overridden = new HashSet<>();
		for (int i = 0; i < view.joinModeOverrides().size(); i++) {
			JoinModeOverride override = view.joinModeOverrides().get(i);
			if (override.joinDefinitionId() == null || !available.joins().containsKey(override.joinDefinitionId())
					|| !overridden.add(override.joinDefinitionId())) {
				throw InvalidCatalogException.invalid("joinModeOverrides[" + i + "].joinDefinitionId",
						"The catalog has no such join, or the view lists it twice.");
			}
			if (!"rows".equals(override.joinMode()) && !"aggregate".equals(override.joinMode())) {
				throw InvalidCatalogException.invalid("joinModeOverrides[" + i + "].joinMode",
						"The join mode is rows or aggregate.");
			}
			modes.put(override.joinDefinitionId(), override.joinMode());
		}
		available.joins().forEach((joinId, join) -> join.columns()
			.forEach((key, column) -> filterable.put(joinId + "." + key, new CatalogColumns.Filterable(column, joinId))));
		if (!"and".equals(view.filterMode()) && !"or".equals(view.filterMode())) {
			throw InvalidCatalogException.invalid("filterMode", "The filter mode is and or or.");
		}
		if (view.filters().size() > FilterRules.MAX_FILTERS) {
			throw InvalidCatalogException.invalid("filters", "A view has at most 20 filters.");
		}
		List<FilterExpression> filters = new ArrayList<>();
		for (int i = 0; i < view.filters().size(); i++) {
			filters.add(check(filterable, view.filters().get(i), "filters[" + i + "]"));
		}
		ViewSort sort = view.sort();
		if (sort != null) {
			CatalogColumns.Filterable column = (sort.column() != null) ? filterable.get(sort.column()) : null;
			boolean sortable = column != null && column.column().result().sortable()
					&& (column.joinId() == null || "rows".equals(modes.get(column.joinId())));
			if (!sortable || (!"asc".equals(sort.direction()) && !"desc".equals(sort.direction()))) {
				throw InvalidCatalogException.invalid("sort",
						"The sort names a sortable column of the view or of a join in rows mode, asc or desc.");
			}
		}
		return List.copyOf(filters);
	}

	private static ObjectNode viewJson(UUID id, SavedViewInput view) {
		ObjectNode node = JsonMapper.shared().createObjectNode();
		node.put("id", id.toString());
		node.put("name", view.name());
		node.put("description", view.description());
		ArrayNode columns = node.putArray("columnOrder");
		view.columnOrder().forEach(columns::add);
		node.set("filters", expressionsJson(view.filters()));
		node.put("filterMode", view.filterMode());
		ArrayNode overrides = node.putArray("joinModeOverrides");
		for (JoinModeOverride override : view.joinModeOverrides()) {
			overrides.addObject()
				.put("joinDefinitionId", override.joinDefinitionId().toString())
				.put("joinMode", override.joinMode());
		}
		if (view.sort() != null) {
			node.putObject("sort").put("column", view.sort().column()).put("direction", view.sort().direction());
		}
		else {
			node.putNull("sort");
		}
		node.put("isDefault", view.isDefault());
		return node;
	}

	private static int indexOfView(ArrayNode views, UUID viewId) {
		for (int i = 0; i < views.size(); i++) {
			if (viewId.toString().equals(views.get(i).path("id").asString(null))) {
				return i;
			}
		}
		throw new NoSuchElementException("saved view " + viewId);
	}

	/**
	 * The catalog if the principal may change it (design D3 of us-047-configure-columns). Without
	 * {@code catalog:manage}, only one's own personal catalog can be changed and every other ID is
	 * answered with 403, so that other personal catalogs stay hidden; with it, an unknown ID and
	 * another person's personal catalog are answered with 404. The catalog stays locked until the
	 * transaction ends, so that concurrent changes of the configuration do not overwrite each other.
	 */
	private StoredCatalog writable(OeaPrincipal principal, UUID id) {
		long personId = person(principal);
		boolean manager = this.permissions.permissions(principal).contains(MANAGE_PERMISSION);
		StoredCatalog stored = this.store.findForUpdate(id).orElse(null);
		boolean own = stored != null && "personal".equals(stored.scope()) && stored.createdById() == personId;
		boolean visible = stored != null && ("instance".equals(stored.scope()) || own);
		if (!manager && !own) {
			throw new PermissionDeniedException();
		}
		if (!visible) {
			throw new NoSuchElementException("catalog " + id);
		}
		return stored;
	}

	/** Checks a join in the order of design D2 of us-048-catalog-joins. */
	private Join validate(StoredCatalog stored, JoinInput input, UUID id) {
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > JOIN_NAME_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("name", "The name must have 1 to 100 characters.");
		}
		if (input.connectionMetatypeId() == null || !this.store
			.isConnection(input.connectionMetatypeId(), PackageImporter.INSTANCE_CONFIGURATION)
			.orElse(false)) {
			throw InvalidCatalogException.invalid("connectionMetatypeId", "The entity type is not a connection type.");
		}
		String direction = input.direction();
		if (!"outbound".equals(direction) && !"inbound".equals(direction) && !"both".equals(direction)) {
			throw InvalidCatalogException.invalid("joinDirection", "The direction is outbound, inbound or both.");
		}
		if (input.targetMetatypeId() == null
				|| !this.store.metatypeExists(input.targetMetatypeId(), PackageImporter.INSTANCE_CONFIGURATION)
				|| !reachable(stored.primaryMetatypeId(), input.connectionMetatypeId(), direction,
						input.targetMetatypeId())) {
			throw InvalidCatalogException.invalid("targetMetatypeId",
					"The entity type is not reachable through the connection type in this direction.");
		}
		List<Column> columns = (input.targetColumns() != null) ? input.targetColumns() : List.of();
		if (columns.isEmpty()) {
			throw InvalidCatalogException.invalid("targetColumns", "A join shows at least one column.");
		}
		List<Column> checked = validate(input.targetMetatypeId(), columns, "targetColumns", MAX_TARGET_COLUMNS, false,
				false);
		String mode = input.defaultJoinMode();
		if (mode != null && !"rows".equals(mode) && !"aggregate".equals(mode)) {
			throw InvalidCatalogException.invalid("defaultJoinMode", "The join mode is rows or aggregate.");
		}
		return new Join(id, name, input.connectionMetatypeId(), direction, input.targetMetatypeId(), checked, mode,
				true);
	}

	/**
	 * Whether the target metatype is reachable from the primary one through the connection
	 * metatype in the direction, by its allowed endpoints (BR-03).
	 */
	boolean reachable(UUID primary, UUID connection, String direction, UUID target) {
		List<AllowedConnections.Option> options;
		try {
			options = this.connections.between(primary, target);
		}
		catch (NoSuchElementException ex) {
			return false;
		}
		return options.stream()
			.filter((option) -> option.connection().id().equals(connection))
			.anyMatch((option) -> switch (direction) {
				case "outbound" -> option.direction() != AllowedConnections.Direction.TARGET_TO_SOURCE;
				case "inbound" -> option.direction() != AllowedConnections.Direction.SOURCE_TO_TARGET;
				default -> true;
			});
	}

	private void auditJoin(OeaPrincipal principal, String action, UUID catalogId, Join join) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("joinId", join.id().toString());
		details.put("name", join.name());
		details.put("connectionMetatypeId", join.connectionMetatypeId().toString());
		details.put("joinDirection", join.direction());
		details.put("targetMetatypeId", join.targetMetatypeId().toString());
		this.audit.append(new AuditEvent(action, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog",
				catalogId.toString(), details));
	}

	private static int indexOfJoin(ArrayNode joins, UUID joinId) {
		for (int i = 0; i < joins.size(); i++) {
			if (joinId.toString().equals(joins.get(i).path("id").asString(null))) {
				return i;
			}
		}
		throw new NoSuchElementException("join " + joinId);
	}

	private static ArrayNode array(String json) {
		JsonNode node = (json != null) ? JsonMapper.shared().readTree(json) : null;
		return (node instanceof ArrayNode array) ? array : JsonMapper.shared().createArrayNode();
	}

	private static ObjectNode joinJson(Join join) {
		ObjectNode node = JsonMapper.shared().createObjectNode();
		node.put("id", join.id().toString());
		node.put("name", join.name());
		node.put("connectionMetatypeId", join.connectionMetatypeId().toString());
		node.put("joinDirection", join.direction());
		node.put("targetMetatypeId", join.targetMetatypeId().toString());
		node.set("targetColumns", columnsJson(join.targetColumns()));
		node.put("defaultJoinMode", join.defaultJoinMode());
		return node;
	}

	private static ArrayNode columnsJson(List<Column> columns) {
		ArrayNode json = JsonMapper.shared().createArrayNode();
		for (Column column : columns) {
			ObjectNode node = json.addObject();
			if (column.endpoint() != null) {
				node.put("endpoint", column.endpoint().key());
			}
			if (column.field() != null) {
				node.put("field", column.field().key());
			}
			else {
				node.put("propertyDefId", column.propertyDefId().toString());
			}
			node.put("displayLabel", column.displayLabel());
			node.put("visible", column.visible());
			node.put("sortable", column.sortable());
			node.put("defaultSortDirection", column.defaultSortDirection());
		}
		return json;
	}

	/**
	 * Checks a column list in the order of design D2 of us-047-configure-columns, for the
	 * metatype; {@code at} prefixes the invalid fields. Returns it with trimmed labels. Columns of
	 * the source or target are allowed only if {@code endpoints} (a connection catalog, BR-11).
	 */
	private List<Column> validate(UUID metatypeId, List<Column> columns, String at0, int max, boolean defaultSort,
			boolean endpoints) {
		if (columns.size() > max) {
			throw InvalidCatalogException.invalid(at0, "At most " + max + " columns are allowed.");
		}
		LocalDate today = LocalDate.now(this.clock);
		Set<UUID> available = this.queries.availableProperties(metatypeId, today).keySet();
		Map<Endpoint, Set<UUID>> offered = new EnumMap<>(Endpoint.class);
		Set<String> seen = new HashSet<>();
		List<Column> checked = new ArrayList<>();
		int defaults = 0;
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			String at = at0 + "[" + i + "]";
			if ((column.field() == null) == (column.propertyDefId() == null)) {
				throw InvalidCatalogException.invalid(at, "A column names either a field or a property.");
			}
			if (column.endpoint() != null && !endpoints) {
				throw InvalidCatalogException.invalid(at + ".endpoint",
						"Only a catalog of a connection type shows attributes of source and target.");
			}
			Set<UUID> properties = (column.endpoint() == null) ? available
					: offered.computeIfAbsent(column.endpoint(),
							(side) -> this.queries.endpointProperties(metatypeId, side.key(), today).keySet());
			if (column.propertyDefId() != null && !properties.contains(column.propertyDefId())) {
				throw InvalidCatalogException.invalid(at + ".propertyDefId",
						(column.endpoint() == null) ? "The property is not a public property of the entity type."
								: "The property is not a public property of an allowed " + column.endpoint().key()
										+ " of the connection type.");
			}
			String attribute = ((column.endpoint() != null) ? column.endpoint().key() + "." : "")
					+ ((column.field() != null) ? column.field().key() : column.propertyDefId().toString());
			if (!seen.add(attribute)) {
				throw InvalidCatalogException.invalid(at, "The attribute is listed twice.");
			}
			String label = (column.displayLabel() != null && !column.displayLabel().isBlank())
					? column.displayLabel().strip() : null;
			if (label != null && label.length() > LABEL_MAX_LENGTH) {
				throw InvalidCatalogException.invalid(at + ".displayLabel", "A label has at most 100 characters.");
			}
			String direction = column.defaultSortDirection();
			if (direction != null && !"asc".equals(direction) && !"desc".equals(direction)) {
				throw InvalidCatalogException.invalid(at + ".defaultSortDirection", "The direction is asc or desc.");
			}
			if (direction != null && !defaultSort) {
				throw InvalidCatalogException.invalid(at + ".defaultSortDirection",
						"The default sort belongs to the columns of the catalog.");
			}
			if (direction != null) {
				defaults++;
			}
			checked.add(new Column(column.field(), column.propertyDefId(), label, column.visible(), column.sortable(),
					direction, true, column.endpoint()));
		}
		if (!checked.isEmpty() && checked.stream().noneMatch(Column::visible)) {
			throw InvalidCatalogException.invalid(at0, "At least one column must be visible.");
		}
		if (defaults > 1) {
			throw InvalidCatalogException.invalid(at0, "At most one column has a default sort.");
		}
		return checked;
	}

	private Catalog catalog(StoredCatalog stored) {
		LocalDate today = LocalDate.now(this.clock);
		List<Column> columns = columns(array(stored.columnsJson()), stored.primaryMetatypeId(), today);
		List<Join> joins = new ArrayList<>();
		for (JsonNode node : array(stored.joinsJson())) {
			UUID connection = UUID.fromString(node.path("connectionMetatypeId").asString());
			UUID target = UUID.fromString(node.path("targetMetatypeId").asString());
			String direction = node.path("joinDirection").asString();
			boolean metatypes = this.store.isConnection(connection, PackageImporter.INSTANCE_CONFIGURATION).orElse(false)
					&& this.store.metatypeExists(target, PackageImporter.INSTANCE_CONFIGURATION)
					&& reachable(stored.primaryMetatypeId(), connection, direction, target);
			List<Column> targetColumns = columns((node.get("targetColumns") instanceof ArrayNode array) ? array
					: JsonMapper.shared().createArrayNode(), target, today);
			boolean shown = targetColumns.stream().anyMatch((column) -> column.visible() && column.available());
			joins.add(new Join(UUID.fromString(node.path("id").asString()), node.path("name").asString(), connection,
					direction, target, targetColumns,
					node.hasNonNull("defaultJoinMode") ? node.get("defaultJoinMode").asString() : null,
					metatypes && shown));
		}
		ArrayNode filters = array(stored.savedFiltersJson());
		Set<String> keys = filters.isEmpty() ? Set.of()
				: this.columns.filterable(stored.primaryMetatypeId(), columns, joins).keySet();
		List<SavedFilter> savedFilters = new ArrayList<>();
		for (JsonNode node : filters) {
			List<FilterExpression> expressions = expressions(node.path("expressions"));
			savedFilters.add(new SavedFilter(UUID.fromString(node.path("id").asString()), node.path("name").asString(),
					node.hasNonNull("description") ? node.get("description").asString() : null,
					node.path("logicalOperator").asString("and"), List.copyOf(expressions),
					expressions.stream().allMatch((expression) -> keys.contains(expression.attributePath()))));
		}
		ArrayNode views = array(stored.savedViewsJson());
		CatalogColumns.ViewColumns viewColumns = views.isEmpty() ? null
				: this.columns.viewColumns(stored.primaryMetatypeId(), columns, joins, stored.defaultJoinMode());
		List<SavedView> savedViews = new ArrayList<>();
		for (JsonNode node : views) {
			SavedViewInput view = view(node);
			boolean available;
			try {
				checkState(viewColumns, view);
				available = true;
			}
			catch (InvalidCatalogException ex) {
				available = false;
			}
			savedViews.add(new SavedView(UUID.fromString(node.path("id").asString()), view.name(), view.description(),
					view.columnOrder(), view.filters(), view.filterMode(), view.joinModeOverrides(), view.sort(),
					view.isDefault(), available));
		}
		return new Catalog(stored.id(), stored.name(), stored.description(), stored.primaryMetatypeId(),
				stored.primaryMetatypeName(), stored.defaultJoinMode(), stored.scope(), stored.createdById(),
				stored.createdByName(), stored.createdAt(), stored.lastModifiedAt(), List.copyOf(columns),
				List.copyOf(joins), List.copyOf(savedFilters), List.copyOf(savedViews));
	}

	/** A stored view without ID and availability. */
	private static SavedViewInput view(JsonNode node) {
		List<String> columnOrder = new ArrayList<>();
		node.path("columnOrder").forEach((key) -> columnOrder.add(key.asString()));
		List<JoinModeOverride> overrides = new ArrayList<>();
		node.path("joinModeOverrides")
			.forEach((override) -> overrides.add(new JoinModeOverride(
					UUID.fromString(override.path("joinDefinitionId").asString()), override.path("joinMode").asString())));
		JsonNode sort = node.get("sort");
		return new SavedViewInput(node.path("name").asString(),
				node.hasNonNull("description") ? node.get("description").asString() : null, List.copyOf(columnOrder),
				expressions(node.path("filters")), node.path("filterMode").asString("and"), List.copyOf(overrides),
				(sort != null && sort.isObject())
						? new ViewSort(sort.path("column").asString(), sort.path("direction").asString()) : null,
				node.path("isDefault").asBoolean(false));
	}

	/** Stored filter expressions, with a list value for {@code in} and {@code notIn}. */
	private static List<FilterExpression> expressions(JsonNode json) {
		List<FilterExpression> expressions = new ArrayList<>();
		for (JsonNode expression : json) {
			JsonNode value = expression.get("value");
			Object parsed = null;
			if (value instanceof ArrayNode array) {
				List<String> values = new ArrayList<>();
				array.forEach((item) -> values.add(item.asString()));
				parsed = List.copyOf(values);
			}
			else if (value != null && !value.isNull()) {
				parsed = value.asString();
			}
			expressions.add(new FilterExpression(expression.path("attributePath").asString(),
					expression.path("operator").asString(), parsed));
		}
		return List.copyOf(expressions);
	}

	private static ArrayNode expressionsJson(List<FilterExpression> expressions) {
		ArrayNode json = JsonMapper.shared().createArrayNode();
		for (FilterExpression expression : expressions) {
			ObjectNode item = json.addObject();
			item.put("attributePath", expression.attributePath());
			item.put("operator", expression.operator());
			if (expression.value() instanceof List<?> list) {
				ArrayNode values = item.putArray("value");
				list.forEach((value) -> values.add((String) value));
			}
			else if (expression.value() != null) {
				item.put("value", (String) expression.value());
			}
		}
		return json;
	}

	/** Stored columns with their availability for the metatype. */
	private List<Column> columns(ArrayNode json, UUID metatypeId, LocalDate today) {
		List<Column> columns = new ArrayList<>();
		Map<String, Set<UUID>> available = new HashMap<>();
		for (JsonNode node : json) {
			UUID property = node.hasNonNull("propertyDefId") ? UUID.fromString(node.get("propertyDefId").asString())
					: null;
			Endpoint endpoint = Endpoint.ofKey(node.path("endpoint").asString(null));
			boolean shown = property == null || available
				.computeIfAbsent((endpoint != null) ? endpoint.key() : "",
						(side) -> side.isEmpty() ? this.queries.availableProperties(metatypeId, today).keySet()
								: this.queries.endpointProperties(metatypeId, side, today).keySet())
				.contains(property);
			columns.add(new Column(SystemField.ofKey(node.path("field").asString(null)), property,
					node.hasNonNull("displayLabel") ? node.get("displayLabel").asString() : null,
					node.path("visible").asBoolean(true), node.path("sortable").asBoolean(true),
					node.hasNonNull("defaultSortDirection") ? node.get("defaultSortDirection").asString() : null,
					shown, endpoint));
		}
		return columns;
	}

	/** Catalogs belong to persons; other principals hold no permission for them (ADR-032). */
	private static long person(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		return principal.personId();
	}

}
