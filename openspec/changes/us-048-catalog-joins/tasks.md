# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design D1):
  - add `addCatalogJoin`, `updateCatalogJoin` and `removeCatalogJoin` with `CatalogJoinInput`, the condition for instance catalogs and the responses 200/201, 403, 404 and 422;
  - make `CatalogJoinDefinition` writable through the input, add `available`, drop `aggregateLabel`;
  - add the `joinMode` deep-object parameter to `queryCatalog`, `CatalogQueryResult.joins` (`CatalogQueryJoin`), `CatalogRow.joinResults` (`CatalogJoinResult`) and `joinMode` in `CatalogQueryProblem`;
  - update the description of `queryCatalog`.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend

- [x] 2.1 `Catalogs.addJoin`, `updateJoin` and `removeJoin` (D2): validation with `AllowedConnections` and the column rules, storage in `config.joinDefinitions`, the cleanup of saved-view overrides, `last_modified_*`, the audit events and the permission rules. Add the controller methods with `@ConditionsEnforcedByHandler` and `probePath`, and the mapping of the joins into `Catalog`. Verify with integration tests in a new `CatalogJoinsTests`, which also covers 2.2:
  - happy path in each direction;
  - not a connection metatype, an unreachable target, an invalid target column, a default sort on a target column;
  - the limit of 10 joins;
  - edit keeps the ID, remove cleans up overrides;
  - 403 for `viewer`, the own personal catalog, 404 for an unknown join;
  - the audit events.
- [x] 2.2 Traversal and query (D3, D4) in `CatalogQueries` and `CatalogQueryStore`:
  - aggregate joins per page;
  - rows joins with `LEFT JOIN LATERAL` and counting;
  - `both` deduplicated;
  - deleted connections and targets left out;
  - the mode override with 400 for an unknown join;
  - sorting by join columns in rows mode only;
  - unavailable joins skipped.

  Verify with the query tests in `CatalogJoinsTests`, including the binding of `joinMode[...]`.
- [x] 2.3 Cucumber `features/catalog/catalog-joins.feature` with the scenarios of `catalog/catalog-joins`, and the new scenarios of `catalog/catalog-query` in its feature (US-048 AC1–AC6, US-050 AC1–AC4). Verify with the `CucumberIT` run.

## 3. Web portal

- [x] 3.1 SCR-020 dialog "Joins" (D5): API functions and types; the list, the four-step form with options from the allowed endpoints, edit and remove; the button with its permission rule; messages in English and German. Verify with specs for the scenarios of `catalog/catalog-joins`.
- [x] 3.2 SCR-020 table (D5):
  - the join column groups;
  - aggregated cells;
  - the "Compact / Rows" switch;
  - the `mode` URL state;
  - sorting by join columns in rows mode.

  Verify with specs for the scenarios of `catalog/catalog-query` and with the bundle key parity. Add the stories "Joins" and "Join rows", run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 4. Manual and documentation

- [x] 4.1 Manual in English and German: `usage/catalogs.md` with a section "Joins" (adding, modes, sorting in rows mode) and the API operations. Verify with `python3 scripts/check_manual.py` and the manual build.
- [x] 4.2 `business-objects/catalog.md` (JoinDefinition as implemented, `aggregateLabel` dropped, limits), `docs/data-model.puml` (`config.joinDefinitions`), `docs/audit-event-catalog.md` (the three join events).
- [x] 4.3 Realign US-048, US-050 and US-049 (status, technical notes), REQ-045 (inputs, outputs, AC1–AC5), REQ-046 (`joinResults`) and REQ-049; update the sprint plan. Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 5. Verification

- [ ] 5.1 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
