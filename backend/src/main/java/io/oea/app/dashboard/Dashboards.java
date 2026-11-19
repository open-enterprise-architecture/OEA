package io.oea.app.dashboard;

import io.oea.app.catalog.Catalogs;
import io.oea.app.history.PreconditionRequiredException;
import io.oea.app.history.StaleEntityVersionException;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.dashboard.DashboardStore;
import io.oea.core.dashboard.DashboardStore.StoredDashboard;
import io.oea.core.dashboard.DashboardStore.StoredWidget;
import java.time.Clock;
import java.util.ArrayList;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * Dashboards and their widgets (US-055, US-058 to US-060, US-062; design D3 of us-055-dashboards).
 * A shared dashboard ({@code instance}) is changed with {@code dashboard:manage}, a personal one by
 * its owner only; holders of {@code settings:manage} read personal dashboards of others, read-only
 * (ADR-032 amendment 2026-11-18). Another person's personal dashboard is answered as not existing
 * (L3). Every write locks the dashboard row, checks {@code If-Match} and raises the version.
 */
@Service
public class Dashboards {

	/** Permission for shared dashboards, the contract condition of the writes. */
	public static final String MANAGE_PERMISSION = "dashboard:manage";

	/**
	 * The channel of a read: the work area lets {@code settings:manage} read any dashboard, the
	 * portal admits shared and own personal dashboards only (owner decision 2026-11-19).
	 */
	public enum Reach {

		WORK_AREA, PORTAL

	}

	/** Permission that reads the personal dashboards of other persons. */
	public static final String ADMIN_PERMISSION = "settings:manage";

	static final int NAME_MAX_LENGTH = 200;

	static final int DESCRIPTION_MAX_LENGTH = 1000;

	static final int TITLE_MAX_LENGTH = 150;

	static final int CONTENT_MAX_LENGTH = 10_000;

	static final int UNIT_MAX_LENGTH = 20;

	static final int MAX_WIDGETS = 50;

	static final int MAX_LAYOUT_ITEMS = 50;

	static final int MIN_PAGE_SIZE = 5;

	static final int MAX_PAGE_SIZE = 100;

	static final int DEFAULT_PAGE_SIZE = 10;

	static final int GRID_COLUMNS = 12;

	static final int MAX_ROW = 500;

	static final int MAX_HEIGHT = 6;

	private static final String EXCEEDS_GRID = "Widget exceeds the grid width";

	private static final JsonMapper JSON = JsonMapper.shared();

	private final DashboardStore store;

	private final Catalogs catalogs;

