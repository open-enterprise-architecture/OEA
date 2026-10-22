# Tasks

## 1. Test infrastructure (A3)

- [ ] 1.1 Add the shared provisioned PostgreSQL server with the migrated template `oea_template` and the database handle of D3 (`OeaPostgres.shared()`, `OeaDatabases.freshMigrated()` returning `OeaTestDatabase` with `getJdbcUrl()`, `asSuperuser()`, `asRole()`, `close()`; provisioning script run per cloned database); verify with `TestInfrastructureTests` that a cloned database has the database-level privileges of the provisioning (no `CONNECT` for `PUBLIC`, `CONNECT` for the OEA roles), the schemas and the migrated version, that two cloned databases are independent, and that the application starts against a clone (startup checks of `DatabaseStartupChecks` pass)
- [ ] 1.2 Switch the 26 users of `freshMigrated()` (test classes, Cucumber step definitions, `OidcTestInstance`) to the handle; keep own containers for `MigrationStepTests`, `DatabaseProvisioningTests` and `DatabaseStartupChecksTests`; verify `./mvnw verify` passes unchanged in test count and that the run starts at most 5 PostgreSQL containers (count in the build log)
- [ ] 1.3 Split the CI job `backend` into "Backend unit and integration tests" (`-DskipITs`) and "Backend Cucumber" (`-Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`) as in D4, both with Maven cache and the Testcontainers summary; verify both jobs pass in the PR, the slower one finishes in under 8 minutes, and actionlint/zizmor report no findings for `ci.yml`

## 2. Problem responses declared at the operation (A2)

- [ ] 2.1 Add `@ContractViolationResponse` (D2) and read it in the contract-violation handlers of `ApiExceptionHandler` through the `HandlerMethod` argument; annotate `EntityBatchController.batchWriteEntities` (`BATCH_ERRORS`, 422), `CatalogController.createCatalog` (`INVALID_FIELDS`, 422) and `AdminController.importMetamodelPackage` (`PACKAGE_POINTER`, 422 with the rejection audit); remove `isCatalogs`, `isCatalogQuery`, `isPackageImport` and the `BATCH_PATH` comparison; verify that all existing tests of these responses pass unchanged (`EntityBatchTests`, `CatalogTests`, `CatalogQueryTests`, `PackageImportTests`, the Cucumber features of these capabilities) and with a unit test that `ApiExceptionHandler` does not read the request URI

## 3. Request-size limit (WS-02, #73)

- [ ] 3.1 Extend `api/openapi.yaml` with the shared response `PayloadTooLarge` and the 413 response on every operation with a request body (API section of the design); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds
- [ ] 3.2 Replace `PackageSizeLimitFilter` by `RequestSizeLimitFilter` with the limits of D1 (`oea.web.request-size.default`, `oea.web.request-size.entity-batch`, `oea.metamodel.import.max-size`) and map `RequestTooLargeException` to 413 in `ApiExceptionHandler`; verify with integration tests for the spec scenarios "Oversized batch with declared length", "Oversized body without declared length", "Body within the limit", "Limit configured by the operator", "Oversized package" and "Oversized body without session"
- [ ] 3.3 Write the Cucumber feature `platform/request-limits.feature` for the `platform/request-limits` spec (oversized batch with declared length, body within the limit, oversized body without session) on the shared instance with `enterprise-architect`; verify it passes through `CucumberIT`

## 4. Wrap-up

- [ ] 4.1 Add the request-body limits to REQ-186 (row "Request body size": 1 MB default, 10 MB entity batch and package import) and the settings to `docs/operations.md`; note in issue #73 and the retrospective (A2, A3) that the change resolves them; verify markdown lint and link validation pass
- [ ] 4.2 No user story is referenced, so no story status changes; `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` are unchanged; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 4.3 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check` and `./mvnw verify` and verify all pass; record the measured CI times of both backend jobs in the PR
