# Design

## Context

- **Contract:** the permission `dashboard:manage` is in the catalog (V3) and held by `enterprise-architect`. The contract has the planned `getDashboardConfig`, `getDashboardData` (tag `Views – Editors`) and `getPortalDashboardData`, with the schemas `DashboardConfig`, `DashboardData` and `WidgetData`.
- **Catalogs:** they have a scope (`instance`, `personal`) that never changes, and saved views. `core/catalog/CatalogStore.findVisible(id, personId)` applies the `VISIBLE` rule (`scope = 'instance' OR created_by = ?`); `app/catalog/Catalogs.get(principal, id)` answers an invisible catalog with `NoSuchElementException` and a non-person caller with `PermissionDeniedException`. `app/catalog/CatalogQueries.query(principal, catalogId, Request, Channel)` applies the reader's visibility and the property redaction (L4); `Request.view` must be an available saved view (otherwise `InvalidCatalogQueryException` naming `view`), `page` is 0-based and `pageSize` at most 200. There is no `deleteCatalog` operation yet.
- **Contract conventions:** catalog writes declare `entity:read` with a `conditions` entry for `catalog:manage`; `restoreEntity` and `setEntityParent` use `IfMatchRequiredHeader`, `PreconditionFailed` (412 `stale-version`), `PreconditionRequired` (428) and the `VersionETag` header.
- **Business object:** `business-objects/dashboard.md` describes dashboard, widget, data source and grid position.

## Goals / Non-Goals

**Goals:** as in `specs/dashboard/dashboards` and `specs/dashboard/widgets`.

**Non-goals:**

- KPI and chart widgets, property aggregation and global filters;
- the portal view;
- column overrides and `additionalFilters` of the table widget.

## Decisions

### D1 – Schema (`V43__dashboards.sql`)

```sql
dashboards (id UUID PK, name TEXT 1–200 (trimmed), description TEXT ≤ 1000 DEFAULT '',
            scope TEXT CHECK IN ('instance','personal'), owner_id BIGINT REFERENCES persons,
            version INTEGER ≥ 1, created_at, created_by, updated_at, updated_by)
UNIQUE INDEX dashboards_instance_name ON dashboards (lower(btrim(name))) WHERE scope = 'instance'
UNIQUE INDEX dashboards_personal_name ON dashboards (owner_id, lower(btrim(name))) WHERE scope = 'personal'

dashboard_widgets (id UUID PK, dashboard_id UUID REFERENCES dashboards ON DELETE CASCADE,
                   type TEXT CHECK IN ('kpi','chart','table','text'), title TEXT 1–150,
                   col INT 1–12, width INT 1–12, CHECK (col + width <= 13),
                   grid_row INT 1–500, height INT 1–6,
                   config JSONB NOT NULL, created_at, created_by)
```

- **`config`:** for `text`, `{content}`; for `table`, `{catalogId, savedViewId?, pageSize}`. It is validated in the service; the database only checks the column constraints.
- **Grants:** `oea_app` gets `SELECT, INSERT, UPDATE, DELETE`, because dashboards are deleted for real.

### D2 – Contract (`api/openapi.yaml`, tag `Dashboards` for the writes, `Views – Editors` for the reads)

**Operations:**

| Operation | Path | Authorization |
|---|---|---|
| `listDashboards` | `GET /dashboards` | `entity:read` |
| `createDashboard` | `POST /dashboards` | `entity:read`; scope `instance` also needs `dashboard:manage` |
| `updateDashboard` | `PUT /dashboards/{id}` | `entity:read`, plus the change rules of the spec; `If-Match` |
| `deleteDashboard` | `DELETE /dashboards/{id}` | as `updateDashboard`; `If-Match` |
| `addDashboardWidget` | `POST /dashboards/{id}/widgets` | as `updateDashboard`; `If-Match` |
| `updateDashboardWidget` | `PUT /dashboards/{id}/widgets/{widgetId}` | `If-Match` |
| `removeDashboardWidget` | `DELETE /dashboards/{id}/widgets/{widgetId}` | `If-Match` |
| `updateDashboardLayout` | `PUT /dashboards/{id}/layout` | body `{positions: [{widgetId, col, row, width, height}]}`, 1–50 items; `If-Match` |
| `getDashboardConfig` | `GET /views/dashboards/{id}` | `entity:read` |
| `getDashboardData` | `GET /views/dashboards/{id}/data` | `entity:read` |
| `getDashboardWidgetData` | `GET /views/dashboards/{id}/widgets/{widgetId}/data?page=` | `entity:read` |

- `getDashboardConfig` and `getDashboardData` lose `x-oea-status: planned`.
- The writes return the dashboard with its widgets and the new `version`, also in an `ETag` header.
- `x-oea-authorization` declares `entity:read` for all of them, because personal dashboards need no more, and states the service rules as `conditions` (ADR-032 §8), as the catalog writes do:
  - `createDashboard`: `{"when": "body field scope is 'instance'", "permissions": ["dashboard:manage"]}`;
  - `updateDashboard`: `{"when": "the dashboard has scope 'instance', or body field scope changes it to 'instance' (a personal dashboard is changeable by its owner only)", "permissions": ["dashboard:manage"]}`; the other writes the same without the scope change;
  - `listDashboards` and the three reads: `{"when": "another person's personal dashboard (read-only)", "permissions": ["settings:manage"]}`.
