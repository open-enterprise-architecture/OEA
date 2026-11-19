package io.oea.api.dashboard;

import io.oea.api.ContractViolationResponse;
import io.oea.api.generated.DashboardsApi;
import io.oea.api.generated.ViewsEditorsApi;
import io.oea.api.generated.model.DashboardConfig;
import io.oea.api.generated.model.DashboardCreate;
import io.oea.api.generated.model.DashboardSummary;
import io.oea.api.generated.model.DashboardUpdate;
import io.oea.api.generated.model.GridPosition;
import io.oea.api.generated.model.LayoutUpdate;
import io.oea.api.generated.model.WidgetAggregation;
import io.oea.api.generated.model.WidgetData;
import io.oea.api.generated.model.WidgetGroupBy;
import io.oea.api.generated.model.WidgetSettings;
import io.oea.api.generated.model.WidgetType;
import io.oea.api.generated.model.WidgetWrite;
import io.oea.app.dashboard.DashboardData;
import io.oea.app.dashboard.Dashboards;
import io.oea.app.entity.EntityBlocks;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.ConditionsEnforcedByHandler;
import io.oea.config.security.OeaAuthentication;
import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * Dashboards and their widgets (US-055, US-058 to US-060, US-062) and the reads of the dashboard
 * page (design D2 and D3 of us-055-dashboards). Implements {@link ViewsEditorsApi} for the reads;
 * its other operations stay planned.
 */
@RestController
public class DashboardController implements DashboardsApi, ViewsEditorsApi {

	/** Path of the dashboard collection. */
	public static final String DASHBOARDS_PATH = "/api/v1/dashboards";

	private static final Pattern STRONG_VERSION = Pattern.compile("\"(\\d{1,9})\"");

	/** A widget body the probe of the handler-enforced condition sends. */
	private static final String PROBE_WIDGET = """
			{"type": "text", "title": "Probe", "position": {"col": 1, "row": 1, "width": 3, "height": 1},
			 "config": {"content": "Probe"}}""";

	private static final String PROBE_DASHBOARD = "/api/v1/dashboards/00000000-0000-4000-8000-0000000d0001";

	private final Dashboards dashboards;

	private final DashboardData data;

	private final EntityBlocks blocks;

	public DashboardController(Dashboards dashboards, DashboardData data, EntityBlocks blocks) {
		this.dashboards = dashboards;
		this.data = data;
		this.blocks = blocks;
	}

	/** Both interfaces declare this default; no example responses are rendered. */
	@Override
	public Optional<NativeWebRequest> getRequest() {
		return Optional.empty();
	}

	@Override
	public ResponseEntity<List<DashboardSummary>> listDashboards() {
		return ResponseEntity.ok(this.dashboards.list(principal())
			.stream()
			.map(DashboardMapping::summary)
			.toList());
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid dashboard", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Dashboards.create requires dashboard:manage for scope=instance",
			probeRole = "viewer", probeBody = """
					{"name": "Probe", "scope": "instance"}""")
	public ResponseEntity<DashboardConfig> createDashboard(DashboardCreate request) {
		Dashboards.Dashboard created = this.dashboards.create(principal(), new Dashboards.NewDashboard(
				request.getName(), request.getDescription(), Dashboards.Scope.valueOf(request.getScope().name())));
		return ResponseEntity.created(URI.create("/api/v1/views/dashboards/" + created.id()))
			.eTag(etag(created.version()))
			.body(DashboardMapping.config(created));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid dashboard", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Dashboards.update requires dashboard:manage for a shared dashboard and "
			+ "for scope=instance; a personal dashboard is changed by its owner only", probeRole = "viewer",
			probeBody = """
					{"name": "Probe", "scope": "instance"}""", probePath = PROBE_DASHBOARD)
	public ResponseEntity<DashboardConfig> updateDashboard(UUID id, String ifMatch, DashboardUpdate request) {
		return versioned(this.dashboards.update(principal(), id, expectedVersion(ifMatch), request.getName(),
				request.getDescription(), Dashboards.Scope.valueOf(request.getScope().name())));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "Dashboards.delete requires dashboard:manage for a shared dashboard; "
			+ "a personal dashboard is deleted by its owner only", probeRole = "viewer", probeBody = "{}",
			probePath = PROBE_DASHBOARD)
	public ResponseEntity<Void> deleteDashboard(UUID id, String ifMatch) {
		this.dashboards.delete(principal(), id, expectedVersion(ifMatch));
		return ResponseEntity.noContent().build();
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid widget", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Dashboards.addWidget requires dashboard:manage for a shared dashboard; "
			+ "a personal dashboard is changed by its owner only", probeRole = "viewer", probeBody = PROBE_WIDGET,
			probePath = PROBE_DASHBOARD + "/widgets")
	public ResponseEntity<DashboardConfig> addDashboardWidget(UUID id, String ifMatch, WidgetWrite request) {
		return versioned(this.dashboards.addWidget(principal(), id, expectedVersion(ifMatch), widget(request)));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid widget", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Dashboards.updateWidget requires dashboard:manage for a shared "
			+ "dashboard; a personal dashboard is changed by its owner only", probeRole = "viewer",
			probeBody = PROBE_WIDGET,
			probePath = PROBE_DASHBOARD + "/widgets/00000000-0000-4000-8000-0000000d0002")
	public ResponseEntity<DashboardConfig> updateDashboardWidget(UUID id, UUID widgetId, String ifMatch,
			WidgetWrite request) {
		return versioned(
				this.dashboards.updateWidget(principal(), id, widgetId, expectedVersion(ifMatch), widget(request)));
	}

