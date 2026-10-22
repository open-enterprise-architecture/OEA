# Tasks

## 1. Contract

- [ ] 1.1 Extend `api/openapi.yaml` (design, API): `GET /catalogs/{id}/query` with `PageParam`, `PageSizeParam`, `SortParam` and the schemas `CatalogColumn`, `CatalogRow`, `CatalogQueryResult`; remove `/views/catalog`, `/views/catalog/{id}` and the tag `Views – Catalog`, update the description of `/portal/catalog` (D5); verify Redocly lint, `scripts/check_openapi_authorization.py`, that the generated backend interfaces compile and that `npm run generate:api` succeeds

## 2. Backend: catalog query

- [ ] 2.1 Implement the column resolution in `io.oea.app.catalog` (default columns `name` and the public, mapped property `Status`, D2) and `CatalogQueryStore` in `io.oea.core.catalog` (one page query plus count, sort with `NULLS LAST` and name/id tie-breakers, D3); verify with integration tests for the specs "Default columns of an application catalog", "Second page sorted by name", "Empty repository" and "Deleted entity", for sorting by status with missing values last and for a page beyond the last one
- [ ] 2.2 Implement `queryCatalog` in `CatalogController` with the visibility rule of `Catalogs.get` (D4) and the 400 problem for an invalid `sort` (D1); verify with integration tests for "Personal catalog of another person", "Unknown sort column", several sort pairs, `pageSize` above 200 and a principal without `entity:read`, and that `AuthorizationMatrixTests` covers the new operation
- [ ] 2.3 Write the Cucumber feature `catalog/catalog-query.feature` for the `catalog/catalog-query` spec (API scenarios: three applications, empty repository, 60 applications on page 1 with page size 20, 120 applications with the default page size, descending sort, foreign personal catalog, deleted entity) with persons holding `enterprise-architect` and `viewer` through IdP group mappings and the starter package imported; verify it passes through `CucumberIT`

## 3. Frontend: catalog table

- [ ] 3.1 Add `queryCatalog` to `src/api/catalogs.ts`; verify with unit tests for the query string (page, page size, sort) and the 404 answer
- [ ] 3.2 Implement `CatalogTable.vue` (returned columns, header cycle ascending → descending → default with ↑/↓ and `aria-sort`, "Page N of M" and `UPagination`, empty state, loading state) and use it on `CatalogPage.vue` (D6); verify with component tests for the specs "Catalog with three applications", "Sort ascending", "Switch sorting", "Empty repository" and "First of three pages" (a sort change resets to page 0); `npm run type-check` and `npm test` pass
- [ ] 3.3 Extend `frontend/src/screens/SCR-020-catalog-browser.stories.ts` with the catalog table as first story and the variants of D7; set SCR-020 and SCR-028 to `implemented` in `docs/screens/SCREENS.md`; render the images with `npm run render:screens`; verify `npm run build-storybook`, the story play/a11y checks and `python3 scripts/check_screens.py` pass

## 4. Walking-skeleton check

- [ ] 4.1 Verify walking-skeleton steps 7–8 in the development environment: `dev-architect` creates "Application Inventory", the catalog page shows "CRM System", "ERP" and "Intranet" with name and status, sorting by name ascending/descending/default works, a batch of 120 further applications shows "Page 1 of 3"; `dev-viewer` sees the same table; record the result in the PR

## 5. Wrap-up

- [ ] 5.1 Align REQ-046 with the contract conventions (D1: `page`, `pageSize` ≤ 200, `sort`, `PageMeta`) and the default columns; document the default-column rule in `business-objects/catalog.md`; update `docs/walking-skeleton.md` (step 8, status: walking skeleton complete); verify markdown lint and link validation pass
- [ ] 5.2 Update the user story US-049 (status `in-progress`, AC1 without join column and AC2–AC5 implemented, AC1 join column and AC6 follow); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py`; the implementation hash is added when the change is archived after the merge
- [ ] 5.3 Synchronize `docs/data-model.puml` (unchanged), `business-objects/catalog.md` and `docs/screens/SCREENS.md` with the implementation; regenerate `THIRD-PARTY-LICENSES.md` if the bundle changed; run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check`, the OpenAPI checks and `./mvnw verify` and verify all pass
