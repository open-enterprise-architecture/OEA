# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design, section API):
  - remove `getPortalCatalogView` and `CatalogFilterOptions`;
  - add `listPortalCatalogs` and `queryPortalCatalog` with `x-oea-authorization` (`portal:read`);
  - move `getPortalEntityDetail` to `/portal/entities/{id}` (still planned);
  - update the tag description.

  Verify with Redocly lint, `python3 scripts/check_openapi_authorization.py` and the generated `PortalApi` interface compiling.

## 2. Backend: portal catalogs

- [x] 2.1 Add `CatalogStore.listShared()` and `PortalCatalogs` (D1), and move the result mapping into `CatalogQueryMapping` (D2). Verify with `PortalCatalogsTests` for these cases:
  - "Shared catalogs only" and "Own personal catalog not in the portal";
  - "Rows of a shared catalog": the result equals `CatalogQueries.query` with the default view state;
  - "Sorting and paging", "Personal catalog" (404 for the creator too) and "Unknown sort column" (400 `sort`);
  - "Restricted property" and "Deleted entity";
  - the existing `CatalogController` tests still pass.
- [x] 2.2 Add `PortalController` implementing `PortalApi` (D2). Verify that the operations no longer answer 501 and that a principal without `portal:read` gets 403.
- [x] 2.3 Cucumber: a new `features/portal/portal-catalogs.feature` with the API scenarios of `portal/portal-catalogs`: list, query, personal catalog, without permission and sort error. Use a portal reader with only the role `portal-reader`. Verify with the `CucumberIT` run.

## 3. Backend: delivery of the portal

- [x] 3.1 Add the property `oea.portal.directory` and `PortalConfiguration` with the resource handler, the fallback to `index.html` and the cache headers (D3). Verify with `PortalDeliveryTests` (MockMvc, temporary directory):
  - "Link into the portal", "Unknown API path" and "Without a portal directory";
  - "Hashed asset", a missing asset (404), and `no-cache` on `index.html`;
  - `/api/v1/catalogs` without a session still answers 401.
- [x] 3.2 Add the portal content security policy in `portal/content-security-policy.txt` and the headers per path in `SecurityConfiguration` (D4). Verify in `PortalDeliveryTests`:
  - "Headers of index.html";
  - "API headers unchanged", with the existing header tests still passing;
  - authenticated API responses keep `no-store`.
- [x] 3.3 Image (D5):
  - add the `frontend` stage to `backend/Dockerfile`, pinned by digest;
  - extend the `.dockerignore` allowlist;
  - add `/app/portal` and `OEA_PORTAL_DIRECTORY`;
  - add the checks for `index.html` and source maps to `scripts/check_production_artifact.py`;
  - add "Portal in the operator setup" to `scripts/compose_smoke_test.sh`.

  Verify with a local `docker build` and the smoke test, or with the CI job "Security checks".

## 4. Web portal

- [x] 4.1 Add `src/api/portal.ts`, `PortalPage.vue` and `PortalCatalogPage.vue` with their routes, plus the `readOnly` prop of `CatalogTable` and the i18n keys `portal.*` in English and German (D7). Verify with `PortalPage.spec.ts` and `PortalCatalogPage.spec.ts`:
  - "Opening a shared catalog": list, link, sort, page and no change actions;
  - the empty and error states, and "Catalog not found".
- [x] 4.2 Add the router guard for readers without `entity:read`, and the header entry "Portal" (D7). Verify with `guards.spec.ts` or `router` tests and `AppHeader.spec.ts`:
  - "Landing page of a portal reader";
  - "Portal entry for architects".
- [x] 4.3 Add the story `screens/SCR-050-portal-home.stories.ts` (Overview, Empty, Catalog, NotFound) and update `docs/screens/SCREENS.md`: SCR-050 `implemented`, the generated images, and the legacy SVGs deleted (ADR-031). Verify with `npm run test:stories` and `python3 scripts/check_screens.py`.
- [x] 4.4 Add the browser smoke test `npm run test:csp` (D6) and run it in the CI job "Frontend" after the build. Verify that "Portal runs under the policy" passes locally in the Playwright container. If it reports a style violation, apply the `style-src` relaxation of D4 and record the reason.

## 5. Manual

- [x] 5.1 Add `manual/usage/portal.md` and `manual/de/usage/portal.md` (covers SCR-050), the navigation entry in `manual/.vitepress/config.mts`, and the links from the index pages. Update the installation page in both languages: the portal is served by the backend and needs TLS (D8). Verify with `python3 scripts/check_manual.py` and the manual build.

## 6. Wrap-up

- [x] 6.1 Add implementation notes to REQ-187 (portal headers, AC1) and REQ-160 (portal channel), and the status of change (6) to the sprint plan. Leave the hash for the archive PR.
- [x] 6.2 Synchronize `docs/screens/SCREENS.md`. The data model is unchanged (`docs/data-model.puml`, `business-objects/`). Regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [x] 6.3 Run `./mvnw verify`, the frontend type-check, tests, build, story tests and `test:csp`, markdownlint, `openspec validate --specs --strict` and all repository checks, including `python3 scripts/validate_links.py`.
- [x] 6.4 Close #67 and #76 through the implementation PR (`Closes #67`, `Closes #76`).
