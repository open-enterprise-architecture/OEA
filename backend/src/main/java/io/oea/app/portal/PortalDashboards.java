package io.oea.app.portal;

import io.oea.app.dashboard.DashboardData;
import io.oea.app.dashboard.Dashboards;
import io.oea.app.security.OeaPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The dashboards of the web portal (design D2 and D3 of us-061-portal-dashboards): the shared
 * dashboards and the caller's own personal ones, read-only. It delegates to {@link Dashboards}
 * and {@link DashboardData} with the portal reach, which admits no personal dashboard of another
 * person, also not for holders of {@code settings:manage}, and measures table widgets with the
 * channel {@code portal}. A service account is rejected before any lookup.
 */
@Service
public class PortalDashboards {

	private final Dashboards dashboards;

	private final DashboardData data;

	PortalDashboards(Dashboards dashboards, DashboardData data) {
		this.dashboards = dashboards;
		this.data = data;
	}

	/** The shared and the own personal dashboards, ordered by name, never editable. */
	public List<Dashboards.Summary> list(OeaPrincipal principal) {
		return this.dashboards.listForPortal(principal);
	}

	/** The configuration; another person's personal dashboard is answered as not existing. */
	public Dashboards.Dashboard get(OeaPrincipal principal, UUID id) {
		return this.dashboards.getForPortal(principal, id);
	}

	/** The data of all widgets; table widgets give their first page. */
	public DashboardData.Result data(OeaPrincipal principal, UUID id) {
		return this.data.data(principal, id, Dashboards.Reach.PORTAL);
	}

	/** The data of one widget, for further pages of a table widget ({@code page} is 0-based). */
	public DashboardData.WidgetResult widget(OeaPrincipal principal, UUID id, UUID widgetId, int page) {
		return this.data.widget(principal, id, widgetId, page, Dashboards.Reach.PORTAL);
	}

}
