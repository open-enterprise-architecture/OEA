# Proposal

## Why

UC-07 asks for dashboards, which give the CIO and other stakeholders a structured view of the architecture. S16 builds the scaffold:

- dashboards with name, description and scope (US-055, US-062);
- the first two widget types, text (US-059) and table (US-058);
- their layout in a 12-column grid (US-060).

KPI and chart widgets with the property aggregation (US-056, US-057) and the read-only portal view (US-061) follow in S17. The contract already has the planned operations `getDashboardConfig` and `getDashboardData` (SCR-024) and the permission `dashboard:manage`, which the role `enterprise-architect` holds.

This is S16 change 3 ([sprint plan](../../../docs/sprint-plan.md), UC-07 1/2). It takes migration V43.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-07](../../../requirements/use-cases/UC-07-create-and-use-dashboard.md) |
| User Story | [US-055](../../../requirements/user-stories/US-055-create-dashboard.md) (AC1–AC4), [US-058](../../../requirements/user-stories/US-058-configure-table-widget.md) (AC1–AC5), [US-059](../../../requirements/user-stories/US-059-add-text-widget.md) (AC1–AC4), [US-060](../../../requirements/user-stories/US-060-adjust-widget-layout.md) (AC1–AC4; AC5 with the portal view in S17), [US-062](../../../requirements/user-stories/US-062-personal-dashboard.md) (AC1–AC5) |
| Requirements | [REQ-051](../../../requirements/req/REQ-051-create-dashboard.md), [REQ-052](../../../requirements/req/REQ-052-configure-widget.md), [REQ-054](../../../requirements/req/REQ-054-catalog-query-datasource.md), [REQ-055](../../../requirements/req/REQ-055-retrieve-dashboard-data.md), [REQ-056](../../../requirements/req/REQ-056-dashboard-access-visibility.md), [REQ-057](../../../requirements/req/REQ-057-widget-grid-layout.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`dashboard:manage`), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md) (licenses of new libraries), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Business object | [dashboard](../../../business-objects/dashboard.md) |
| Sprint / Milestone | S16 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-18:**

- **Editing shared dashboards:** every holder of `dashboard:manage` maintains every shared dashboard, as with catalogs. This covers adding, configuring and arranging widgets, renaming the dashboard and deleting it.
- **Personal dashboards:** administrators may read the personal dashboards of other persons, read-only. Nobody else sees them.

**Decisions and assumptions of this change (open for review):**

- **"Administrators":** the holders of `settings:manage` (admin-class), together with `entity:read`, which every dashboard operation needs; recorded as amendment 2026-11-18 of ADR-032. They see other persons' personal dashboards in the list, marked with the owner, and can open them, but cannot change them. The table data of such a dashboard is computed with the administrator's own visibility.
- **Scope:**
  - `instance` (shared) needs `dashboard:manage` to create.
  - `personal` needs only `entity:read`; only its owner changes it (US-062 AC1).
  - Only persons own and read dashboards; service accounts get 403 on every dashboard operation, as on the catalogs, whose query reads per person.
  - The owner of a personal dashboard who holds `dashboard:manage` may change its scope to `instance` (US-062 AC4, AC5).
  - A shared dashboard does not become personal (422).
  - Another person's personal dashboard answers 404 for everyone but administrators (ADR-032 L3, as `getCatalog`); US-062 AC3 and REQ-056, which say 403, are realigned.
