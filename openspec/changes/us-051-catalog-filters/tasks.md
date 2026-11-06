# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design D1):
  - the parameters `filter` and `filterMode` of `queryCatalog`, `filter` in `CatalogQueryProblem` and the description;
  - `addCatalogFilter` and `removeCatalogFilter` with `CatalogSavedFilterInput`, the condition for instance catalogs and the responses;
  - `CatalogFilterExpression` and `CatalogSavedFilter` as implemented, with `available`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend

- [ ] 2.1 Filter parsing and validation in `CatalogQueries` (D2): keys of visible primary, endpoint and join columns, operators per type, values, at most 20 filters, 400 `filter`. Verify with tests in a new `CatalogFilterTests`:
  - each type with its operators;
  - unknown keys, operators and values;
  - an enum value outside the enum.
- [ ] 2.2 Conditions in `CatalogQueryStore` (D3): primary and endpoint conditions; join conditions as `EXISTS` and in the aggregate and lateral statements; AND and OR; `count`. Verify with tests in `CatalogFilterTests`:
  - "Active applications only" with the total;
  - `contains` ignoring case;
  - a date before;
  - REST interfaces in both join modes;
  - the GDPR example on a data flow catalog;
  - source contains CRM;
  - AND and OR;
  - filtered paging.
- [ ] 2.3 Saved filters in `Catalogs` (D4): add and remove with validation (D2 rules, name, limits), storage in `config.savedFilters`, `available`, the cleanup on `removeJoin`, the permission rule and the audit events. Add the controller methods with `@ConditionsEnforcedByHandler`. Verify with tests in `CatalogFilterTests`:
  - save and read back;
  - 422 for an unknown attribute, a taken name and an empty list;
  - 403 for `viewer`, and success in the own personal catalog;
  - removal;
  - the join removal cleanup;
  - the audit events.
- [ ] 2.4 Cucumber: a new `features/catalog/catalog-filters.feature` with the scenarios of `catalog/catalog-filters` that the API can show, and "Filtered total" in `catalog-query.feature` (US-051 AC1–AC4, US-052 AC1–AC4, US-080 AC5, AC6). Verify with the `CucumberIT` run.

## 3. Web portal

- [ ] 3.1 SCR-020 filter bar (D5):
  - API functions and types;
  - `CatalogFilterBar.vue` with chips, the add popover by type, removal and the AND/OR switch;
  - the URL state and the fallback on 400;
  - messages in English and German.

  Verify with specs in `CatalogPage.spec.ts` for "Active applications only", "Remove a filter", "Operators for the name", "Applications with a REST interface", "Reload keeps the filter" and OR.
- [ ] 3.2 SCR-020 saved filters (D5): the save modal with its permission rule, the quick selection, loading a filter into the bar, and unavailable filters. Verify with specs for "Active systems only", "Activate a saved filter" and the reader without the right. Add the stories "Filters" and "Saved filters" to `SCR-020`, run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 4. Manual and documentation

- [ ] 4.1 Manual in English and German: `usage/catalogs.md` with a section "Filters" (adding, operators, join columns, AND/OR, saved filters, the address) and the `covers` entries for US-051 and US-052. Verify with `python3 scripts/check_manual.py` and the manual build.
- [ ] 4.2 `business-objects/catalog.md` (SavedFilter, FilterExpression, BR-04, BR-12), `docs/data-model.puml` (`config.savedFilters`) and `docs/audit-event-catalog.md` (`catalog.filter-added`, `catalog.filter-removed`) (D6).

## 5. Wrap-up

- [ ] 5.1 Realign:
  - US-051 (status, technical note on the URL);
  - US-052 (AC1, AC3, technical notes);
  - US-080 (AC5, AC6 done);
  - REQ-047 (column keys, scope dropped, runtime parameters, AC6, AC7);
  - REQ-046 AC3 and REQ-064 (implementation note).

  Update the sprint plan and SCREENS.md. Regenerate the traceability matrix with `python3 scripts/generate_traceability.py`, and verify with `validate_links.py` and `generate_traceability.py --check`.
- [ ] 5.2 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