- The description of `settings:manage` in `x-oea-permissions` gets "read the personal dashboards of other persons, read-only" (ADR-032 amendment 2026-11-18).
- Writes take `IfMatchRequiredHeader` and list `412` (`PreconditionFailed`), `428` (`PreconditionRequired`), `401`, `403`, `404`, `413`, `422` and `429`; `getDashboardConfig` and the writes return the `ETag` (`VersionETag`).
- `getPortalDashboardData` stays planned and refers to the reshaped `DashboardData`.

**Schemas:**

- `DashboardConfig` (reshaped, also returned by the writes) `{id, name, description, scope, owner: {id, displayName}, version, canEdit, widgets: [WidgetConfig]}`;
- `DashboardSummary {id, name, description, scope, owner, widgetCount, canEdit}`;
- `WidgetConfig` (reshaped) `{id, type, title, position: GridPosition, config}`, a discriminated union by `type`: `TextWidgetConfig {content}` or `TableWidgetConfig {catalogId, savedViewId?, pageSize}`; `type` lists `kpi`, `chart`, `table` and `text`;
- `WidgetData {widgetId, type, status: ok | static | error, error?: {code: catalog-not-found | view-not-found, message}, table?: CatalogQueryResult}`; the table reuses the catalog query result, so redacted values look as in the catalog;
- `DashboardData {dashboardId, generatedAt, widgets: [WidgetData]}`.

The existing `DashboardConfig`, `WidgetConfig`, `WidgetData` and `DashboardData` were never implemented and are reshaped in place. `getDashboardWidgetData` takes `page` (0-based, as `queryCatalog`).

**Problems:**

- 422 `validation-failed` with `invalidFields`; a taken name gives 422 `dashboard-name-taken` naming `name`, detail "A dashboard with this name already exists";
- 403 for missing permission or ownership;
- 404 for unknown or invisible dashboards and widgets;
- 412 and 428 for `If-Match`.

### D3 – Backend (`core/dashboard/DashboardStore`, `app/dashboard/Dashboards`, `app/dashboard/DashboardData`, `api/dashboard/DashboardController`)

- **Store:** all SQL lives in `core/dashboard` (ADR-051).
- **`Dashboards`:**
  - **Visibility:** a reader sees `scope='instance'`, or `owner_id = caller`, or anything with `settings:manage`; an invisible dashboard is 404 (L3, as `getCatalog`).
  - **Change rights:** a shared dashboard can be changed with `dashboard:manage`, a personal one by its owner only; otherwise 403. A personal dashboard of another person stays 404 for non-admins and is 403 for admins.
  - **Checks:** names, scope transition, widget limits, grid, the type-specific config and the catalog through `Catalogs.get(principal, catalogId)` (not found or invisible → 403 naming `catalogId`; `savedViewId` must be in `Catalog.savedViews()`, otherwise 422). On a shared dashboard the catalog must have scope `instance` (422 naming `catalogId`); a personal dashboard whose widgets show a personal catalog cannot become shared (422 naming `scope`). Becoming shared also checks the name against the shared dashboards.
  - Every write locks the dashboard row, checks `If-Match`, raises `version` and writes the audit event.
- **`DashboardData`:**
  - text widgets give `static` and need no query;
  - table widgets call `CatalogQueries.query(principal, catalogId, new Request(page, pageSize, null, null, List.of(), null, savedViewId, null), Channel.DASHBOARD)`; the new channel value `DASHBOARD` tags the query metrics and the timeout audit;
  - `NoSuchElementException` gives `error` `catalog-not-found`, `InvalidCatalogQueryException` naming `view` gives `view-not-found`; other failures propagate;
  - each widget runs in its own try block, so one failure does not abort the others.
- **Audit details:** `dashboardId`, `scope`, and the changed fields (`name`, `description`, `scope`, `widgets` with `widgetId` and `action` `added`/`changed`/`removed`, `layout`); no Markdown content and no names.
- **Callers that are not persons** (service accounts) get 403 on every dashboard operation, as on the catalogs (`Catalogs` reads per person); dashboards are for persons.

### D4 – Frontend

- **Routes:**
  - `/dashboards` shows `DashboardsPage.vue` (SCR-056);
  - `/dashboards/:id` shows `DashboardPage.vue` (SCR-024).
  - `components/AppHeader.vue` gets the link "Dashboards" next to "Catalogs", under the same `entity:read` condition (`canSeeTree`).
