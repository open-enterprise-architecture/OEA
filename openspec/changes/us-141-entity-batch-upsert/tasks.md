# Tasks

## 1. Data model and migration

- [x] 1.1 Update `docs/data-model.puml` (clarifications and changelog comment of D1), `business-objects/entity.md` and `business-objects/entity-version.md` to ADR-022 (typed values, version header plus deltas, batch items without `version`); verify the PlantUML file renders and link validation passes
- [x] 1.2 Flyway migration `db/migration/V6__entities.sql` with `entities`, `entity_property_values`, `entity_version_headers`, `entity_system_field_history`, `entity_property_value_history` and the grants of D1; verify with schema tests for owners, grants (history append-only for `oea_app`), the unique `external_id` index and the one-value check

## 2. Contract

- [x] 2.1 Extend `api/openapi.yaml` (design, API): `unchanged`, `errors`/`warnings` per item, `summary.unchanged`, the 422 problem with `errors[]`; verify Redocly lint, `scripts/check_openapi_authorization.py` and that the generated interfaces compile

## 3. Writing entities

- [x] 3.1 Implement `MetatypeRules` and `EntityValidator` (D2); verify with unit tests per data type and constraint and for the specs "Unknown metatype", "Metatype changed on update", "Mandatory property missing", "Property with necessity warning missing", "Value outside the enum values", "Property not mapped to the metatype" and "Property with restricted visibility"
- [x] 3.2 Implement the entity repository and `EntityWriter` with merge (D3) and versioning (D5); verify with integration tests for the specs "Entity changed" and "Unchanged write"
- [x] 3.3 Extend `AuditWriter` for entity reference, agent fields and change rows and write `entity.created`/`entity.updated` (D6); verify with tests for the specs "Created entity audited" and "Write with agent headers", the sanitizing of `agent_label` and `agent_run`, and that `oea audit verify` accepts the chain with change rows
- [x] 3.4 Write the Cucumber feature `repository/entity-write.feature` for the `repository/entity-write` spec against the running application with the starter package imported; verify it passes through `CucumberIT`

## 4. Batch endpoint

- [x] 4.1 Implement `EntityBatch` and `EntityBatchController` with limit, rejected contract parts, duplicate detection, merge, strategies and both atomicity modes (D4); verify with tests for the specs "Batch without permission", "Batch above the limit", "Duplicate externalId in a batch", "Partial update by a source", "Create-only with an existing externalId", "Skip-existing", "Partial errors without atomicity", "Error in an atomic batch" and "Batch with connections"
- [x] 4.2 Write the Cucumber feature `repository/entity-batch-write.feature` for the `repository/entity-batch-write` spec (including "Upsert of a repository scan" with 200 entities and "Repeated upsert"), with a person holding `enterprise-architect` through an IdP group mapping; verify it passes through `CucumberIT`
- [x] 4.3 Measure 500 new entities in one batch (`atomic=false` and `atomic=true`) against PostgreSQL in an integration test; verify the US-141 target of 5 s and record the measured times in the PR

## 5. Walking-skeleton check

- [x] 5.1 Verify walking-skeleton steps 3–6 manually in the development environment: `scripts/dev_seed.py`, starter import, OIDC login as `dev-architect`, `POST /entities/batch` with "CRM System", "ERP" and "Intranet", the same batch again (unchanged), `oea audit verify` OK; record the result in the PR

## 6. Wrap-up

- [x] 6.1 Document `entity.created` and `entity.updated` in `docs/audit-event-catalog.md` (and remove `entity.*` from the planned types where covered), the batch limit `oea.entities.batch.max-items` in `docs/operations.md` and step 6 in `docs/walking-skeleton.md`; verify markdown lint and link validation pass
- [x] 6.2 Update the user story US-141 (status `in-progress`, AC1 implemented; AC2–AC4 follow); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py`; the implementation hash is added when the change is archived after the merge
- [x] 6.3 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (unchanged) with the implementation; regenerate `THIRD-PARTY-LICENSES.md` if dependencies changed; run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check` and the OpenAPI checks and verify all pass
