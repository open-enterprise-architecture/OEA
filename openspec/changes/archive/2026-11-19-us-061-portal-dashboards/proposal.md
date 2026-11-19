# Proposal

## Why

Dashboards exist in the work area since S16 (`us-055-dashboards`) and have KPI and chart widgets since S17 changes 5 and 6. Readers of the web portal, such as the CIO (SH-05) with the role `portal-reader`, cannot open them: every dashboard read needs `entity:read`, and the portal operation `getPortalDashboardData` is only planned. US-061 asks for a read-only dashboard view in the portal, and US-060 AC5 for the saved layout shown there without editing.

This is S17 change 7 ([sprint plan](../../../docs/sprint-plan.md)). It completes US-061 and US-060, and with them UC-07.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-07](../../../requirements/use-cases/UC-07-create-and-use-dashboard.md) |
| User Stories | [US-061](../../../requirements/user-stories/US-061-show-dashboard-in-web-portal.md), [US-060](../../../requirements/user-stories/US-060-adjust-widget-layout.md) AC5 |
| Requirements | [REQ-055](../../../requirements/req/REQ-055-retrieve-dashboard-data.md), [REQ-056](../../../requirements/req/REQ-056-dashboard-access-visibility.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`portal:read`, L3 object level), [ADR-008](../../../adrs/ADR-008-gui-architecture-dual-track.md) (web portal) |
| Screens | SCR-050 "Portal", SCR-053 "Dashboard view (read-only)" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-19:**

- The portal shows the shared dashboards and, read-only, the caller's own personal dashboards. Only the owner sees a personal dashboard in the portal; administrators (`settings:manage`) do not see the personal dashboards of others there.
- The dashboards appear on the page "Portal" (SCR-050) in a section "Dashboards" next to the catalogs; a click opens the read-only view SCR-053.

**Decisions and assumptions of this change (open for review):**

- **Permission:** the portal operations need `portal:read` only, as the portal catalogs do. Only persons use dashboards; a service account gets 403.
- **Another person's personal dashboard:** answered with 404, like an unknown dashboard, also for administrators (ADR-032 L3, the same answer as in the work area). US-061 AC6 names 403; it is realigned to 404.
- **Widget data:** computed exactly as in the work area, with the reader's permissions and property visibility: text widgets, table widgets with paging, KPI and chart widgets, including the errors `restricted`, `invalid-configuration`, "Catalog not found" and "Saved view not found". A table widget runs the catalog query with the metrics channel `portal`.
- **Configuration:** the portal reads the configuration of a dashboard (widgets, types, titles, positions and settings) through its own operation, always with `canEdit: false`. The planned description "No widget configuration; data points only" is dropped: the client needs the positions and the widget settings (unit, chart type, target) to draw the widgets.
- **Read-only view:** no edit mode, no "Add widget", no drag and drop, no resizing; the widgets sit in their saved grid positions. A "Refresh" button reads the data again; there is no polling (US-061 technical notes).
- **Routing:** a reader with `portal:read` and without `entity:read` who opens `/dashboards` lands on `/portal`, and `/dashboards/{id}` on `/portal/dashboards/{id}`.
- **KPI date (follow-up of change 6):** the date of the previous value is shown as visible text next to "30 days ago" instead of only in a native tooltip, so keyboard and screen reader users reach it. This applies in the work area and the portal.
- **ADR-032:** the sentence "Personal dashboards and saved filters can be created by every principal holding `entity:read` or `portal:read`" no longer matches the implemented rule (personal dashboards need `entity:read`, S16). An amendment of 2026-11-19 records that personal dashboards are created with `entity:read` and are shown in the portal to their owner only.

**Contract:**

- `listPortalDashboards` (`GET /portal/dashboards`), `getPortalDashboard` (`GET /portal/dashboards/{id}`), `getPortalDashboardData` (`GET /portal/dashboards/{id}/data`, from planned to implemented) and `getPortalDashboardWidgetData` (`GET /portal/dashboards/{id}/widgets/{widgetId}/data`), each with `portal:read`.
- The schemas `DashboardSummary`, `DashboardConfig`, `DashboardData` and `WidgetData` are reused.

**Not in this change:**

- creating or editing dashboards in the portal;
- the portal entity detail (SCR-051), so table rows do not link to an entity;
- automatic refresh;
- the other planned portal operations (diagrams, audit events).

## Capabilities

### New Capabilities

- `portal/portal-dashboards`: the dashboards of the web portal – which ones the portal lists, who reads them, their read-only view and the widget data in the portal.

### Modified Capabilities

- `dashboard/widgets`: the KPI shows the date of the previous value as visible text.

## Impact

- **Contract:** four portal operations, the description of the tag `Portal`.
- **Backend:**
  - `app.portal.PortalDashboards` on top of `Dashboards` and `DashboardData` (a portal visibility rule: shared or own);
  - `PortalController`;
  - no migration.
- **Frontend:**
  - the section "Dashboards" on `PortalPage.vue` (SCR-050);
  - the page `PortalDashboardPage.vue` (SCR-053) with the grid and widgets read-only;
  - the router guard;
  - `KpiWidget.vue`;
  - stories, fakes and specs.
- **Documentation:**
  - the manual page "Portal" (en/de) and the KPI part of "Dashboards";
  - SCREENS.md (SCR-050, SCR-053);
  - US-060 and US-061 (done), UC-07, REQ-055, REQ-056;
  - ADR-032 (amendment);
  - the sprint plan.
