# Tasks

## 1. Contract

- [ ] 1.1 Update `api/openapi.yaml` as in D7; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`

## 2. Backend

- [ ] 2.1 Add the app exceptions and their problem mapping (D1) and `StorageNames` (D2); verify with unit tests for the derivation of storage names (special characters, leading digit, reserved name, collision, 63 characters)
- [ ] 2.2 Add `MetatypeEditing` and the metatype operations (D3); verify with integration tests for "SecurityZone created", "Name of an imported metatype", "Without permission", "Metatype with entities" (also soft-deleted and a catalog), "Imported metatype", `isConnection: true` and the audit events
- [ ] 2.3 Add `PropertyDefinitions` with enum values and categories (D4); verify with integration tests for "Enum property with values", "Name taken", "Enum value in use", "Imported property", the data type change with values (also in the history), the deletion with mappings and of a used category, `reference` rejected and the audit events
- [ ] 2.4 Add `PropertyMappings` and the mapping `scope` (D5); verify with integration tests for "Mandatory property", "Own property on an imported metatype", "Unused mapping removed", the removal with values, a duplicate mapping and a package mapping that cannot be removed
- [ ] 2.5 Keep organization mappings in `PackageImporter` (D6); verify with an integration test for "Re-import keeps organization mappings" and that the existing import tests pass
- [ ] 2.6 Cucumber scenarios in `metamodel/metatype-editing.feature` (new) for US-032 AC1 to AC3; verify they pass through `CucumberIT`

## 3. Documentation

- [ ] 3.1 Document the administration of the metamodel through the API in `manual/administration/metamodel-packages.md` (or a new page next to it) in English and German with US-032 in `covers`; add the audit events of D3 to D5 to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 4. Wrap-up

- [ ] 4.1 Update US-032 with the realignment notes of the proposal (status stays until SCR-030 in S7 change 4); add notes to REQ-032 and ADR-002; record the progress in `docs/sprint-plan.md`; verify markdown lint passes
- [ ] 4.2 Update `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (scope of a mapping, extension of imported metatypes, derived storage name); regenerate `requirements/traceability.md` and verify `--check` passes
- [ ] 4.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-032-metatype-editing --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
