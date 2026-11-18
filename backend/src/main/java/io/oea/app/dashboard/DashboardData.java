package io.oea.app.dashboard;

import io.oea.app.catalog.CatalogQueries;
import io.oea.app.catalog.InvalidCatalogQueryException;
import io.oea.app.dashboard.Dashboards.Settings;
import io.oea.app.dashboard.Dashboards.WidgetType;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.dashboard.DashboardStore.StoredDashboard;
import io.oea.core.dashboard.DashboardStore.StoredWidget;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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

	private final Dashboards dashboards;

	private final CatalogQueries queries;

	private final Clock clock;

	DashboardData(Dashboards dashboards, CatalogQueries queries, Clock clock) {
		this.dashboards = dashboards;
		this.queries = queries;
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

	/** The data of one widget; {@code table} is set for a table widget with status {@code ok}. */
	public record WidgetResult(UUID widgetId, WidgetType type, Status status, Error error,
			CatalogQueries.Result table) {
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

}
