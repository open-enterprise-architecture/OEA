# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design D1):
  - add `updateCatalogColumns` (`PUT /catalogs/{id}/columns`) with `CatalogColumnsUpdate`, the condition for instance catalogs and the responses 200, 403, 404 and 422;
  - add the schemas `CatalogColumnConfig`, `CatalogSystemField`, `CatalogJoinDefinition`, `CatalogSavedFilter`, `CatalogFilterExpression`, `CatalogSavedView` and `CatalogJoinModeOverride`, and type `Catalog` with them (#64);
  - update the description of `queryCatalog` (configured columns, default sort, sortability, enum order).

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend

- [ ] 2.1 `Catalogs.updateColumns` (D2, D3): validation, storage in `config.columns`, `last_modified_*`, the audit event `catalog.columns-changed` and the permission rules. Add the controller method with `@ConditionsEnforcedByHandler` and `probePath`, and the mapping of the configured columns into `Catalog`. Verify with integration tests in `CatalogsTests` or a new `CatalogColumnsTests`:
  - happy path with label and order;
  - an unknown, unmapped or non-public property, a duplicate and an invalid label;
  - no visible column and two default sorts;
  - 403 for `viewer` on an instance catalog, on another person's personal catalog and on an unknown ID;
  - the own personal catalog;
  - the audit event;
  - the other `config` keys unchanged.
- [ ] 2.2 Query with configured columns (D4) in `CatalogQueries` and `CatalogQueryStore`: system fields, labels, stale columns skipped, the default sort in `sort`, 400 for a non-sortable column. Verify with tests in `CatalogQueryTests`.
- [ ] 2.3 Enum order (D5) in `CatalogQueryStore`. Verify with a test in `CatalogQueryTests`: ascending and descending by enum order, values outside the enum and missing values last (#65).
- [ ] 2.4 Cucumber `features/catalog/catalog-columns.feature` with the scenarios of `catalog/catalog-columns`, and the new scenarios of `catalog/catalog-query` in its feature (US-047 AC1–AC5). Verify with the `CucumberIT` run.

## 3. Web portal

- [ ] 3.1 SCR-020 dialog "Configure columns" (D6): API functions and types; the dialog with drag and drop, up/down buttons, switches, labels, default sort and the error for no visible column; the button with its permission rule; reloading the table after saving; messages in English and German. Verify with specs for the scenarios of `catalog/catalog-columns` and with the bundle key parity. Add the story "Column configuration", run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 4. Manual and documentation

- [ ] 4.1 Manual in English and German: `usage/catalogs.md` with a section "Configuring columns" and the API operation. Verify with `python3 scripts/check_manual.py` and the manual build.
- [ ] 4.2 `business-objects/catalog.md` (ColumnConfig as implemented, BR for visible column and default sort), `docs/data-model.puml` (`config.columns`), `docs/audit-event-catalog.md` (`catalog.columns-changed`).
- [ ] 4.3 Realign US-047 (status, technical notes), REQ-044 (inputs, AC1–AC5, implementation) and REQ-046; close the scope of #64 and #65 in the PR; update the sprint plan. Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 5. Verification

- [ ] 5.1 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
