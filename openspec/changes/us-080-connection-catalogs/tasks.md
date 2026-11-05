# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` (design D1):
  - `CatalogColumnConfig.endpoint` with BR-11 in the description;
  - the descriptions of `CatalogColumn.key` and of the `sort` parameter.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.

## 2. Backend

- [ ] 2.1 Endpoint columns in `Catalogs` (D2):
  - `Column.endpoint`, stored in `config.columns`;
  - the rule for connection catalogs, with 422 in join target columns;
  - the offered properties per side from the allowed endpoints with subtypes, or all `public` properties without a constraint;
  - uniqueness per endpoint and attribute;
  - `available` for properties no longer offered.

  Verify with tests in `CatalogColumnsTests`:
  - the source and target attributes are offered and saved;
  - a property of no allowed endpoint, an endpoint column on an element catalog and in a join target are rejected with 422;
  - an unconstrained side offers every `public` property.
- [ ] 2.2 Store and query (D3, D4):
  - `QueryColumn.endpoint` with the joins on `entity_connections` and the endpoint entities;
  - empty cells for a deleted endpoint;
  - sorting by endpoint columns, and the `SORT` pattern with `source.`/`target.`;
  - the default columns "Source", "Name", "Target" (and "Status") of a connection catalog.

  Verify with tests in a new `ConnectionCatalogTests`:
  - connection rows with endpoint values;
  - sorting by `source.name`;
  - the default columns;
  - a deleted endpoint;
  - the `carries-data` join from a data flow in both modes;
  - an element catalog with a data-flow join unchanged.
- [ ] 2.3 Cucumber: a new `features/catalog/connection-catalogs.feature` with the scenarios of "Connections as rows" and "Default columns of a connection catalog", and the endpoint scenarios of `catalog-columns` in their feature (US-080 AC1–AC4). Verify with the `CucumberIT` run.

## 3. Web portal

- [ ] 3.1 SCR-020 dialog "Configure columns" (D5):
  - the groups "Source" and "Target" with the offered attributes;
  - `endpoint` in the API types and the save;
  - the messages in English and German.

  Verify with specs for the scenarios "Attributes of source and target" and the grouping.
- [ ] 3.2 SCR-020 table, create form and dialog "Joins" (D5):
  - the endpoint column labels and sorting;
  - the grouped metatypes in the create form;
  - the hint for a connection catalog without a possible join.

  Verify with specs in `CatalogPage.spec.ts`, `CreateCatalogForm.spec.ts` and for the dialog "Joins", and with the bundle key parity. Add the story "Connection catalog" with synthetic data in `frontend/src/test/catalogs.ts`, run `npm run test:stories`, and update `docs/screens/SCREENS.md`.

## 4. Manual and documentation

- [ ] 4.1 Manual in English and German: `usage/catalogs.md` with a section "Connection catalogs" (source and target columns, joins from connections) and the `covers` entry for US-080. Verify with `python3 scripts/check_manual.py` and the manual build.
- [ ] 4.2 `business-objects/catalog.md` (ColumnConfig `endpoint`, BR-11, connection metatype as primary) and `docs/data-model.puml` (D6).

## 5. Wrap-up

- [ ] 5.1 Realign US-080 (status, AC5/AC6 to change 3, technical notes), REQ-065 (virtual fields as endpoint columns, BR-04 as the hint, the query operation), REQ-044 and REQ-046 (implementation notes); update the sprint plan and SCREENS.md. Regenerate the traceability matrix with `python3 scripts/generate_traceability.py`, and verify with `validate_links.py` and `generate_traceability.py --check`.
- [ ] 5.2 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
