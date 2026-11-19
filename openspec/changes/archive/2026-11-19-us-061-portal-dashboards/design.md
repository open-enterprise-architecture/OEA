# Design

## Context

- **Dashboards in the work area (S16, S17 changes 5 and 6):**
  - `Dashboards` holds the dashboards; `DashboardData` computes the widget data in `io.oea.app.dashboard`.
  - The reads `getDashboardConfig`, `getDashboardData` and `getDashboardWidgetData` under `/views/dashboards` need `entity:read`.
  - `Dashboards.visible` lets the caller read a shared dashboard, an own personal one, or any dashboard with `settings:manage`. Everything else is answered as not existing.
  - `Dashboards.person` rejects service accounts.
- **The portal (S9, `s9-portal-catalogs`):**
  - `PortalController` and `app.portal.PortalCatalogs` serve the shared catalogs with `portal:read`.
  - `PortalPage.vue` (SCR-050) lists the catalogs, and `PortalCatalogPage.vue` shows one read-only.
  - The router guard `portalTarget` sends readers without `entity:read` from `/` and `/catalogs` to `/portal`.
- **The contract:** `getPortalDashboardData` is planned, with the note "data points only".
- **The frontend widgets:**
  - `DashboardGrid.vue` draws the grid with its editor.
  - `TextWidget`, `TableWidget`, `KpiWidget` and `ChartWidget` draw the widgets.
  - `DashboardPage.vue` (SCR-024) composes them and loads the data through the work-area operations.

## Goals / Non-Goals

**Goals:**

- Portal readers list and open the shared dashboards and their own personal ones read-only, with the same widget data as in the work area.
- The saved layout is shown without editing (US-060 AC5).
- The date of the KPI previous value is visible text.

**Non-Goals:**

- Editing in the portal.
- The portal entity detail.
- Automatic refresh.
- The other planned portal operations.

## Architecture Constraints

- **ADR-032:**
  - `portal:read` grants shared content and the own personal dashboards (§3).
  - The object-level check (L3) lives in `io.oea.app`, with the same 404 for "not existing" and "not permitted".
  - Property visibility (L4) applies to every read channel, including the portal and the aggregation.
- **ADR-051:** no SQL in `app`. The portal service uses `Dashboards` and `DashboardData`, which use the stores of `core`. The rule `applicationLayerAddsNoSql` must stay green.
- **ADR-013:** the contract first. Every new operation declares `x-oea-authorization` with `portal:read`, and the authorization check script covers it.
- **ADR-031:** SCR-053 becomes a story; the legacy SVG mockups are deleted.

## Decisions

### D1 Four portal operations with the existing schemas

| Operation | Path | Answer |
|---|---|---|
| `listPortalDashboards` | `GET /portal/dashboards` | `DashboardSummary[]`, each `canEdit: false` |
| `getPortalDashboard` | `GET /portal/dashboards/{id}` | `DashboardConfig`, `canEdit: false`, no `ETag` |
| `getPortalDashboardData` | `GET /portal/dashboards/{id}/data` | `DashboardData` (no longer planned) |
| `getPortalDashboardWidgetData` | `GET /portal/dashboards/{id}/widgets/{widgetId}/data?page=` | `WidgetData`; `page` 0-based, minimum 0 |

