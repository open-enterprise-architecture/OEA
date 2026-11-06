# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design D1):
  - `CatalogSavedViewInput` and `CatalogSavedView` with `available`, without `activeFilterIds`;
  - `addCatalogView`, `updateCatalogView` and `removeCatalogView` with the condition for instance catalogs and the responses;
  - the parameter `view` of `queryCatalog`, `view` in `CatalogQueryProblem` and the description;
  - the note of `Catalog.savedViews`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend

- [ ] 2.1 Views in `Catalogs` (D2, D3):
  - add, replace and remove with the validation order of D2;
  - storage in `config.savedViews` under the row lock, the default switch;
  - `available`, the cleanup on `removeJoin`, the permission rule and the audit events;
  - the controller methods with `@ConditionsEnforcedByHandler`.

  Verify with tests in a new `CatalogViewTests`:
  - save and read back;
  - 422 for an unknown column, a filter outside the view, an invalid sort, an unknown join override, a taken name and the limit;
  - the default switch, and removing the default;
  - 403 for `viewer`, and success in the own personal catalog;
  - a removed column makes the view unavailable;
  - the join removal cleanup;
  - the audit events.
- [ ] 2.2 The query with `view` (D4): the columns of the view in its order, filters and sort restricted to them, 400 `view` for an unknown or unavailable view. Verify with tests in `CatalogViewTests`: "Columns of the compact view", a filter on a column outside the view (400 `filter`), "Unknown view".
- [ ] 2.3 Cucumber: a new `features/catalog/catalog-views.feature` with the scenarios of `catalog/catalog-views` that the API can show, and "Remove a join used by a view" in `catalog-joins.feature` (US-053 AC1–AC4, REQ-048 AC1–AC3, AC6). Verify with the `CucumberIT` run.

## 3. Web portal

- [ ] 3.1 SCR-020 views (D5):
  - API functions and types;
  - the view selection with "All columns", the default mark, unavailable views and "(changed)";
  - `view` in the URL and the fallback on 400;
  - the default view on opening;
  - messages in English and German.

  Verify with specs in `CatalogPage.spec.ts` for "Switch to the full view", "Default view applied", "Shared link keeps its state", "No default view" and a reload with a view.
- [ ] 3.2 SCR-020 "Save view" and the dialog "Views" (D5): the modal with name, description, column selection and order, and default; "Make default" and "Remove"; the permission rule. Verify with specs for "Compact view", "Default switch" and the reader without the right. Add the stories "Views" and "Save view" to `SCR-020`, run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 4. Manual and documentation

- [ ] 4.1 Manual in English and German: `usage/catalogs.md` with a section "Views" (choosing a view, the default view and links, saving, making default, removing, unavailable views, the API) and the `covers` entry for US-053. Verify with `python3 scripts/check_manual.py` and the manual build.
- [ ] 4.2 `business-objects/catalog.md` (SavedView, BR-05, BR-06, BR-10), `docs/data-model.puml` (`config.savedViews`) and `docs/audit-event-catalog.md` (`catalog.view-added`, `catalog.view-changed`, `catalog.view-removed`) (D6).

## 5. Wrap-up

- [ ] 5.1 Realign:
  - US-053 (status, AC1, technical notes);
  - US-049 (AC6, implementation line);
  - REQ-048 (inputs, processing, AC1, AC4, implementation notes);
  - REQ-047 (AC7 dropped).

  Update the sprint plan and SCREENS.md. Regenerate the traceability matrix with `python3 scripts/generate_traceability.py`, and verify with `validate_links.py` and `generate_traceability.py --check`.
- [ ] 5.2 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