	private final WidgetAggregations aggregations;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	Dashboards(DashboardStore store, Catalogs catalogs, WidgetAggregations aggregations,
			PermissionResolver permissions, AuditWriter audit, Clock clock) {
		this.store = store;
		this.catalogs = catalogs;
		this.aggregations = aggregations;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Visibility of a dashboard. */
	public enum Scope {

		INSTANCE, PERSONAL;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** The type of a widget. */
	public enum WidgetType {

		KPI, CHART, TABLE, TEXT;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** The owner of a dashboard. */
	public record Owner(long id, String displayName) {
	}

	/** A dashboard in the overview. */
	public record Summary(UUID id, String name, String description, String scope, Owner owner, int widgetCount,
			boolean canEdit) {
	}

	/** The position of a widget in the grid. */
	public record Position(int col, int row, int width, int height) {
	}

	/** The grouping of a chart: {@code kind} is {@code property}, {@code metatype} or {@code layer}. */
	public record GroupBy(String kind, UUID propertyDefId) {
	}

	/**
	 * The property aggregation of a KPI or chart widget (REQ-053): {@code function} is
	 * {@code count}, {@code sum}, {@code avg}, {@code min} or {@code max}; {@code groupBy} is set
	 * for a chart.
	 */
	public record Aggregation(UUID metatypeId, UUID propertyDefId, String function, GroupBy groupBy) {
	}

	/**
	 * The configuration of a widget: {@code content} of a text widget; {@code catalogId},
	 * {@code savedViewId} and {@code pageSize} of a table widget; {@code aggregation},
	 * {@code unit}, {@code comparison} and {@code targetValue} of a KPI widget, {@code aggregation},
	 * {@code unit}, {@code chartType}, {@code showLegend} and {@code showDataLabels} of a chart
	 * widget; {@code dataSource} as sent ({@code null} means the default of the type).
	 */
	public record Settings(String content, UUID catalogId, UUID savedViewId, Integer pageSize, String dataSource,
			Aggregation aggregation, String unit, String comparison, Double targetValue, String chartType,
			Boolean showLegend, Boolean showDataLabels) {

		/** The settings of a text or table widget. */
		public Settings(String content, UUID catalogId, UUID savedViewId, Integer pageSize, String dataSource) {
			this(content, catalogId, savedViewId, pageSize, dataSource, null, null, null, null, null, null, null);
		}

	}

	/** Input of a widget. */
	public record WidgetInput(WidgetType type, String title, Position position, Settings config) {
	}

	/** A widget with its stored configuration. */
	public record Widget(UUID id, WidgetType type, String title, Position position, Settings config) {
	}

	/** A dashboard with its widgets, its version and whether the caller may change it. */
	public record Dashboard(UUID id, String name, String description, String scope, Owner owner, int version,
			boolean canEdit, List<Widget> widgets) {
	}

	/** The position of a widget named by its ID. */
	public record WidgetPosition(UUID widgetId, int col, int row, int width, int height) {
	}

	/** Input of a new dashboard. */
	public record NewDashboard(String name, String description, Scope scope) {
	}

	/**
	 * Creates a dashboard without widgets and audits it. Order: scope permission, fields, name (by
	 * the unique index, so concurrent creations cannot both succeed).
	 */
	@Transactional
	public Dashboard create(OeaPrincipal principal, NewDashboard input) {
		long personId = person(principal);
		if (input.scope() == null) {
			throw InvalidDashboardException.invalid("scope", "The scope is required.");
		}
		if (input.scope() == Scope.INSTANCE && !manager(principal)) {
			throw new PermissionDeniedException();
		}
		String name = name(input.name());
		String description = description(input.description());
		UUID id;
		try {
			id = this.store.create(name, description, input.scope().dbValue(), personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			throw nameConflict(ex);
		}
		this.audit.append(event("dashboard.created", principal, id,
				Map.of("dashboardId", id.toString(), "scope", input.scope().dbValue())));
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/**
	 * The shared dashboards and the caller's personal ones; holders of {@code settings:manage}
	 * also see the personal dashboards of others. Ordered by name.
	 */
	@Transactional(readOnly = true)
	public List<Summary> list(OeaPrincipal principal) {
		long personId = person(principal);
		boolean admin = admin(principal);
		boolean manager = manager(principal);
		return this.store.visibleTo(personId, admin)
			.stream()
			.map((stored) -> new Summary(stored.id(), stored.name(), stored.description(), stored.scope(),
					new Owner(stored.ownerId(), stored.ownerName()), stored.widgetCount(),
					canEdit(stored, personId, manager)))
			.toList();
	}

	/** The dashboard with its widgets; one the caller may not see is answered as not existing. */
	@Transactional(readOnly = true)
	public Dashboard get(OeaPrincipal principal, UUID id) {
		return dashboard(principal, visible(principal, id));
	}

	/**
	 * The shared dashboards and the caller's own personal ones, never editable, for the portal;
	 * {@code settings:manage} adds nothing. Ordered by name.
	 */
	@Transactional(readOnly = true)
	public List<Summary> listForPortal(OeaPrincipal principal) {
		long personId = person(principal);
		return this.store.visibleTo(personId, false)
			.stream()
			.map((stored) -> new Summary(stored.id(), stored.name(), stored.description(), stored.scope(),
					new Owner(stored.ownerId(), stored.ownerName()), stored.widgetCount(), false))
			.toList();
	}

	/** The dashboard for the portal: shared or own, never editable. */
	@Transactional(readOnly = true)
	public Dashboard getForPortal(OeaPrincipal principal, UUID id) {
		Dashboard dashboard = dashboard(principal, visible(principal, id, Reach.PORTAL));
		return new Dashboard(dashboard.id(), dashboard.name(), dashboard.description(), dashboard.scope(),
				dashboard.owner(), dashboard.version(), false, dashboard.widgets());
	}

	/**
	 * Changes name, description and scope (US-062 AC4, AC5). A shared dashboard does not become
	 * personal; becoming shared needs {@code dashboard:manage}, a name free among the shared
	 * dashboards and no widget on a personal catalog.
	 */
	@Transactional
	public Dashboard update(OeaPrincipal principal, UUID id, Long expectedVersion, String newName,
			String newDescription, Scope newScope) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		if (newScope == null) {
			throw InvalidDashboardException.invalid("scope", "The scope is required.");
		}
		boolean becomesShared = "personal".equals(stored.scope()) && newScope == Scope.INSTANCE;
		if (becomesShared && !manager(principal)) {
			throw new PermissionDeniedException();
		}
		String name = name(newName);
		String description = description(newDescription);
		if ("instance".equals(stored.scope()) && newScope == Scope.PERSONAL) {
			throw InvalidDashboardException.invalid("scope", "A shared dashboard cannot become personal.");
		}
		if (becomesShared && showsPersonalCatalog(principal, id)) {
			throw InvalidDashboardException.invalid("scope",
					"A dashboard with a widget on a personal catalog cannot become shared.");
		}
		List<String> changed = new ArrayList<>();
		if (!name.equals(stored.name())) {
			changed.add("name");
		}
		if (!description.equals(stored.description())) {
			changed.add("description");
		}
		if (!newScope.dbValue().equals(stored.scope())) {
			changed.add("scope");
		}
		try {
			this.store.update(id, name, description, newScope.dbValue(), person(principal), this.clock.instant());
			// the unique index is checked at the statement, so a taken name is known here
		}
		catch (DuplicateKeyException ex) {
			throw nameConflict(ex);
		}
		updated(principal, id, newScope.dbValue(), changed, List.of());
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/** Deletes the dashboard with its widgets (no soft delete). */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		this.store.delete(id);
		this.audit.append(event("dashboard.deleted", principal, id,
				Map.of("dashboardId", id.toString(), "scope", stored.scope())));
	}

	/** Adds a widget. */
	@Transactional
	public Dashboard addWidget(OeaPrincipal principal, UUID id, Long expectedVersion, WidgetInput input) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		if (stored.widgetCount() >= MAX_WIDGETS) {
			throw InvalidDashboardException.invalid("widgets", "A dashboard has at most 50 widgets.");
		}
		Checked checked = check(principal, stored, input, null);
		UUID widgetId = this.store.addWidget(id, checked.type, checked.title, checked.position.col(),
				checked.position.row(), checked.position.width(), checked.position.height(), checked.configJson,
				person(principal), this.clock.instant());
		this.store.touch(id, person(principal), this.clock.instant());
		updated(principal, id, stored.scope(), List.of(), List.of(widgetChange(widgetId, "added")));
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/** Replaces title, position and configuration of a widget; its type does not change. */
	@Transactional
	public Dashboard updateWidget(OeaPrincipal principal, UUID id, UUID widgetId, Long expectedVersion,
			WidgetInput input) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		StoredWidget widget = widget(id, widgetId);
		Checked checked = check(principal, stored, input, widget);
		this.store.updateWidget(id, widgetId, checked.title, checked.position.col(), checked.position.row(),
				checked.position.width(), checked.position.height(), checked.configJson);
		this.store.touch(id, person(principal), this.clock.instant());
		updated(principal, id, stored.scope(), List.of(), List.of(widgetChange(widgetId, "changed")));
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/** Removes a widget. */
	@Transactional
	public Dashboard removeWidget(OeaPrincipal principal, UUID id, UUID widgetId, Long expectedVersion) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		widget(id, widgetId);
		this.store.removeWidget(id, widgetId);
		this.store.touch(id, person(principal), this.clock.instant());
		updated(principal, id, stored.scope(), List.of(), List.of(widgetChange(widgetId, "removed")));
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/** Sets the positions of several widgets in one change (US-060). */
	@Transactional
	public Dashboard updateLayout(OeaPrincipal principal, UUID id, Long expectedVersion,
			List<WidgetPosition> positions) {
		StoredDashboard stored = writable(principal, id, expectedVersion);
		if (positions == null || positions.isEmpty() || positions.size() > MAX_LAYOUT_ITEMS) {
			throw InvalidDashboardException.invalid("positions", "A layout change has 1 to 50 positions.");
		}
		Set<UUID> known = new HashSet<>();
		this.store.widgets(id).forEach((widget) -> known.add(widget.id()));
		Set<UUID> seen = new HashSet<>();
		List<DashboardStore.Position> checked = new ArrayList<>();
		for (WidgetPosition position : positions) {
			if (position.widgetId() == null || !known.contains(position.widgetId())
					|| !seen.add(position.widgetId())) {
				throw InvalidDashboardException.invalid("widgetId",
						"A position names a widget of another dashboard or a widget twice.");
			}
			Position valid = position(new Position(position.col(), position.row(), position.width(),
					position.height()));
			checked.add(new DashboardStore.Position(position.widgetId(), valid.col(), valid.row(), valid.width(),
					valid.height()));
		}
		this.store.updatePositions(id, checked);
		this.store.touch(id, person(principal), this.clock.instant());
		updated(principal, id, stored.scope(), List.of("layout"), List.of());
		return dashboard(principal, this.store.find(id).orElseThrow());
	}

	/**
	 * The dashboard if the caller may see it: a shared one, the caller's own personal one, or
	 * any with {@code settings:manage}; everything else is answered as not existing (L3).
	 */
	StoredDashboard visible(OeaPrincipal principal, UUID id) {
		return visible(principal, id, Reach.WORK_AREA);
	}

	/** As {@link #visible(OeaPrincipal, UUID)}; the portal ignores {@code settings:manage}. */
	StoredDashboard visible(OeaPrincipal principal, UUID id, Reach reach) {
		long personId = person(principal);
		StoredDashboard stored = this.store.find(id).orElse(null);
		if (stored == null || !("instance".equals(stored.scope()) || stored.ownerId() == personId
				|| reach == Reach.WORK_AREA && admin(principal))) {
			throw new NoSuchElementException("dashboard " + id);
		}
		return stored;
	}

	/** The widgets of a dashboard in the order of the grid. */
	List<StoredWidget> widgetsOf(UUID id) {
		return this.store.widgets(id);
	}

	/**
	 * The dashboard if the caller may change it, locked until the transaction ends, with the
	 * version checked: nothing is known of other persons' personal dashboards (404), a shared
	 * dashboard without {@code dashboard:manage} and a personal one of another person for an
	 * administrator are 403, then {@code If-Match} (428, 412).
	 */
	private StoredDashboard writable(OeaPrincipal principal, UUID id, Long expectedVersion) {
		long personId = person(principal);
		StoredDashboard stored = this.store.findForUpdate(id).orElse(null);
		if (stored == null) {
			throw new NoSuchElementException("dashboard " + id);
		}
		if ("instance".equals(stored.scope())) {
			if (!manager(principal)) {
				throw new PermissionDeniedException();
			}
		}
		else if (stored.ownerId() != personId) {
			if (admin(principal)) {
				throw new PermissionDeniedException();
			}
			throw new NoSuchElementException("dashboard " + id);
		}
		if (expectedVersion == null) {
			throw new PreconditionRequiredException("The write requires If-Match with the version read.");
		}
		if (expectedVersion != stored.version()) {
			throw new StaleEntityVersionException(stored.version());
		}
		return stored;
	}

	private StoredWidget widget(UUID dashboardId, UUID widgetId) {
		return this.store.widgets(dashboardId)
			.stream()
			.filter((widget) -> widget.id().equals(widgetId))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("widget " + widgetId));
	}

	/** A widget input that passed the checks, ready to store. */
	private record Checked(String type, String title, Position position, String configJson) {
	}

	/** Checks a widget; with {@code existing}, the type must stay. */
	private Checked check(OeaPrincipal principal, StoredDashboard dashboard, WidgetInput input, StoredWidget existing) {
		if (input.type() == null) {
			throw InvalidDashboardException.invalid("type", "The type is required.");
		}
		if (existing != null && !existing.type().equals(input.type().dbValue())) {
			throw InvalidDashboardException.invalid("type", "The type of a widget cannot change.");
		}
		String title = (input.title() != null) ? input.title().strip() : "";
		if (title.isEmpty() || title.length() > TITLE_MAX_LENGTH) {
			throw InvalidDashboardException.invalid("title", "The title must have 1 to 150 characters.");
		}
		Position position = position(input.position());
		Settings config = (input.config() != null) ? input.config() : new Settings(null, null, null, null, null);
		ObjectNode json = JSON.createObjectNode();
		if (input.type() == WidgetType.TEXT) {
			String content = (config.content() != null) ? config.content() : "";
			if (content.length() > CONTENT_MAX_LENGTH) {
				throw InvalidDashboardException.invalid("content", "The content must have at most 10000 characters.");
			}
			json.put("content", content);
		}
		else if (input.type() == WidgetType.TABLE) {
			tableConfig(principal, dashboard, config, json);
		}
		else {
			aggregationConfig(input.type(), config, json);
		}
		return new Checked(input.type().dbValue(), title, position, JSON.writeValueAsString(json));
	}

	/** Checks the configuration of a KPI or chart widget (design D2 of us-056-kpi-and-chart-widgets). */
	private void aggregationConfig(WidgetType type, Settings config, ObjectNode json) {
		boolean chart = type == WidgetType.CHART;
		if (config.dataSource() != null && !"property-aggregation".equals(config.dataSource())) {
			throw InvalidDashboardException.invalid("dataSource",
					"A KPI or chart widget requires the property aggregation data source.");
		}
		rejectSet("catalogId", config.catalogId());
		rejectSet("savedViewId", config.savedViewId());
		rejectSet("pageSize", config.pageSize());
		rejectSet("content", config.content());
		if (chart) {
			rejectSet("comparison", config.comparison());
			rejectSet("targetValue", config.targetValue());
			if (config.chartType() == null) {
				throw InvalidDashboardException.invalid("chartType", "A chart widget needs a chart type.");
			}
		}
		else {
			rejectSet("chartType", config.chartType());
			rejectSet("showLegend", config.showLegend());
			rejectSet("showDataLabels", config.showDataLabels());
		}
		if (config.aggregation() == null) {
			throw InvalidDashboardException.invalid("aggregation", "The aggregation is required.");
		}
		this.aggregations.resolve(config.aggregation(), chart);
		String unit = (config.unit() != null) ? config.unit().strip() : "";
		if (unit.length() > UNIT_MAX_LENGTH) {
			throw InvalidDashboardException.invalid("unit", "The unit must have at most 20 characters.");
		}
		json.put("dataSource", "property-aggregation");
		json.set("aggregation", aggregationJson(config.aggregation()));
		if (!unit.isEmpty()) {
			json.put("unit", unit);
		}
		if (chart) {
			json.put("chartType", config.chartType());
			json.put("showLegend", config.showLegend() == null || config.showLegend());
			json.put("showDataLabels", config.showDataLabels() != null && config.showDataLabels());
			return;
		}
		String comparison = (config.comparison() != null) ? config.comparison() : "none";
		if ("vs-target".equals(comparison)) {
			if (config.targetValue() == null || !Double.isFinite(config.targetValue())) {
				throw InvalidDashboardException.invalid("targetValue", "The comparison vs-target needs a target value.");
			}
			json.put("targetValue", config.targetValue());
		}
		json.put("comparison", comparison);
	}

	private static void rejectSet(String field, Object value) {
		if (value != null) {
			throw InvalidDashboardException.invalid(field, "The field " + field + " does not belong to this widget type.");
		}
	}

	private static ObjectNode aggregationJson(Aggregation aggregation) {
		ObjectNode json = JSON.createObjectNode();
		json.put("function", aggregation.function());
		if (aggregation.metatypeId() != null) {
			json.put("metatypeId", aggregation.metatypeId().toString());
		}
		if (aggregation.propertyDefId() != null) {
			json.put("propertyDefId", aggregation.propertyDefId().toString());
		}
		if (aggregation.groupBy() != null) {
			ObjectNode groupBy = json.putObject("groupBy").put("kind", aggregation.groupBy().kind());
			if (aggregation.groupBy().propertyDefId() != null) {
				groupBy.put("propertyDefId", aggregation.groupBy().propertyDefId().toString());
			}
		}
		return json;
	}

	private void tableConfig(OeaPrincipal principal, StoredDashboard dashboard, Settings config, ObjectNode json) {
		if (config.dataSource() != null && !"catalog-query".equals(config.dataSource())) {
			throw InvalidDashboardException.invalid("dataSource", "Table widget requires CatalogQuery DataSource");
		}
		if (config.catalogId() == null) {
			throw InvalidDashboardException.invalid("catalogId", "A table widget needs a catalog.");
		}
		int pageSize = (config.pageSize() != null) ? config.pageSize() : DEFAULT_PAGE_SIZE;
		if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
			throw InvalidDashboardException.invalid("pageSize", "The page size must be 5 to 100.");
		}
		Catalogs.Catalog catalog;
		try {
			catalog = this.catalogs.get(principal, config.catalogId());
		}
		catch (NoSuchElementException ex) {
			// unknown and invisible catalogs are answered alike, so personal catalogs stay hidden (US-058 AC3)
			throw new PermissionDeniedException();
		}
		if ("instance".equals(dashboard.scope()) && !"instance".equals(catalog.scope())) {
			throw InvalidDashboardException.invalid("catalogId", "A shared dashboard can show shared catalogs only.");
		}
		if (config.savedViewId() != null
				&& catalog.savedViews().stream().noneMatch((view) -> view.id().equals(config.savedViewId()))) {
			throw InvalidDashboardException.invalid("savedViewId", "The saved view does not belong to the catalog.");
		}
		json.put("catalogId", config.catalogId().toString());
		if (config.savedViewId() != null) {
			json.put("savedViewId", config.savedViewId().toString());
		}
		json.put("pageSize", pageSize);
	}

	private static Position position(Position position) {
		if (position == null) {
			throw InvalidDashboardException.invalid("position", "The position is required.");
		}
		if (position.col() < 1 || position.col() > GRID_COLUMNS) {
			throw InvalidDashboardException.invalid("col", "The column must be 1 to 12.");
		}
		if (position.width() < 1 || position.width() > GRID_COLUMNS) {
			throw InvalidDashboardException.invalid("width", "The width must be 1 to 12.");
		}
		if (position.col() + position.width() > GRID_COLUMNS + 1) {
			throw InvalidDashboardException.invalid("width", EXCEEDS_GRID);
		}
		if (position.row() < 1 || position.row() > MAX_ROW) {
			throw InvalidDashboardException.invalid("row", "The row must be 1 to 500.");
		}
		if (position.height() < 1 || position.height() > MAX_HEIGHT) {
			throw InvalidDashboardException.invalid("height", "The height must be 1 to 6.");
		}
		return position;
	}

	/** Whether a table widget of the dashboard shows a catalog with scope personal. */
	private boolean showsPersonalCatalog(OeaPrincipal principal, UUID id) {
		for (StoredWidget widget : this.store.widgets(id)) {
			Settings config = settings(widget);
			if (config.catalogId() == null) {
				continue;
			}
			try {
				if ("personal".equals(this.catalogs.get(principal, config.catalogId()).scope())) {
					return true;
				}
			}
			catch (NoSuchElementException ex) {
				// a deleted catalog is shown as an error widget, whatever the scope
			}
		}
		return false;
	}

	private static String name(String raw) {
		String name = (raw != null) ? raw.strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw InvalidDashboardException.invalid("name", "The name must have 1 to 200 characters.");
		}
		return name;
	}

	private static String description(String raw) {
		String description = (raw != null) ? raw : "";
		if (description.length() > DESCRIPTION_MAX_LENGTH) {
			throw InvalidDashboardException.invalid("description", "The description must have at most 1000 characters.");
		}
		return description;
	}

	private static RuntimeException nameConflict(DuplicateKeyException ex) {
		String message = ex.getMessage();
		if (message != null && (message.contains(DashboardStore.INSTANCE_NAME_INDEX)
				|| message.contains(DashboardStore.PERSONAL_NAME_INDEX))) {
			return InvalidDashboardException.nameTaken();
		}
		return ex;
	}

	private Dashboard dashboard(OeaPrincipal principal, StoredDashboard stored) {
		long personId = person(principal);
		List<Widget> widgets = this.store.widgets(stored.id())
			.stream()
			.map((widget) -> new Widget(widget.id(), WidgetType.valueOf(widget.type().toUpperCase(Locale.ROOT)),
					widget.title(), new Position(widget.col(), widget.row(), widget.width(), widget.height()),
					settings(widget)))
			.toList();
		return new Dashboard(stored.id(), stored.name(), stored.description(), stored.scope(),
				new Owner(stored.ownerId(), stored.ownerName()), stored.version(),
				canEdit(stored, personId, manager(principal)), widgets);
	}

	/** The configuration of a stored widget. */
	static Settings settings(StoredWidget widget) {
		JsonNode json = JSON.readTree(widget.configJson());
		return new Settings(text(json, "content"), uuid(json, "catalogId"), uuid(json, "savedViewId"),
				json.hasNonNull("pageSize") ? json.get("pageSize").asInt() : null, text(json, "dataSource"),
				aggregation(json.path("aggregation")), text(json, "unit"), text(json, "comparison"),
				json.hasNonNull("targetValue") ? json.get("targetValue").asDouble() : null, text(json, "chartType"),
				json.hasNonNull("showLegend") ? json.get("showLegend").asBoolean() : null,
				json.hasNonNull("showDataLabels") ? json.get("showDataLabels").asBoolean() : null);
	}

	private static Aggregation aggregation(JsonNode json) {
		if (!json.isObject()) {
			return null;
		}
		JsonNode groupBy = json.path("groupBy");
		return new Aggregation(uuid(json, "metatypeId"), uuid(json, "propertyDefId"), text(json, "function"),
				groupBy.isObject() ? new GroupBy(text(groupBy, "kind"), uuid(groupBy, "propertyDefId")) : null);
	}

	private static String text(JsonNode json, String field) {
		return json.hasNonNull(field) ? json.get(field).asString() : null;
	}

	private static UUID uuid(JsonNode json, String field) {
		return json.hasNonNull(field) ? UUID.fromString(json.get(field).asString()) : null;
	}

	private static boolean canEdit(StoredDashboard stored, long personId, boolean manager) {
		return "instance".equals(stored.scope()) ? manager : stored.ownerId() == personId;
	}

	private void updated(OeaPrincipal principal, UUID id, String scope, List<String> changed,
			List<Map<String, String>> widgets) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("dashboardId", id.toString());
		details.put("scope", scope);
		details.put("changed", List.copyOf(changed));
		if (!widgets.isEmpty()) {
			details.put("widgets", widgets);
		}
		this.audit.append(event("dashboard.updated", principal, id, details));
	}

	private static Map<String, String> widgetChange(UUID widgetId, String action) {
		return Map.of("widgetId", widgetId.toString(), "action", action);
	}

	private static AuditEvent event(String type, OeaPrincipal principal, UUID id, Map<String, Object> details) {
		return new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "dashboard", id.toString(), details);
	}

	private boolean manager(OeaPrincipal principal) {
		return this.permissions.permissions(principal).contains(MANAGE_PERMISSION);
	}

	private boolean admin(OeaPrincipal principal) {
		return this.permissions.permissions(principal).contains(ADMIN_PERMISSION);
	}

	/** Dashboards belong to persons; other principals get no access (ADR-032). */
	static long person(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		return principal.personId();
	}

}
