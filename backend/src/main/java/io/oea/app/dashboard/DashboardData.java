package io.oea.app.dashboard;

import io.oea.app.catalog.CatalogQueries;
import io.oea.app.catalog.InvalidCatalogQueryException;
import io.oea.app.dashboard.Dashboards.Settings;
import io.oea.app.dashboard.Dashboards.WidgetType;
import io.oea.app.dashboard.WidgetAggregations.Function;
import io.oea.app.dashboard.WidgetAggregations.GroupKind;
import io.oea.app.dashboard.WidgetAggregations.Resolved;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.dashboard.DashboardStore.StoredDashboard;
import io.oea.core.dashboard.DashboardStore.StoredWidget;
import io.oea.core.entity.EntityAggregationStore;
import io.oea.core.entity.EntityAggregationStore.GroupRow;
import io.oea.core.entity.EntityAggregationStore.Grouping;
import io.oea.core.metamodel.ArchitectureLayerStore;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyDefinitionStore;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The data of a dashboard, computed on each request with the reader's permissions and visibility
 * (US-058 AC4, REQ-055; design D3 of us-055-dashboards). A text widget is {@code static}; a table
 * widget runs the catalog query on the channel {@link CatalogQueries.Channel#DASHBOARD}. A widget
 * whose catalog or saved view is gone gets an error status without failing the others.
 */
@Service
public class DashboardData {

	private static final String CATALOG_NOT_FOUND = "catalog-not-found";

	private static final String VIEW_NOT_FOUND = "view-not-found";

	private static final String RESTRICTED = "restricted";

	private static final String INVALID_CONFIGURATION = "invalid-configuration";

	/** The groups a chart shows; the rest forms "Other" (design D3 of us-056-kpi-and-chart-widgets). */
	static final int MAX_GROUPS = 50;

	private final Dashboards dashboards;

	private final CatalogQueries queries;

	private final WidgetAggregations aggregations;

	private final EntityAggregationStore aggregationStore;

	private final PropertyAccess propertyAccess;

	private final MetatypeStore metatypes;

	private final ArchitectureLayerStore layers;

	private final PropertyDefinitionStore definitions;

	private final Clock clock;

	DashboardData(Dashboards dashboards, CatalogQueries queries, WidgetAggregations aggregations,
			EntityAggregationStore aggregationStore, PropertyAccess propertyAccess, MetatypeStore metatypes,
			ArchitectureLayerStore layers, PropertyDefinitionStore definitions, Clock clock) {
		this.dashboards = dashboards;
		this.queries = queries;
		this.aggregations = aggregations;
		this.aggregationStore = aggregationStore;
		this.propertyAccess = propertyAccess;
		this.metatypes = metatypes;
		this.layers = layers;
		this.definitions = definitions;
		this.clock = clock;
	}

	/** The status of a widget's data. */
	public enum Status {

		OK, STATIC, ERROR;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** Why a widget has no data. */
	public record Error(String code, String message) {
	}

	/**
	 * One group of a chart: {@code kind} is {@code value}, {@code none}, {@code other},
	 * {@code true} or {@code false}; {@code key}, {@code label} and {@code labelKey} are set for
	 * {@code value} (the label key only for labels of the label bundle).
	 */
	public record Series(String kind, String key, String label, String labelKey, double value) {
	}

	/**
	 * The data of one widget; {@code table} is set for a table widget with status {@code ok},
	 * {@code value} and {@code target} for a KPI widget ({@code value} is {@code null} without
	 * data), {@code series} for a chart widget.
	 */
	public record WidgetResult(UUID widgetId, WidgetType type, Status status, Error error,
			CatalogQueries.Result table, Double value, Double target, List<Series> series) {

		WidgetResult(UUID widgetId, WidgetType type, Status status, Error error, CatalogQueries.Result table) {
			this(widgetId, type, status, error, table, null, null, null);
		}

	}

	/** The data of all widgets of a dashboard. */
	public record Result(UUID dashboardId, Instant generatedAt, List<WidgetResult> widgets) {
	}

	/** The data of all widgets; table widgets give their first page. */
	public Result data(OeaPrincipal principal, UUID id) {
		StoredDashboard stored = this.dashboards.visible(principal, id);
		return new Result(stored.id(), this.clock.instant(),
				this.dashboards.widgetsOf(id).stream().map((widget) -> result(principal, widget, 0)).toList());
	}

	/** The data of one widget, for further pages of a table widget ({@code page} is 0-based). */
	public WidgetResult widget(OeaPrincipal principal, UUID id, UUID widgetId, int page) {
		this.dashboards.visible(principal, id);
		StoredWidget widget = this.dashboards.widgetsOf(id)
			.stream()
			.filter((candidate) -> candidate.id().equals(widgetId))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("widget " + widgetId));
		return result(principal, widget, page);
	}

	private WidgetResult result(OeaPrincipal principal, StoredWidget widget, int page) {
		WidgetType type = WidgetType.valueOf(widget.type().toUpperCase(Locale.ROOT));
		if (type == WidgetType.KPI || type == WidgetType.CHART) {
			return aggregate(principal, widget, type);
		}
		if (type != WidgetType.TABLE) {
			return new WidgetResult(widget.id(), type, Status.STATIC, null, null);
		}
		Settings config = Dashboards.settings(widget);
		try {
			CatalogQueries.Result table = this.queries.query(principal, config.catalogId(),
					new CatalogQueries.Request(page, config.pageSize(), null, null, List.of(), null,
							config.savedViewId(), null),
					CatalogQueries.Channel.DASHBOARD);
			return new WidgetResult(widget.id(), type, Status.OK, null, table);
		}
		catch (NoSuchElementException ex) {
			return new WidgetResult(widget.id(), type, Status.ERROR,
					new Error(CATALOG_NOT_FOUND, "Catalog not found"), null);
		}
		catch (InvalidCatalogQueryException ex) {
			if (ex.getInvalidFields().contains("view")) {
				return new WidgetResult(widget.id(), type, Status.ERROR,
						new Error(VIEW_NOT_FOUND, "Saved view not found"), null);
			}
			throw ex;
		}
	}


	// ---- KPI and chart widgets (design D3 to D5 of us-056-kpi-and-chart-widgets)

	private WidgetResult aggregate(OeaPrincipal principal, StoredWidget widget, WidgetType type) {
		Settings config = Dashboards.settings(widget);
		Resolved resolved;
		try {
			if (config.aggregation() == null) {
				throw InvalidDashboardException.invalid("aggregation", "The aggregation is required.");
			}
			resolved = this.aggregations.resolve(config.aggregation(), type == WidgetType.CHART);
		}
		catch (InvalidDashboardException ex) {
			return failed(widget, type, INVALID_CONFIGURATION, "The configuration no longer fits the metamodel");
		}
		PropertyAccess.Snapshot access = this.propertyAccess.of(principal);
		if (resolved.propertyDefId() != null && !access.readableCompletely(resolved.propertyDefId())
				|| resolved.groupPropertyDefId() != null && !access.readableCompletely(resolved.groupPropertyDefId())) {
			return failed(widget, type, RESTRICTED, "The values are not readable for every entity");
		}
		if (type == WidgetType.KPI) {
			List<GroupRow> rows = this.aggregationStore.aggregate(resolved.metatypeIds(), resolved.propertyDefId(),
					Grouping.none());
			Partial total = rows.isEmpty() ? new Partial(0, null, null, null) : partial(rows.get(0));
			Double target = "vs-target".equals(config.comparison()) ? config.targetValue() : null;
			return new WidgetResult(widget.id(), type, Status.OK, null, null, total.value(resolved.function()),
					target, null);
		}
		return new WidgetResult(widget.id(), type, Status.OK, null, null, null, null, series(resolved));
	}

	private static WidgetResult failed(StoredWidget widget, WidgetType type, String code, String message) {
		return new WidgetResult(widget.id(), type, Status.ERROR, new Error(code, message), null);
	}

	/** The partial aggregates of a group, from which every function and the rest group are derived. */
	private record Partial(long count, Long sum, Long min, Long max) {

		Double value(Function function) {
			return switch (function) {
				case COUNT -> (double) this.count;
				case SUM -> (this.count > 0 && this.sum != null) ? Double.valueOf(this.sum) : null;
				case AVG -> (this.count > 0 && this.sum != null) ? Double.valueOf((double) this.sum / this.count) : null;
				case MIN -> (this.min != null) ? Double.valueOf(this.min) : null;
				case MAX -> (this.max != null) ? Double.valueOf(this.max) : null;
			};
		}

		Partial plus(Partial other) {
			return new Partial(this.count + other.count, add(this.sum, other.sum),
					pick(this.min, other.min, true), pick(this.max, other.max, false));
		}

		private static Long add(Long first, Long second) {
			return (first == null) ? second : (second == null) ? first : Long.valueOf(first + second);
		}

		private static Long pick(Long first, Long second, boolean smaller) {
			if (first == null || second == null) {
				return (first == null) ? second : first;
			}
			return smaller ? Math.min(first, second) : Math.max(first, second);
		}

	}

	private static Partial partial(GroupRow row) {
		return new Partial(row.count(), row.sum(), row.min(), row.max());
	}

	/** A group before ordering: its entry without the value and its partials. */
	private record Group(String kind, String key, String label, String labelKey, Partial partial) {
	}

	private List<Series> series(Resolved resolved) {
		List<Group> groups = switch (resolved.groupKind()) {
			case PROPERTY -> propertyGroups(resolved);
			case METATYPE -> metatypeGroups(resolved);
			case LAYER -> layerGroups(resolved);
			case NONE -> List.of();
		};
		Function function = resolved.function();
		List<Group> ordered = new ArrayList<>(groups.stream().filter((group) -> group.partial().value(function) != null).toList());
		ordered.sort(Comparator.<Group>comparingDouble((group) -> group.partial().value(function))
			.reversed()
			.thenComparing((group) -> "value".equals(group.kind()) ? 0 : 1)
			.thenComparing((group) -> sortLabel(group), String.CASE_INSENSITIVE_ORDER)
			.thenComparing((group) -> sortLabel(group)));
		List<Series> series = new ArrayList<>();
		Partial rest = null;
		for (int i = 0; i < ordered.size(); i++) {
			Group group = ordered.get(i);
			if (i < MAX_GROUPS) {
				series.add(new Series(group.kind(), group.key(), group.label(), group.labelKey(),
						group.partial().value(function)));
			}
			else {
				rest = (rest == null) ? group.partial() : rest.plus(group.partial());
			}
		}
		if (rest != null && rest.value(function) != null) {
			series.add(new Series("other", null, null, null, rest.value(function)));
		}
		return series;
	}

	private static String sortLabel(Group group) {
		return (group.label() != null) ? group.label() : group.kind();
	}

	private List<Group> propertyGroups(Resolved resolved) {
		List<GroupRow> rows = this.aggregationStore.aggregate(resolved.metatypeIds(), resolved.propertyDefId(),
				Grouping.byProperty(resolved.groupPropertyDefId(), resolved.groupColumn()));
		Map<String, UUID> enumIds = new HashMap<>();
		if ("enum".equals(resolved.groupDataType())) {
			this.definitions.enumValues()
				.stream()
				.filter((value) -> value.propertyDefId().equals(resolved.groupPropertyDefId()))
				.forEach((value) -> enumIds.put(value.value(), value.id()));
		}
		List<Group> groups = new ArrayList<>();
		for (GroupRow row : rows) {
			Partial partial = partial(row);
			if (row.key() == null) {
				groups.add(new Group("none", null, null, null, partial));
			}
			else if ("boolean".equals(resolved.groupDataType())) {
				groups.add(new Group(row.key(), null, null, null, partial));
			}
			else if ("enum".equals(resolved.groupDataType())) {
				UUID id = enumIds.get(row.key());
				groups.add(new Group("value", row.key(), row.key(),
						(id != null) ? "enum-value." + id + ".label" : null, partial));
			}
			else if ("reference".equals(resolved.groupDataType())) {
				groups.add(new Group("value", row.key(), row.name(), null, partial));
			}
			else {
				groups.add(new Group("value", row.key(), row.key(), null, partial));
			}
		}
		return groups;
	}

	private List<Group> metatypeGroups(Resolved resolved) {
		Map<UUID, String> names = this.metatypes.names();
		List<Group> groups = new ArrayList<>();
		for (GroupRow row : this.aggregationStore.aggregate(resolved.metatypeIds(), resolved.propertyDefId(),
				Grouping.byMetatype())) {
			groups.add(new Group("value", row.key(), names.get(UUID.fromString(row.key())),
					"metatype." + row.key() + ".name", partial(row)));
		}
		return groups;
	}

	/** The metatype groups folded per layer; metatypes without a layer form "(none)". */
	private List<Group> layerGroups(Resolved resolved) {
		List<GroupRow> rows = this.aggregationStore.aggregate(resolved.metatypeIds(), resolved.propertyDefId(),
				Grouping.byMetatype());
		Map<UUID, UUID> layerOf = this.layers.layerByMetatype(rows.stream().map((row) -> UUID.fromString(row.key())).toList());
		Map<UUID, String> names = new HashMap<>();
		this.layers.list().forEach((layer) -> names.put(layer.id(), layer.name()));
		Map<UUID, Partial> folded = new LinkedHashMap<>();
		Partial none = null;
		for (GroupRow row : rows) {
			UUID layer = layerOf.get(UUID.fromString(row.key()));
			if (layer == null || !names.containsKey(layer)) {
				none = (none == null) ? partial(row) : none.plus(partial(row));
			}
			else {
				folded.merge(layer, partial(row), Partial::plus);
			}
		}
		List<Group> groups = new ArrayList<>();
		folded.forEach((layer, partial) -> groups.add(new Group("value", layer.toString(), names.get(layer),
				"architecture-layer." + layer + ".name", partial)));
		if (none != null) {
			groups.add(new Group("none", null, null, null, none));
		}
		return groups;
	}

}