- **SCR-056:**
  - a list with name, scope badge, owner (for administrators also for other persons' personal dashboards) and widget count;
  - "Create dashboard" (dialog: name, description, scope; `instance` only offered with `dashboard:manage`);
  - after creating, it navigates to the new dashboard.
- **SCR-024:**
  - **View mode:**
    - a CSS grid with 12 columns;
    - the widgets at their positions;
    - an empty dashboard shows "No widgets yet. Add widget." (the action only with `canEdit`).
  - **Edit mode (`canEdit`):**
    - "Add widget" (type text or table), "Edit" and "Remove" per widget, "Rename" and "Delete" for the dashboard, and the scope change;
    - the layout by drag and drop: move the widget by its header, resize by its corner;
    - keyboard buttons for column ±1, row ±1, width ±1 and height ±1;
    - the editor checks `col + width ≤ 13` before sending and shows "Widget exceeds the grid width";
    - overlapping widgets get a red border.
  - All writes send `If-Match` with the loaded version and reload on 412.
- **Grid library:** none is in `package.json` yet. A small library is allowed if its license and those of its bundled dependencies are permitted by ADR-043 (e.g. `grid-layout-plus`, MIT, with `interactjs`, MIT); otherwise pointer events on a CSS grid.
- **Text widget:**
  - Markdown is rendered with `markdown-it` (MIT) with `html: false`, and its output goes through `dompurify` (`MPL-2.0 OR Apache-2.0`, one allowed alternative suffices) with a tag and attribute allowlist and a URL scheme allowlist of `http`, `https` and `mailto`; neither is in `package.json` yet, and only bundled packages enter the license manifest (`frontend/third-party-licenses.ts`);
  - `v-html` is used only for this sanitized output.
- **Table widget:**
  - the columns and rows come from `WidgetData`;
  - the pagination uses `getDashboardWidgetData`;
  - cell values are rendered as text, and redacted values are shown as in the catalog; `components/CatalogTable.vue` is reused read-only where it fits.
- **Fakes and stories:**
  - fakes in `frontend/src/test/dashboards.ts`, next to `catalogs.ts`;
  - stories `SCR-056-dashboards.stories.ts` and `SCR-024-dashboard-editor.stories.ts`: empty, with text and table, an error widget, overlap, read-only.

### D5 – Documentation and data model

- **Manual (en/de):** the new page "Dashboards" in the usage section, with `covers` US-055, US-058, US-059, US-060 and US-062.
- **`SCREENS.md`:**
  - SCR-024 is set to `implemented`, with its legacy SVGs replaced by images;
  - the new SCR-056 is added under "Client App – Core Functions" (CA+WP, UC-07, should) as `implemented`, and both go into the should-have overview;
  - SCR-053 stays.
- **Business object and data model:** `business-objects/dashboard.md` (owner, version, limits, widget types in this change) and `docs/data-model.puml`.
- **Audit catalog:** `docs/audit-event-catalog.md` gets `dashboard.created`, `dashboard.updated` and `dashboard.deleted`.
- **Requirements:**
  - UC-07 and REQ-051 to REQ-057 are realigned with the owner decisions;
  - US-062 AC3 is realigned to 404;
  - US-055, US-058, US-059 and US-062 are set to `done`;
  - US-060 is set to `in-progress`, because AC5, the portal, follows in S17.
- **Third-party licenses:** `THIRD-PARTY-LICENSES.md` is regenerated after the new frontend dependencies.
- **ADR-032:** the amendment 2026-11-18 (`settings:manage` reads personal dashboards) is written with this change.
- **Sprint plan:** the S16 row gets the status of change (3) and the owner decisions of 2026-11-18 on dashboards.

### D6 – Security

- **Authorization:**
  - reading needs `entity:read`;
  - shared dashboards are changed with `dashboard:manage`, personal ones only by their owner;
  - administrators (`settings:manage` with `entity:read`) read personal dashboards and never change them;
  - service accounts get 403 on every dashboard operation;
  - personal dashboards of others are 404 for everyone else, so their IDs are not confirmed.
- **Data:** the table data always runs as the reader. Catalog visibility and property redaction apply, so a shared dashboard never discloses another person's personal catalog or restricted values. When it is written, the catalog must be visible to the writer, and a shared dashboard shows shared catalogs only.
- **XSS:** Markdown is sanitized in the client (D4), and the content is stored as text. The server returns the content unchanged; it is never rendered on the server.
- **Input limits, both sides tested:**
  - name 200/201, description 1000/1001 and title 150/151;
  - content 10 000/10 001;
  - `pageSize` 5/4 and 100/101;
  - widgets 50/51;
  - `col` 1/0 and 12/13, `width` 1/0 and 12/13, `col + width` 13/14;
  - row 1/0 and 500/501, height 1/0 and 6/7;
  - layout items 1–50, where 0 is rejected.
- **Integrity:** `If-Match` on all writes, and the row lock per dashboard.
- **Audit:** all writes are audited, rejections are not; details carry IDs and field names only.

## Risks / Trade-offs

- **Many table widgets:** a dashboard with many table widgets runs one catalog query each. The 50-widget limit bounds the cost per request.
- **Admin read:** an administrator who reads a personal dashboard sees the table data with their own visibility, not the owner's; a widget on the owner's personal catalog shows `catalog-not-found`.
- **Deleted catalogs:** there is no `deleteCatalog` operation yet; tests remove the catalog row directly to cover US-058 AC4.

## Migration Plan

V43 is additive. No existing data is affected.

## Open Questions

None.
