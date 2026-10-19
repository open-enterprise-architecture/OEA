# Tasks

## 1. Data model and migration

- [ ] 1.1 Update `docs/data-model.puml` (`catalogs`: functional unique index, `description` default, CHECK constraints, `config` default, changelog comment for change 6) and `business-objects/catalog.md` (primary metatype by ID, name as a unique but mutable label with references by ID, empty columns allowed, name uniqueness, shared namespace) as in design D1, D2; verify the PlantUML file renders and link validation passes
- [ ] 1.2 Flyway migration `db/migration/V7__catalogs.sql` with `catalogs`, the unique index `catalogs_name` on `lower(btrim(name))` and the grants of D1; verify with schema tests for owner, grants (no `DELETE` for `oea_app`), the CHECK constraints and that " application inventory " collides with "Application Inventory"

## 2. Contract

- [ ] 2.1 Extend `api/openapi.yaml` (design, API): tag `Catalogs`, `listCatalogs`, `createCatalog` with the scope condition, `getCatalog`, schemas `CatalogScope`, `JoinMode`, `CatalogCreate`, `CatalogSummary`, `Catalog`; verify Redocly lint, `scripts/check_openapi_authorization.py`, that the generated backend interfaces compile and that `npm run generate:api` succeeds

## 3. Backend: catalogs

- [ ] 3.1 Add `@ConditionsEnforcedByHandler` and accept it in `OperationCoverageCheck`; extend the authorization matrix with a case per handler-enforced condition (D3); verify with unit tests of the coverage check and the matrix test for `createCatalog` (viewer with `scope=instance` → 403)
- [ ] 3.2 Implement the catalog repository in `io.oea.core.catalog` and the creation service in `io.oea.app.catalog` with scope check, field validation, metatype lookup, insert with mapping of the unique violation and `catalog.created` audit (D3, D4, D6); verify with integration tests for the specs "Catalog created", "Default join mode omitted", "Same name again", "Unknown primary metatype", "Name too long", "Viewer creates a shared catalog", "Viewer creates a personal catalog", "Created catalog audited" and "Rejected creation not audited", and that `oea audit verify` accepts the chain
- [ ] 3.3 Implement the visibility rules for the overview and the single read (D5) and `CatalogController` with `Location` header and problem mapping; verify with integration tests for "Personal catalog of another person" and "Own personal catalog" (ordering by name, 404 for a foreign personal and a non-existent catalog)
- [ ] 3.4 Write the Cucumber feature `catalog/catalog-definition.feature` for the `catalog/catalog-definition` spec (API scenarios) with persons holding `enterprise-architect` and `viewer` through IdP group mappings and the starter package imported; verify it passes through `CucumberIT`

## 4. Frontend: catalog overview, creation and catalog page

- [ ] 4.1 Add `src/api/catalogs.ts` (list, get, create with problem mapping) typed from the generated schema; verify with unit tests for the 201, 404 and both 422 problem types
- [ ] 4.2 Implement `CatalogsPage.vue`, `CreateCatalogModal.vue`, `CatalogPage.vue`, the router entries `/catalogs` and `/catalogs/:id` and the "Catalogs" header link (D7); verify with component tests for the specs "Collision in the creation form", "Viewer opens the creation form" and "Principal without read permission", navigation to `/catalogs/{id}` after creation and the 404 state; `npm run type-check` and `npm test` pass
- [ ] 4.3 Create the screen stories `frontend/src/screens/SCR-020-catalog-browser.stories.ts` (list, empty overview, empty catalog) and `frontend/src/screens/SCR-028-create-catalog.stories.ts` (default, viewer, name collision) with the real components (D8); add SCR-028 to `docs/screens/SCREENS.md`, set SCR-020 and SCR-028 to `prototype`, delete `catalog-browser-light.svg`/`-dark.svg` and link the generated PNGs; verify `npm run build-storybook` and `python3 scripts/check_screens.py` pass once CI has generated the images

## 5. Walking-skeleton check

- [ ] 5.1 Verify walking-skeleton step 7 manually in the development environment: OIDC login as `dev-architect`, "New catalog" → "Application Inventory", `Application Component`, scope `instance`, redirect to the empty catalog page, the catalog in the overview; a second login as a viewer sees it and can create only a personal catalog; `oea audit verify` OK; record the result in the PR

## 6. Wrap-up

- [ ] 6.1 Adjust US-046 AC4 and REQ-043 AC5 to the ADR-032 rule (instance needs `catalog:manage`, personal needs `entity:read`; the creation form offers `instance` only with `catalog:manage`) and reference the rule in UC-06 A4; document `catalog.created` in `docs/audit-event-catalog.md` and step 7 in `docs/walking-skeleton.md`; verify markdown lint and link validation pass
- [ ] 6.2 Update the user story US-046 (status `in-progress` until merge, AC1–AC4 implemented); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py`; the implementation hash and status `realized` are added when the change is archived after the merge
- [ ] 6.3 Synchronize `docs/data-model.puml`, `business-objects/catalog.md` and `docs/screens/SCREENS.md` with the implementation; regenerate `THIRD-PARTY-LICENSES.md` if dependencies changed; run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check` and the OpenAPI checks and verify all pass
