# Tasks

## 1. Data model and migration

- [x] 1.1 Update `docs/data-model.puml` and `business-objects/metamodel-configuration.md` for D1, D4 and D5 (scope and origin on metatypes, property definitions and categories; `is_builtin` removed; actor pair on `metamodel_configurations`; new `metamodel_package_imports`); verify the PlantUML file renders and link validation passes
- [x] 1.2 Flyway migration `db/migration/V5__metamodel.sql` with the metamodel tables of the design, the instance configuration and the grants for `oea_app`; verify with schema tests for owners, grants, scope constraints and the instance configuration

## 2. Contract

- [x] 2.1 Extend `api/openapi.yaml` (design, API): package identity, `scope` and `sourcePackage`, `StarterPackage`, `listStarterPackages`, `importStarterPackage`, 409/413 and `ImportResult.batchId`/`conflictList`; verify Redocly lint, `scripts/check_openapi_authorization.py` and that the generated interfaces compile

## 3. Package import

- [x] 3.1 Implement the size-limit filter and the limited JSON reader with located parse errors and the schema validation of D3; verify with tests for the specs "Syntactically faulty package", "Package violating the schema", "Package above the size limit" and "Unsupported package sections"
- [x] 3.2 Implement the atomic import with scope, origin, update of the same package and conflict detection (D1, D2), and the import record and audit events (D5); verify with tests for the specs "Package with five metatypes", "Re-import of the same package", "Import without permission", "Name already used by another package" and "Batch ID of an import"
- [x] 3.3 Write the Cucumber feature `metamodel/package-import.feature` for the `metamodel/package-import` spec against the running application; verify it passes through `CucumberIT`

## 4. Starter packages and metatype catalog

- [x] 4.1 Create `packages/oea-starter-togaf-classic.json` (D7), copy `packages/` into the backend artifact and implement `StarterPackages` with startup validation (D6); verify with a test that every shipped package is valid and that the summary lists seven metatypes
- [x] 4.2 Implement `listStarterPackages` and `importStarterPackage`; verify with tests for the specs "Package list of a new instance", "Starter configuration imported" and "Unknown starter package"
- [x] 4.3 Implement `listMetatypes` and `getMetatype` (D8); verify with tests for the specs "Metatypes after the starter import" and "Reading without permission"
- [ ] 4.4 Write the Cucumber feature `metamodel/starter-packages.feature` for the `metamodel/starter-packages` and `metamodel/metatype-catalog` specs, with a person holding `metamodel-admin` through an IdP group mapping; verify it passes through `CucumberIT`

## 5. Frontend

- [ ] 5.1 Add the start-page card "Import starter configuration" (D9) with the API client for starter packages and metatypes; verify with Vitest tests for the specs "Card on a new instance" and "No card without permission or after the import"; the card gets no screen story (screen stories with change 6, as for the pages of change 3)

## 6. Walking-skeleton check

- [ ] 6.1 Verify walking-skeleton steps 3–5 manually in the development environment: bootstrapping, `scripts/dev_seed.py`, OIDC login as `dev-metamodel-admin` (TOTP), import of `oea-starter-togaf-classic` through the start-page card or the API, seven metatypes listed, re-import without duplicates, `oea audit verify` OK; record the result in the PR

## 7. Wrap-up

- [ ] 7.1 Document the starter configuration (who may import, `metamodel-admin` mapping, package format, limits) in `docs/operations.md`, the new event types in `docs/audit-event-catalog.md` and step 5 in `docs/walking-skeleton.md`; clarify in US-033 that `Application Component` comes from the starter package (ADR-005), not as built-in type; verify markdown lint and link validation pass
- [ ] 7.2 Update the user story US-033 (status `in-progress`, AC1 for packages implemented; AC2–AC4 follow); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py`; the implementation hash is added when the change is archived after the merge
- [ ] 7.3 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (unchanged) with the implementation; regenerate `THIRD-PARTY-LICENSES.md` if dependencies changed; run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check` and the OpenAPI checks and verify all pass