- Each operation declares `{"access": "permission", "permissions": ["portal:read"]}`, with 401, 403 and 429.
- The three operations with `{id}` also give 400 (an ID or widget ID that is no UUID, a negative `page`) and 404 (unknown dashboard or widget, another person's personal dashboard, also for `settings:manage`).
- `getPortalDashboardData` and `getPortalDashboardWidgetData` also give 503 `query-timeout`: a table widget whose catalog query exceeds the time limit fails the request, as in the work area and in `queryPortalCatalog` (`ApiExceptionHandler` maps `CatalogQueryTimeoutException`).
- The descriptions state that service accounts get 403 (unlike `listPortalCatalogs`, which answers them), and that the `ETag` and `version` of the configuration carry no write meaning in the portal.
- The description of `getPortalDashboardData` loses "No widget configuration; data points only".
- The description of the tag `Portal` names the dashboards as implemented.

**Alternative:** separate portal schemas without settings. They were rejected: the client needs unit, chart type, target and positions to draw the widgets, and the settings carry no data a reader of the dashboard may not see.

### D2 Portal visibility: shared or own

- The new service `io.oea.app.portal.PortalDashboards` delegates to `Dashboards` and `DashboardData` and passes a portal visibility rule.
- The rule admits a dashboard if its scope is `instance`, or if it is personal and the caller owns it. Holders of `settings:manage` get no exception (owner decision 2026-11-19).
- **Structure:**
  - `Dashboards.visible(principal, id)` and `widgetsOf` are package-private today, so `app.portal` cannot call them. The rule becomes an explicit parameter (e.g. an enum `Reach { WORK_AREA, PORTAL }`) of one `visible` method; `PORTAL` ignores `settings:manage`.
  - The portal list uses `DashboardStore.visibleTo(personId, false)` (shared plus own, already ordered by `lower(name), id`); no new store query.
  - `DashboardData.data` and `widget` take the reach as a parameter; the reach also selects the table channel (`DASHBOARD` or `PORTAL`). The public work-area signatures stay as they are.
  - `canEdit` is always `false` in the portal answers, also for `dashboard:manage` and the owner.
- **Order of the checks:** the contract permission `portal:read` (403), then `Dashboards.person` rejects a service account (403) before any lookup, so a service account learns nothing about IDs, then the visibility (404).
- An unknown dashboard, another person's personal dashboard and an unknown widget raise `NoSuchElementException` (404), as in the work area.

### D3 Widget data unchanged, table channel `portal`

- The widget computation is the one of `DashboardData`: catalog query for tables, `EntityAggregationStore` for KPI and chart, `PropertyAccess.Snapshot.readableCompletely` for the `restricted` check, and the error codes as in the work area.
- **Table widgets in the portal:**
  - They run `CatalogQueries.query` with `Channel.PORTAL`, so the metrics separate portal reads. The channel is a parameter of the computation.
  - The catalog visibility is that of the catalog query. On the owner's own personal dashboard, a table on the owner's personal catalog therefore shows its rows. On a shared dashboard, a personal catalog cannot be referenced (S16).
  - The override role audit `property-override-access` applies as in the catalog query.
- A negative `page` is rejected by the contract validation with 400, as in `getDashboardWidgetData`.

### D4 Read-only view SCR-053

- **The page:** `PortalDashboardPage.vue` at `/portal/dashboards/:id`.
  - It loads `getPortalDashboard` and `getPortalDashboardData` in parallel (US-061 technical notes).
  - It draws the grid with the existing widgets.
  - A 404 shows "Dashboard not found" with a link back to the portal.
- **The grid:**
  - `DashboardGrid.vue` already has the prop `editing: boolean`; the portal page passes `editing: false` and binds no edit events. No new mode is needed.
  - With `editing: false` there is no drag handle, resize handle, arrangement button, keyboard move or overlap marker. Positions are as saved.
- **Further table pages:** `TableWidget.vue` calls the work-area `fetchWidgetData` itself. It gets the page loader as a prop (or a `portal` flag), so the portal reads further pages through `getPortalDashboardWidgetData`. Table rows do not link to an entity (they do not today either): the portal has no entity detail yet.
- **Text widgets:** `TextWidget.vue` is reused unchanged; Markdown stays rendered with markdown-it and sanitized with DOMPurify (US-061 technical notes: no XSS).
- **The header:** the dashboard name, "Personal" for a personal dashboard, the time of the data (`generatedAt`) and the button "Refresh". The button reloads the data, not the configuration.
- **SCR-050:** a section "Dashboards" below or next to "Catalogs".
  - It lists the portal dashboards with name, description and number of widgets; personal ones carry the badge "Personal".
  - An empty list shows "No dashboards yet."
  - The catalogs and the dashboards load independently: a failure of one list shows its error in its own section and does not hide the other.
- **The router guard:** `portalTarget` sends a reader with `portal:read` and without `entity:read` from `/dashboards` to `/portal`, and from `/dashboards/{id}` to `/portal/dashboards/{id}`.
- **Accessibility:** the page has the heading of the dashboard, each widget has its title as heading, and the stories run the a11y check.

### D5 KPI date as text

- `KpiWidget.vue` shows the date of `previousAt` as visible text next to "30 days ago", for example "30 days ago (20 Oct 2026): 12", formatted with the locale's medium date.
- The native `title` tooltip is removed.
- The change applies to the work area and the portal.

### D6 Documentation

- **The manual (en/de):**
  - "Portal": the section "Dashboards" – opening, the read-only view, "Refresh", the personal ones of the owner;
  - "Dashboards": the visible date of the KPI comparison.
- **SCREENS.md:** SCR-053 becomes `implemented`, with a story and generated images; SCR-050 names the section "Dashboards".
- **Requirements:**
  - US-061 and US-060 become done, with implementation notes; US-061 AC6 is realigned to 404;
  - UC-07, REQ-055 and REQ-056 are realigned (portal visibility: shared and own);
  - ADR-032 gets an amendment of 2026-11-19, see the proposal; it also corrects the sentence on creating personal dashboards (§3) and the L3 row "`portal:read` only for instance-scope objects" (plus the own personal dashboards);
  - the traceability matrix is regenerated.
- **The sprint plan:** the status of S17 change 7.

## Data Model

No change. No migration.

## API

The delta to `api/openapi.yaml` is D1. The four operations are under the tag `Portal`; `getPortalDashboardData` loses `x-oea-status: planned`.

## Risks / Trade-offs

- **[Risk]** A second visibility rule next to `Dashboards.visible` could drift.
  - **Mitigation:** one method with the rule as an explicit parameter (work area or portal), tested on both sides: the administrator reads another person's personal dashboard in the work area and gets 404 in the portal.
- **[Risk]** The portal reader computes aggregates without `entity:read`.
  - **Mitigation:** this is the scope of `portal:read` (ADR-032: "including the entity data projected by them"). Property visibility applies unchanged, and the aggregation reveals no single entity.
- **[Trade-off]** A table widget can reference a catalog whose own portal query would answer 404 (the owner's personal catalog on the owner's personal dashboard). Only the owner sees that dashboard in the portal, and the owner reads that catalog in the work area anyway.
