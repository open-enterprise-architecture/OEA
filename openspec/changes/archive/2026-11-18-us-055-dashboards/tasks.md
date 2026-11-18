# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D2): the dashboard and widget operations, `getDashboardWidgetData`, the reshaped schemas, and `getDashboardConfig` and `getDashboardData` without `planned`.

  Security (D6): every operation declares `entity:read` with the `conditions` of D2 (`dashboard:manage`, `settings:manage`); the description of `settings:manage` in `x-oea-permissions` is extended; the limits of D6 are in the schemas; `IfMatchRequiredHeader` on all writes, `VersionETag` on `getDashboardConfig` and the writes; the error responses of D2 are listed.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and `python3 scripts/test_check_openapi_authorization.py` pass.

## 2. Backend

- [x] 2.1 `V43__dashboards.sql` and `DashboardStore` (D1).

  Verify with a migration test:
  - the name uniqueness per scope and owner, case-insensitively;
  - the grid checks at the database level (`col + width` 13 accepted, 14 rejected);
  - the cascade of widgets on delete.
- [x] 2.2 `Dashboards` with the controller (D3): create, list, read, update, delete, scope.

  Verify with `DashboardsTests`:
  - **Shared dashboards:**
    - creating one with and without `dashboard:manage` (201/403);
    - a non-person caller gets 403 on list, read and create;
    - another holder of `dashboard:manage` edits and deletes it;
  - **Personal dashboards:**
    - Michael creates one without `dashboard:manage`;
    - it is not in Kurt's list, and Kurt's read, update and delete give 404;
  - **Administrators:** a holder of `settings:manage` lists and reads a personal dashboard with its owner, and gets 403 when changing or deleting it;
  - **Scope:**
    - personal to instance with `dashboard:manage` succeeds, without it gives 403;
    - personal to instance with a widget on a personal catalog gives 422 naming `scope`;
    - personal to instance with a name taken by a shared dashboard gives 422 `dashboard-name-taken`;
    - instance to personal gives 422 naming `scope`;
  - **Names:**
    - duplicates in the same scope give 422 `dashboard-name-taken`, compared case-insensitively and after trimming;
    - the same name in the other scope, and personal names of two owners, are accepted;
  - **Limits:** name 200/201 and blank, description 1000/1001;
  - **`If-Match`:** missing gives 428, stale gives 412, and the version rises by one per write;
  - **Audit:** `dashboard.created`, `dashboard.updated` with the changed fields, and `dashboard.deleted`, with no Markdown content and no names in the details; no event on a rejection;
- [x] 2.3 Widgets and layout (D3).

  Verify with `DashboardWidgetsTests` (parameterized where possible):
  - **Text widgets:**
    - add, change and remove one;
    - content 0, 10 000 and 10 001;
  - **Table widgets:**
    - with and without a view;
    - another person's personal catalog and an unknown catalog give 403;
    - one's own personal catalog on a shared dashboard gives 422 naming `catalogId`, on a personal dashboard it is accepted;
    - a view of another catalog gives 422 naming `savedViewId`;
    - `pageSize` 5/100 accepted, 4/101 rejected;
    - a property aggregation gives 422 with the specified detail;
  - **Type:** `kpi` and `chart` give 422 naming `type`; changing the type gives 422;
  - **Title:** 150/151 and blank;
  - **Widget count:** the 50th widget is accepted, the 51st rejected;
  - **Grid (parameterized):** `col` 1/12 accepted, 0/13 rejected; `width` 1/12 accepted, 0/13 rejected; `col + width` 13 accepted, 14 rejected with the detail "Widget exceeds the grid width"; row 1/500 accepted, 0/501 rejected; height 1/6 accepted, 0/7 rejected;
  - **Layout request:**
    - 2 positions in one request;
    - an empty list gives 422;
    - a widget of another dashboard gives 422 naming `widgetId`;
  - **Overlap:** overlapping positions are accepted.
- [x] 2.4 `DashboardData` and the read operations (D3).

  Verify with `DashboardDataTests`:
  - the text widget is `static`;
  - the table widget gives the first page and the total;
  - page 2 through `getDashboardWidgetData`;
  - a deleted catalog gives `error` `catalog-not-found` while the text widget is fine (the test removes the catalog row directly; there is no `deleteCatalog` operation yet);
  - a view removed from the catalog (`removeCatalogView`) gives `view-not-found`;
  - an administrator reading Michael's personal dashboard with a widget on Michael's personal catalog gets `catalog-not-found`, not its rows;
  - the query runs on the new channel `CatalogQueries.Channel.DASHBOARD`;
  - a restricted column is redacted for the reader.
- [x] 2.5 Cucumber: `features/dashboard/dashboards.feature` and `features/dashboard/widgets.feature` with the scenarios of the delta specs.

  Logins on shared instances reuse existing sessions (see `PortalSteps.personWithoutRole`); never log the same IdP user in twice, and never add an IdP group mapping that another feature adds.

  Verify that the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 Stories first:
  - SCR-056 with shared and personal dashboards, the administrator view and the create dialog;
  - SCR-024 empty, with text and table widgets, with an error widget, with overlapping widgets in edit mode, and read-only.

  Verify that the stories render without a11y violations.
- [x] 3.2 `DashboardsPage.vue`, `DashboardPage.vue`, the grid editor, the widget dialogs, `api/dashboards.ts`, the routes and the navigation link (D4). Texts go in `i18n`, in English and German.

  Verify with the specs:
  - the create dialog offers `instance` only with `dashboard:manage`;
  - after creating, the empty dashboard opens with "No widgets yet. Add widget.";
  - without `canEdit` there is no edit action;
  - a drag and the keyboard controls send one layout request with `If-Match`;
  - a width beyond column 12 shows "Widget exceeds the grid width" and sends nothing;
  - overlapping widgets are marked;
  - a 412 reloads the dashboard;
  - the table pages through `getDashboardWidgetData`;
  - an error widget shows its message.
- [x] 3.3 Markdown rendering with sanitizing (D4), and the new libraries with permitted licenses (ADR-043).

  Verify with the specs:
  - bold, a list and an `https` link render as HTML;
  - `<script>`, an `onerror` attribute, a `javascript:` link and a `data:` link are removed or made inert;
  - raw HTML in the Markdown is shown as text.
- [x] 3.4 `docs/screens/SCREENS.md`: SCR-024 and SCR-056 (D5).

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation (D5):
  - **Manual:** the page "Dashboards", in English and German;
  - **Business object and data model:** `business-objects/dashboard.md` and `docs/data-model.puml`;
  - **Audit catalog:** `docs/audit-event-catalog.md`;
  - **Requirements:** UC-07 and REQ-051 to REQ-057, realigned (REQ-051: 422 `dashboard-name-taken` instead of 409; REQ-056 and US-062 AC3: 404 instead of 403, administrators with `settings:manage`; REQ-057: row limit 500);
  - **Sprint plan:** the S16 row of `docs/sprint-plan.md` with the status of change (3) and the dashboard owner decisions of 2026-11-18;
  - **ADR-032:** check that the amendment 2026-11-18 matches the implementation;
  - **Third-party licenses:** `THIRD-PARTY-LICENSES.md`, regenerated with `npm run build` and `python3 scripts/generate_third_party_licenses.py`.
- [x] 4.2 Set the stories and regenerate the matrix:
  - US-055, US-058, US-059 and US-062 to `done`;
  - US-060 to `in-progress`;
  - regenerate `requirements/traceability.md`.
- [x] 4.3 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-055-dashboards --strict`;
  - `python3 scripts/check_manual.py`;
  - `python3 scripts/generate_third_party_licenses.py --check`.
