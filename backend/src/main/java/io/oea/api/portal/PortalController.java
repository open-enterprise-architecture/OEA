package io.oea.api.portal;

import io.oea.app.entity.EntityBlocks;
import io.oea.api.ContractViolationResponse;
import io.oea.api.catalog.CatalogQueryMapping;
import io.oea.api.dashboard.DashboardMapping;
import io.oea.api.generated.PortalApi;
import io.oea.api.generated.model.CatalogQueryResult;
import io.oea.api.generated.model.CatalogSummary;
import io.oea.api.generated.model.DashboardConfig;
import io.oea.api.generated.model.DashboardSummary;
import io.oea.api.generated.model.WidgetData;
import io.oea.app.dashboard.DashboardData;
import io.oea.app.portal.PortalCatalogs;
import io.oea.app.portal.PortalDashboards;
import io.oea.app.security.OeaPrincipal;
import io.oea.config.security.OeaAuthentication;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shared catalogs of the web portal (SCR-050; design D2 of s9-portal-catalogs). The query
 * answers exactly like the catalog query. The dashboards (US-061; design D1 of
 * us-061-portal-dashboards) are read-only; the other portal operations stay planned.
 */
@RestController
public class PortalController implements PortalApi {

	private final PortalCatalogs catalogs;

	private final PortalDashboards dashboards;

	private final EntityBlocks blocks;

	public PortalController(PortalCatalogs catalogs, PortalDashboards dashboards, EntityBlocks blocks) {
		this.blocks = blocks;
		this.catalogs = catalogs;
		this.dashboards = dashboards;
	}

	@Override
	public ResponseEntity<List<CatalogSummary>> listPortalCatalogs() {
		return ResponseEntity.ok(this.catalogs.list().stream().map(CatalogQueryMapping::summary).toList());
	}

	@Override
	@ContractViolationResponse(title = "Invalid query", detail = "The paging, sort or cursor parameters are invalid.")
	public ResponseEntity<CatalogQueryResult> queryPortalCatalog(UUID id, Integer page, Integer pageSize, String sort,
			String cursor) {
		return ResponseEntity
			.ok(CatalogQueryMapping.result(this.catalogs.query(principal(), id, page, pageSize, sort, cursor),
					this.blocks));
	}

	@Override
	public ResponseEntity<List<DashboardSummary>> listPortalDashboards() {
		return ResponseEntity.ok(this.dashboards.list(principal()).stream().map(DashboardMapping::summary).toList());
	}

	@Override
	public ResponseEntity<DashboardConfig> getPortalDashboard(UUID id) {
		return ResponseEntity.ok(DashboardMapping.config(this.dashboards.get(principal(), id)));
	}

	@Override
	public ResponseEntity<io.oea.api.generated.model.DashboardData> getPortalDashboardData(UUID id) {
		DashboardData.Result result = this.dashboards.data(principal(), id);
		return ResponseEntity.ok(new io.oea.api.generated.model.DashboardData(result.dashboardId(),
				result.widgets().stream().map((widget) -> DashboardMapping.widgetData(widget, this.blocks)).toList(),
				result.generatedAt().atOffset(ZoneOffset.UTC)));
	}

	@Override
	public ResponseEntity<WidgetData> getPortalDashboardWidgetData(UUID id, UUID widgetId, Integer page) {
		return ResponseEntity.ok(DashboardMapping.widgetData(
				this.dashboards.widget(principal(), id, widgetId, (page != null) ? page : 0), this.blocks));
	}

	private static OeaPrincipal principal() {
		return ((OeaAuthentication) SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
	}

}
