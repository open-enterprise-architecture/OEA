# US-061: Open a Dashboard in the Web Portal as a Viewer

**ID**: US-061
**Story Points**: 5
**Status**: done
**Sprint**: S17
**Implementation**: OpenSpec change `us-061-portal-dashboards` – the section "Dashboards" on the page "Portal" (SCR-050) and the read-only view SCR-053 with the widgets in their saved grid positions and the button "Refresh"; the operations `listPortalDashboards`, `getPortalDashboard`, `getPortalDashboardData` and `getPortalDashboardWidgetData` with `portal:read`; the portal shows the shared dashboards and the own personal ones, another person's personal dashboard is answered with 404 (owner decisions of 2026-11-19)

## Story

As a CIO, I want to open a dashboard in the web portal in the browser and see the calculated metrics, charts and tables – so that I have a current overview of the IT portfolio at any time without installing the client app.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: CIO (SH-05); also all other stakeholders (SH-01–SH-07) as viewers
**Requirements**: [REQ-055](../req/REQ-055-retrieve-dashboard-data.md), [REQ-056](../req/REQ-056-dashboard-access-visibility.md)

## Acceptance Criteria

**AC1** (open dashboard):

- Given: Dashboard "IT Investment Planning" (scope=instance) with 3 widgets (KPI, bar chart, pie chart)
- When: CIO opens the web portal and clicks the dashboard
- Then: All 3 widgets render with live data; KPI shows number + unit; charts show correct bars/segments

**AC2** (missing widget not blocking):

- Given: A widget has an invalid DataSource (e.g. property removed after a metamodel change)
- When: CIO opens the dashboard
- Then: Failing widget shows an error notice in its grid position; other widgets render normally

**AC3** (tooltip on chart):

- When: CIO hovers the mouse over a bar in the bar chart
- Then: Tooltip shows group and value (e.g. "Plateau 2027: 30,500,000 CHF")

**AC4** (pagination in table widget):

- Given: Table widget shows catalog with 47 entries and pageSize=10
- When: CIO clicks on page 2
- Then: Table shows entries 11–20; `totalCount` and current page visible

**AC5** (no edit mode):

- When: CIO opens the dashboard
- Then: No "Edit" or "Add widget" buttons visible (viewer view only)

**AC6** (scope=personal not retrievable):

- Given: Franz has a personal dashboard
- When: CIO tries to open Franz's dashboard URL directly
- Then: HTTP 404, as for a dashboard that does not exist; no dashboard content visible (realigned from 403 in the change `us-061-portal-dashboards`: ADR-032 L3 answers "not permitted" like "not existing"; administrators with `settings:manage` do not see personal dashboards of others in the portal either)

## Technical Notes

- Web portal: Vue 3 SPA (ADR-008, ADR-011); no Vue Flow/canvas needed; standard chart library (e.g. Chart.js, Apache ECharts)
- Initial load: `GET /api/v1/portal/dashboards/{id}` (configuration) + `GET /api/v1/portal/dashboards/{id}/data` (live data) in parallel
- Refresh: v1.0 manual (page reload or refresh button); no polling
- Markdown in text widgets: sanitized HTML; no XSS

## Definition of Done

- [ ] AC1–AC6 met
- [ ] Tests: full rendering, error isolation, tooltip, pagination, no edit mode, 404 for others' personal
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
