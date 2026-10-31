# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` and the package schema as in D9; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V15__metatype_editing.sql` (D3); verify with a schema test the defaults and constraints on a fresh database

## 2. Backend

- [x] 2.1 Add the app exceptions and their problem mapping (D1) and `StorageNames` (D2); verify with unit tests for the derivation of storage names (special characters, leading digit, reserved name, collision, 63 characters)
- [x] 2.2 Add `MetatypeEditing` with base types and inherited mappings in `MetatypeRules` and `MetatypeCatalog` (D4, D5); verify with integration tests for "SecurityZone created", "Name of an imported metatype", "Without permission", "Subtype inherits mappings", "Cycle", "Metatype with entities" (also soft-deleted, a catalog and a subtype), "Imported metatype", a changed base type, `isConnection: true` and the audit events
- [x] 2.3 Add `EntityStore.removeValues` (D6); verify with an integration test that values are removed, each entity gets a version with the delta and marks are cleared
- [x] 2.4 Add `PropertyDefinitions` with enum values and categories (D5); verify with integration tests for "Enum property with values", "Name taken", "Enum value in use", "Imported property", the data type change with values (also in the history), the deletion of a mapped definition, the retirement of a definition in the history, the deletion of a used category, `reference` rejected and the audit events
- [x] 2.5 Add `PropertyMappings` with marks and adapt `EntityValidator` (D7); verify with integration tests for "Mandatory property for new entities", "Existing entities marked" (also subtypes), "Own property on an imported metatype", "Mapping with values removed", "Marked entity written again", "Mandatory property the writer cannot change", the mark cleared by a value, a property mapped twice in the chain and a package mapping that cannot be removed
- [x] 2.6 Add `ReimportPolicies` and adapt `PackageImporter` (D4, D8); verify with integration tests for "Default keeps organization mappings", "Removal inherited from the base type", the global `remove`, base types in a package (own package, built-in, unknown rejected), new mandatory package mappings marking entities, and that the existing import tests pass
- [x] 2.7 Cucumber scenarios in `metamodel/metatype-editing.feature` (new) for US-032 AC1 to AC3, removing a property and marking entities; verify they pass through `CucumberIT`

## 3. Documentation

- [x] 3.1 Document the administration of the metamodel through the API in `manual/administration/metamodel-packages.md` (or a new page next to it) in English and German with US-032 in `covers`, including base types, removing properties, marks and the re-import setting; add the audit events of D5 to D8 to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 4. Wrap-up

- [x] 4.1 Update US-032 with the realignment notes of the proposal (status stays until SCR-030 in S7 change 4); add notes to REQ-032, REQ-149 and ADR-002; record the progress in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 4.2 Update `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (base type, re-import setting, scope of a mapping, retired definitions, marks, derived storage name); regenerate `requirements/traceability.md` and verify `--check` passes
- [x] 4.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-032-metatype-editing --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