	@Override
	@ConditionsEnforcedByHandler(value = "Dashboards.removeWidget requires dashboard:manage for a shared "
			+ "dashboard; a personal dashboard is changed by its owner only", probeRole = "viewer",
			probeBody = "{}", probePath = PROBE_DASHBOARD + "/widgets/00000000-0000-4000-8000-0000000d0002")
	public ResponseEntity<DashboardConfig> removeDashboardWidget(UUID id, UUID widgetId, String ifMatch) {
		return versioned(this.dashboards.removeWidget(principal(), id, widgetId, expectedVersion(ifMatch)));
	}

	@Override
	@ContractViolationResponse(status = 422, title = "Invalid layout", detail = "Some fields are invalid.")
	@ConditionsEnforcedByHandler(value = "Dashboards.updateLayout requires dashboard:manage for a shared "
			+ "dashboard; a personal dashboard is changed by its owner only", probeRole = "viewer", probeBody = """
					{"positions": [{"widgetId": "00000000-0000-4000-8000-0000000d0002", "col": 1, "row": 1,
					 "width": 3, "height": 1}]}""", probePath = PROBE_DASHBOARD + "/layout")
	public ResponseEntity<DashboardConfig> updateDashboardLayout(UUID id, String ifMatch, LayoutUpdate request) {
		return versioned(this.dashboards.updateLayout(principal(), id, expectedVersion(ifMatch),
				request.getPositions()
					.stream()
					.map((position) -> new Dashboards.WidgetPosition(position.getWidgetId(), position.getCol(),
							position.getRow(), position.getWidth(), position.getHeight()))
					.toList()));
	}

	@Override
	public ResponseEntity<DashboardConfig> getDashboardConfig(UUID id) {
		return versioned(this.dashboards.get(principal(), id));
	}

	@Override
	public ResponseEntity<io.oea.api.generated.model.DashboardData> getDashboardData(UUID id) {
		DashboardData.Result result = this.data.data(principal(), id);
		return ResponseEntity.ok(new io.oea.api.generated.model.DashboardData(result.dashboardId(),
				result.widgets().stream().map((widget) -> DashboardMapping.widgetData(widget, this.blocks)).toList(), result.generatedAt().atOffset(ZoneOffset.UTC)));
	}

	@Override
	public ResponseEntity<WidgetData> getDashboardWidgetData(UUID id, UUID widgetId, Integer page) {
		return ResponseEntity.ok(DashboardMapping.widgetData(this.data.widget(principal(), id, widgetId, (page != null) ? page : 0), this.blocks));
	}

	private static Dashboards.WidgetInput widget(WidgetWrite request) {
		GridPosition position = request.getPosition();
		WidgetSettings config = request.getConfig();
		return new Dashboards.WidgetInput(Dashboards.WidgetType.valueOf(request.getType().name()),
				request.getTitle(),
				new Dashboards.Position(position.getCol(), position.getRow(), position.getWidth(),
						position.getHeight()),
				(config != null) ? new Dashboards.Settings(config.getContent(), config.getCatalogId(),
						config.getSavedViewId(), config.getPageSize(),
						(config.getDataSource() != null) ? config.getDataSource().getValue() : null,
						aggregation(config.getAggregation()), config.getUnit(),
						(config.getComparison() != null) ? config.getComparison().getValue() : null,
						config.getTargetValue(),
						(config.getChartType() != null) ? config.getChartType().getValue() : null,
						config.getShowLegend(), config.getShowDataLabels()) : null);
	}

	private static Dashboards.Aggregation aggregation(WidgetAggregation aggregation) {
		if (aggregation == null) {
			return null;
		}
		WidgetGroupBy groupBy = aggregation.getGroupBy();
		return new Dashboards.Aggregation(aggregation.getMetatypeId(), aggregation.getPropertyDefId(),
				aggregation.getFunction().getValue(),
				(groupBy != null) ? new Dashboards.GroupBy(groupBy.getKind().getValue(), groupBy.getPropertyDefId())
						: null);
	}

	private static ResponseEntity<DashboardConfig> versioned(Dashboards.Dashboard dashboard) {
		return ResponseEntity.ok().eTag(etag(dashboard.version())).body(DashboardMapping.config(dashboard));
	}

	private static String etag(int version) {
		return "\"" + version + "\"";
	}

	/**
	 * The version named by {@code If-Match}: {@code null} without one (428); {@code -1} for a
	 * value that is not a strong ETag of a version, which matches no version (412).
	 */
	static Long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			return null;
		}
		Matcher matcher = STRONG_VERSION.matcher(ifMatch.strip());
		return matcher.matches() ? Long.parseLong(matcher.group(1)) : -1L;
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
