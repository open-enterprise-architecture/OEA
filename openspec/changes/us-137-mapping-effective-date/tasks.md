# Tasks

## 1. Contract and migration

- [ ] 1.1 Update `api/openapi.yaml` as in D7; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [ ] 1.2 Flyway migration `V16__mapping_effective_dates.sql` (D1); verify with a schema test the filled scope and `effective_applied_at` of existing rows and the constraints

## 2. Backend

- [ ] 2.1 Store the scope per entry and use it in `MetatypeCatalog`, `PropertyMappings` and `PackageImporter` (D1); verify that the tests of `us-032-metatype-editing` still pass
- [ ] 2.2 Entries by effective date in `PropertyMappings.create` and reading in effect with `includeHistory` (D2, D5); verify with integration tests for "Mandatory from a date", "Same date twice", "History of the entries", a past date taking effect at once, an override of a package mapping and an entry on a subtype rejected
- [ ] 2.3 Marks follow the necessity (D4) and removal of one of several entries; verify with integration tests for "Removing one of several entries" and marks cleared when a property is no longer mandatory
- [ ] 2.4 `MappingEffectiveDates` (D3); verify with integration tests for "Entities marked on the date" with a mutable clock, an entry applied only once and the audit event
- [ ] 2.5 Re-import with organization entries (D6); verify with integration tests for "Override of a package necessity kept", the same-date rule with `skippedPackageEntries` and the removal of overrides with the setting `remove`
- [ ] 2.6 Cucumber scenarios in `metamodel/metatype-editing.feature` for US-137 AC1 and AC2; verify they pass through `CucumberIT`

## 3. Documentation

- [ ] 3.1 Document effective dates in `manual/administration/metamodel-editing.md` in English and German with US-137 in `covers`; add `metamodel.mapping-effective` and `skippedPackageEntries` to `docs/audit-event-catalog.md`; add the setting `oea.metamodel.effective-date-check-interval` to the operations page if operations settings are listed there; verify the manual check and markdown lint pass

## 4. Wrap-up

- [ ] 4.1 Set US-137 to `done` with the realignment notes of the proposal; add notes to REQ-148 and ADR-002; record the progress in `docs/sprint-plan.md`; verify markdown lint passes
- [ ] 4.2 Update `docs/data-model.puml` and `business-objects/metamodel-configuration.md` (scope per entry, `effective_applied_at`, rule for effective dates); regenerate `requirements/traceability.md` and verify `--check` passes
- [ ] 4.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-137-mapping-effective-date --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
