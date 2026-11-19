# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1): `listPortalDashboards`, `getPortalDashboard`, `getPortalDashboardData` (no longer planned) and `getPortalDashboardWidgetData` with `portal:read`; the descriptions of `getPortalDashboardData` and the tag `Portal`.

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and `python3 scripts/test_check_openapi_authorization.py` pass (adjust the expected summary line).

## 2. Backend

- [x] 2.1 The portal visibility rule and `PortalDashboards` (D2, D3), and `PortalController`. No SQL in `app`, ArchUnit stays green.

  Verify with unit or integration tests:
  - the list for a portal reader: shared only; for the owner: shared plus own personal; for `settings:manage`: no foreign personal ones; ordered by name ignoring case;
  - `canEdit` is `false` in every portal answer, also for a holder of `dashboard:manage`;
  - config, data and widget data:
    - 200 for shared and own personal;
    - 404 for another person's personal dashboard (portal reader and administrator) and for an unknown dashboard or widget;
    - the administrator still reads it in the work area (`getDashboardConfig` 200), so the work-area rule is unchanged;
  - 403 without `portal:read`, and 403 for a service account with `viewer` on all four operations, also for an unknown dashboard ID (the service-account check comes before the lookup);
  - 400 for a dashboard ID or widget ID that is no UUID;
  - the widget data equals the work-area data for the same reader. This covers KPI (with `vs-previous-value`), chart, text and table, the errors `restricted`, `invalid-configuration` and "Catalog not found", one widget failing and the others ok;
  - table pages: page 0 and page 1 (entities 11 to 20 of 47 with 10 per page) accepted, page -1 rejected with 400;
  - a table read in the portal is measured with channel `portal`.
- [x] 2.2 Cucumber: `features/portal/portal-dashboards.feature` with the scenarios of the delta spec `portal/portal-dashboards`.
  - Use the instance `CatalogSteps.shared()` with its persons: the portal reader of `PortalSteps` (its session is private today; expose it through a public static accessor like `PortalSteps.personWithoutRole()`), `instance().viewer()` and `instance().architect()` (Kurt, owner of the personal dashboard). Do not use `SolutionSteps.portalReaderSession()`, which belongs to another instance.
  - The scenarios with the administrator (`settings:manage`) and the service account are covered by the tests of 2.1, as in `DashboardSteps`.
  - Never log the same IdP user in twice and never add an IdP mapping again.
  - Close any extra instance after its feature.

  Verify: the full Cucumber suite passes.

## 3. Frontend

- [x] 3.1 API client and fakes for the four operations; `TableWidget.vue` takes the page loader as a prop (D4), so the work area keeps `fetchWidgetData` and the portal uses `getPortalDashboardWidgetData`.
- [x] 3.2 Stories:
  - SCR-053 `frontend/src/screens/SCR-053-portal-dashboard.stories.ts`, with a KPI, a bar chart, a pie chart and a table; a failing widget; a personal dashboard; "Dashboard not found";
  - SCR-050 with the section "Dashboards", including the empty state.

  Verify: the stories render without a11y violations; `python3 scripts/check_screens.py` passes.
- [x] 3.3 `PortalPage.vue` (section "Dashboards"), `PortalDashboardPage.vue` with the route `/portal/dashboards/:id`, the read-only mode of the grid, "Refresh" and the router guard (D4), with `i18n` in en and de.

  Verify with specs:
  - the list with the badge "Personal" and the empty state; a failing dashboard list does not hide the catalogs, and the reverse;
  - the view in saved positions (`DashboardGrid` with `editing: false`) without "Edit", "Add widget", "Delete", drag handle or resize handle;
  - "Refresh" reads the data again;
  - table paging through the portal widget operation;
  - "Dashboard not found" on 404;
  - `portalTarget`: `/dashboards` → `/portal` and `/dashboards/{id}` → `/portal/dashboards/{id}` for a reader without `entity:read`, and no redirect with `entity:read`.

  Use `vi.waitFor` for asynchronous rendering.
- [x] 3.4 `KpiWidget.vue` (D5): the date of the previous value as visible text, without the `title` tooltip.

  Verify: the KpiWidget spec shows the date as text in en and de, and no `title` attribute.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D6:
  - the manual (en/de);
  - SCREENS.md, with the legacy SVGs of SCR-053 deleted;
  - US-060 and US-061 done, US-061 AC6 realigned;
  - UC-07, REQ-055 and REQ-056;
  - the ADR-032 amendment;
  - the sprint plan;
  - the traceability matrix regenerated.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - `python3 scripts/check_screens.py`;
  - markdownlint;
  - `openspec validate us-061-portal-dashboards --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
