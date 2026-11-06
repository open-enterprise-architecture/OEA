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
			Instant lastModifiedAt, List<Column> columns, List<Join> joins, List<SavedFilter> savedFilters) {
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
	 * Removes a join, the join mode overrides of saved views that refer to it (US-048 AC6) and the
	 * saved-filter expressions on its columns, with the saved filters left without expressions
	 * (BR-04, design D4 of us-051-catalog-filters); 404 for an unknown join.
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
		for (JsonNode view : views) {
			if (view.get("joinModeOverrides") instanceof ArrayNode overrides) {
				for (int i = overrides.size() - 1; i >= 0; i--) {
					if (joinId.toString().equals(overrides.get(i).path("joinDefinitionId").asString(null))) {
						overrides.remove(i);
					}
				}
			}
		}
		ArrayNode filters = array(stored.savedFiltersJson());
		String prefix = joinId + ".";
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
		ArrayNode json = node.putArray("expressions");
		for (FilterExpression expression : checked) {
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
			List<FilterExpression> expressions = new ArrayList<>();
			for (JsonNode expression : node.path("expressions")) {
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
			savedFilters.add(new SavedFilter(UUID.fromString(node.path("id").asString()), node.path("name").asString(),
					node.hasNonNull("description") ? node.get("description").asString() : null,
					node.path("logicalOperator").asString("and"), List.copyOf(expressions),
					expressions.stream().allMatch((expression) -> keys.contains(expression.attributePath()))));
		}
		return new Catalog(stored.id(), stored.name(), stored.description(), stored.primaryMetatypeId(),
				stored.primaryMetatypeName(), stored.defaultJoinMode(), stored.scope(), stored.createdById(),
				stored.createdByName(), stored.createdAt(), stored.lastModifiedAt(), List.copyOf(columns),
				List.copyOf(joins), List.copyOf(savedFilters));
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
