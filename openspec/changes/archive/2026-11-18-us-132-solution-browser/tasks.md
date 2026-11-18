# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D1): `getBrowserView` (`GET /views/browser`) and `listBrowserItems` (`GET /views/browser/items`), tag `Views – Navigation`, with the schemas `Browser`, `BrowserPlateau`, `BrowserSolution`, `BrowserItemPage` and `BrowserItem`.

  Security (D6): both declare `entity:read`; `pageSize` uses `PageSizeParam` (1–200); the items page uses `PageMeta`; the responses list 400, 401, 403, 429, and for the items also 404 and 422 (`urn:oea:problem:validation-failed` with `invalidFields`).

  Verify: Redocly lint, `python3 scripts/check_openapi_authorization.py` and `python3 scripts/test_check_openapi_authorization.py` pass.

## 2. Backend

- [x] 2.1 The read methods of D2 in `core/entity/EntityStore` and `core/catalog/CatalogStore`, and the new `core/solution/SolutionBrowseStore`.

  Verify with store tests against the test database:
  - the counts of components and connections without deleted entities;
  - the catalogs with and without another person's personal catalog;
  - the grouped delta counts per solution, including a solution without deltas;
  - the order `lower(name), id`, including two entities with the same name;
  - the name of a delta `modified` whose entity was renamed, of one whose entity was deleted, and of a delta `new` after the go-live set its `entity_id`.
- [x] 2.2 `app/navigation/Browser` and `api/navigation/BrowserController` (D3).

  Verify with `BrowserTests`:
  - **top level:**
    - plateaus `transition` and `target` by `validFrom`, then by name;
    - no node for baseline and realized;
    - an empty plateau with 0;
    - no plateau at all;
  - **solutions:**
    - a plateau solution under its target plateau and a project solution under `projects`, sorted case-insensitively;
    - `implemented` and `archived` are left out, both in plateaus and in projects;
  - **categories:**
    - `current-state` with each of the three categories;
    - `solution` with `components` lists `new`, `modified` and `retiring` with `deltaType`, `entityId` and `solutionId`;
  - **solution status:** an `implemented` solution still lists its deltas;
  - **errors:**
    - `scope=solution` without `solutionId`, and `current-state` with `solutionId`, give 422 naming `solutionId`;
    - `connections` and `catalogs` with `scope=solution` give 422 naming `category`;
    - an unknown solution gives 404, and a `solutionId` that is not a UUID gives 400;
  - **paging (parameterized):**
    - `pageSize` 1 and 200 accepted, 0 and 201 give 400;
    - `page` 0 accepted, -1 gives 400;
    - 51 entities give pages of 50 and 1 with `totalElements` 51;
  - **authorization:** both operations give 403 without `entity:read`, and 401 without a session;
  - **catalog visibility:** Franz does not count or list Kurt's personal catalog, and Kurt does.
- [x] 2.3 Cucumber: `features/navigation/solution-browser.feature` with the scenarios of the delta spec `navigation/solution-browser`.

  Verify that it passes, together with the navigation scenarios.

## 3. Frontend

- [x] 3.1 Stories first: SCR-055 with the tab "Browser" (with plateaus and projects, without plateaus, with an empty category, and with a name containing markup) and the tab "Navigation tree".

  Verify that the stories render without a11y violations.
- [x] 3.2 `components/SolutionBrowser.vue`, the tabs in `AppSidebar.vue`, `api/navigation.ts` and the fake `navigationApi` in `test/navigation.ts` (D4). Texts go in `i18n`, in English and German.

  Verify with the specs:
  - "Browser" is the default tab, and the chosen tab survives a route change;
  - the top-level order is current state, plateaus, then projects;
  - an empty category or plateau is greyed out with `(0)`;
  - a category loads only on its first expansion;
  - "Load more" appears while items are fewer than `page.totalElements`, and is gone after the last page;
  - the links of entity, catalog, solution and delta;
  - the delta badges;
  - a name `<img src=x onerror=alert(1)>` is shown literally;
  - without `entity:read` there is no tab.
- [x] 3.3 `docs/screens/SCREENS.md`: SCR-055 names the tabs and the browser; regenerate the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation:
  - **Manual:** the section "Browser" of "Navigation" (`manual/usage/navigation.md`, `manual/de/usage/navigation.md`);
  - **Realignment notes:** UC-13 and REQ-138 to REQ-140 (D5);
  - **Data model:** the note on the computed browser in `business-objects/tree-node.md`.
- [x] 4.2 Set the stories and regenerate the matrix:
  - US-132 to `done`;
  - US-133 to `in-progress` (AC3 follows with the building blocks);
  - regenerate `requirements/traceability.md`.
- [x] 4.3 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-132-solution-browser --strict`;
  - `python3 scripts/check_manual.py`.
