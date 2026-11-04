# Tasks

## 1. Contract

- [x] 1.1 Update `api/openapi.yaml` (design D2–D5):
  - add `allowedSourceMetatypeIds` and `allowedTargetMetatypeIds` to `MetatypeWrite`;
  - remove the 422 note on `isConnection`;
  - implement `createConnection` (`429` added, `501` removed) with `ConnectionWrite.name` and the new `ConnectionDetail`;
  - implement `getAllowedConnections` (`404` for an unknown metatype, `429`, no `501`);
  - drop the "not supported" note on `BatchWriteRequest.connections`;
  - add the endpoint error codes to the batch description.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.
- [x] 1.2 Regenerate the frontend API types and the manual API reference.

## 2. Connection metatypes (backend)

- [x] 2.1 `MetatypeEditing`:
  - allow `isConnection` on creation;
  - store and replace the constraint rows, with the version increment;
  - check that listed metatypes exist, that base types have the same kind, and that `isConnection` stays fixed;
  - count a metatype named in constraints as in use when it is deleted.

  Fill `connectionConstraints` in `MetatypeCatalog`. Verify with integration tests for creation, update, kind rules, deletion and the audit details.
- [x] 2.2 Packages: read `connectionConstraints` in `MetamodelPackages`, check the named metatypes in `PackageValidator` (422 with pointer), and replace the rows of the package's own metatypes in `PackageImporter`. Verify with import and re-import tests.
- [x] 2.3 `getAllowedConnections` (D5) in `MetamodelController`. Verify with MockMvc tests for the directions, subtypes, `allowed=false` and an unknown metatype.

## 3. Writing connections (backend)

- [x] 3.1 Flyway `V19__entity_connections.sql` with `entity_connections`, indexes and grants (D1). Verify with a schema test.
- [x] 3.2 `ConnectionInput` and `ConnectionValidator` with the endpoint codes of D3 and the subtype rule, reusing the property checks. `EntityWriter` writes the extension row, the default name (D4) and the audit details. Upserts reject other endpoints. Verify with unit and integration tests for each error code, the default name, a T-relationship and an upsert.
- [x] 3.3 `EntityBatch`:
  - write the connections after the entities, with external IDs of the same batch;
  - apply the limits per list, the strategies and atomicity;
  - mark connection results with `itemType=connection` and error paths `connections[i]`.

  Verify with integration tests for a mixed batch, an atomic batch with a faulty connection, and partial success.
- [x] 3.4 `createConnection` in a controller that implements the generated operation (201 with `ConnectionDetail`, 404, 422). Verify with MockMvc tests.
- [x] 3.5 Cucumber:
  - `features/metamodel/connection-metatypes.feature` with the scenarios of `metamodel/connection-metatypes` (US-036 AC4 included);
  - `features/repository/connection-write.feature` with those of `repository/connection-write` (US-036 AC1–AC3);
  - change the scenario "Batch with connections" in `entity-batch-write.feature` and add "Dry run".

  The new steps use an existing shared instance (no further running instance). Verify with the `CucumberIT` run.

## 4. Web portal

- [x] 4.1 `MetatypeDialog` (D6): add the connection switch on creation, the source and target multi-selects, and base types of the same kind. The list shows a connection badge, and the detail shows the allowed endpoints. Extend the fake API and the messages in English and German. Verify with specs for creating `Contains`, the badge and "any". Update the SCR-030 story with a connection metatype and run `npm run test:stories`.

## 5. Manual and documentation

- [x] 5.1 `manual/administration/metamodel-editing.md` (connection types, relations as connection types, allowed endpoints with subtypes) and `manual/usage/entities-batch.md` (connections in the batch, `createConnection`, error codes, fixed endpoints, default name), each in English and German. Verify with `python3 scripts/check_manual.py` and the manual build.
- [x] 5.2 `docs/data-model.puml` (`entity_connections` from V19, notes on the constraints) and `business-objects/metamodel-configuration.md` and `business-objects/entity.md` (connection rules).
- [x] 5.3 US-036 (status and implementation), REQ-036, US-141 (AC2), US-032 (AC1 relation) and the sprint plan with the cut of S8 (D7). Regenerate the traceability matrix. Verify with `validate_links.py` and `generate_traceability.py --check`.

## 6. Verification

- [x] 6.1 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