- **Names:**
  - 1–200 characters, unique among the shared dashboards, and among the personal dashboards of one owner, compared case-insensitively after trimming (BR-01, US-055 AC2); a taken name gives 422 `dashboard-name-taken`, as catalogs do (REQ-051's 409 is realigned).
  - The description has at most 1000 characters.
- **Widgets:**
  - The types `text` and `table` are offered in this change; `kpi` and `chart` give 422 "not supported yet" until S17.
  - The title has 1–150 characters.
  - The type cannot change (REQ-052).
  - At most 50 widgets per dashboard.
- **Text widget:**
  - Markdown of at most 10 000 characters; empty is allowed (US-059 AC4).
  - The data is `status=static` (AC1).
  - The client renders it as sanitized HTML: no script, no event handlers, no unknown tags, and links only `http`, `https` and `mailto` (AC2, XSS).
- **Table widget:**
  - `catalogId` is required and must be visible to the writer (403 otherwise, also for an unknown catalog, US-058 AC3).
  - A shared dashboard shows shared catalogs only (422 naming `catalogId`); a personal dashboard with a widget on a personal catalog cannot become shared (422 naming `scope`). Otherwise other readers would only see an error widget.
  - `savedViewId` is optional and must be a view of that catalog (422).
  - `pageSize` is 5–100, default 10.
  - Any other data source gives 422 "Table widget requires CatalogQuery DataSource" (AC5).
  - The data is computed through the catalog query with the reader's permissions and visibility.
  - A deleted or invisible catalog, or a deleted view, gives an error status for that widget only (AC4, REQ-055).
- **Grid:**
  - The columns run from 1 to 12 and the width from 1 to 12, with `col + width ≤ 13` (US-060 AC2).
  - The row is at least 1 and at most 500 (an input limit; REQ-057 leaves it open), and the height runs from 1 to 6 (REQ-057 BR-03).
  - Overlaps are not checked by the server; the client marks them (AC4).
  - A layout change can update several widgets at once.
- **Concurrency:** every change of a dashboard or its widgets needs `If-Match` with the dashboard version (428/412), because several holders of `dashboard:manage` edit the same dashboards.
- **Audit:** `dashboard.created`, `dashboard.updated` (name, description, scope, widgets and layout) and `dashboard.deleted`.
- **Screens:**
  - SCR-024 "Dashboard editor" becomes a page of the web app at `/dashboards/:id`. It has a view mode and an edit mode, the latter for those who may change the dashboard.
  - The new SCR-056 "Dashboards" (free ID, listed under the client app core functions, CA+WP because S17 reuses the list in the portal) lists the dashboards and creates new ones.
  - The read-only portal view SCR-053 follows in S17 with US-061.
- **Deleting a dashboard:** it is removed together with its widgets; no soft delete, because dashboards carry no architecture content.

**Contract (spec-first, `api/openapi.yaml`):**

- **Dashboards:**
  - `listDashboards` (`GET /dashboards`) and `createDashboard` (`POST /dashboards`);
  - `updateDashboard` (`PUT /dashboards/{id}`) and `deleteDashboard` (`DELETE /dashboards/{id}`).
- **Widgets:**
  - `addDashboardWidget` (`POST /dashboards/{id}/widgets`);
  - `updateDashboardWidget` (`PUT /dashboards/{id}/widgets/{widgetId}`) and `removeDashboardWidget` (`DELETE …`);
  - `updateDashboardLayout` (`PUT /dashboards/{id}/layout`).
- **Read operations, marked implemented:**
  - `getDashboardConfig` (`GET /views/dashboards/{id}`) and `getDashboardData` (`GET /views/dashboards/{id}/data`);
  - the new `getDashboardWidgetData` (`GET /views/dashboards/{id}/widgets/{widgetId}/data`), for further table pages.

**Not in this change:**

- KPI and chart widgets, property aggregation, and global filters (S17).
- The portal view and `getPortalDashboardData` (S17).
- Column overrides of the table widget (US-058 technical note) and `additionalFilters` (REQ-054); both are optional.

## Capabilities

### New Capabilities

- `dashboard/dashboards`: dashboards, their scope, visibility, permissions and audit.
- `dashboard/widgets`: text and table widgets, the grid layout, and the widget data.

### Modified Capabilities

None.

## Impact

- **Contract:** new operations and schemas in `api/openapi.yaml`, the planned read operations marked implemented, and the description of `settings:manage` in `x-oea-permissions`.
- **ADR:** amendment 2026-11-18 of ADR-032 (`settings:manage` reads personal dashboards).
- **Migration:** `V43__dashboards.sql`, with `dashboards` and `dashboard_widgets`.
- **Backend:** the `dashboard` feature in `app`, `core` and `api`.
- **Frontend:** the pages SCR-056 and SCR-024, the grid editor, Markdown rendering with sanitizing, the table widget, fakes and stories. It needs new libraries for Markdown and sanitizing, under licenses allowed by ADR-043, and `THIRD-PARTY-LICENSES.md` is regenerated.
- **Documentation:**
  - a manual page "Dashboards" in English and German;
  - `SCREENS.md`;
  - `business-objects/dashboard.md` and `docs/data-model.puml`;
  - the audit catalog;
  - UC-07 and REQ-051 to REQ-057 realigned;
  - the user stories (US-062 AC3: 404 instead of 403);
  - the S16 status in `docs/sprint-plan.md`.
