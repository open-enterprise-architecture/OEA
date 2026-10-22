# Design

## Context

- **Request size**: only the package import is limited, by `PackageSizeLimitFilter` (registered after the security filter chain for `POST /admin/metamodel/import`, default 10 MB, 413 `package-too-large`; a stream beyond the limit throws `PackageTooLargeException` inside the JSON parser, which `ApiExceptionHandler` maps to 413). All other bodies are parsed completely by Jackson before any validation.
- **Problem responses**: `ApiExceptionHandler` maps the framework exceptions of contract violations (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `HandlerMethodValidationException`, `ConstraintViolationException`, `MethodArgumentTypeMismatchException`) by comparing the request path in 8 places: the entity batch answers 422 with `errors` (code `CONTRACT_VIOLATION`), `POST /catalogs` 422 with `invalidFields`, the package import 422 `invalid-package` with `pointer` or `location` and an audit event `metamodel.package-rejected`, the catalog query 400 with `invalidFields`, everything else 400 `validation-failed`. Application exceptions (`InvalidCatalogException`, `BatchRejectedException`, …) are already typed and need no change.
- **Tests**: `OeaDatabases.freshMigrated()` starts a new provisioned PostgreSQL container and runs `oea migrate`; 26 test classes and step definitions use it (≈ 119 container starts per `./mvnw verify`). Keycloak is already a JVM singleton (`OeaKeycloak.started()`). The CI job `backend` runs `./mvnw verify` (surefire, then failsafe with `CucumberIT`, ≈ 8.5 minutes).
- The provisioning script `deploy/postgres/initdb/10-oea-roles.sh` creates the cluster-wide roles idempotently and sets database-level privileges (`REVOKE ALL ON DATABASE … FROM PUBLIC`, `GRANT CONNECT …`) and schemas for the current database; these database-level privileges are not copied by `CREATE DATABASE … TEMPLATE`.

## Goals / Non-Goals

**Goals:**

- One mechanism for request-body limits that also covers the package import
- Problem responses chosen by the operation, not by the request path; identical responses for all existing operations
- Backend CI under 8 minutes wall time with at most a handful of PostgreSQL containers per JVM

**Non-Goals:**

- Rate limits (#74), new problem formats, parallel test execution inside one JVM

## Architecture Constraints

- ADR-013: the 413 response is added to the contract first; generated interfaces stay the source of the handler signatures
- ADR-028: the size filter and the problem mapping live in `io.oea.config.web` and `io.oea.api`; the app layer is unchanged
- ADR-030 / ADR-016: tests keep running against real PostgreSQL, provisioned by the production script; ADR-037: database roles only from provisioning, never from tests on the shared server
- ADR-029: Cucumber stays on failsafe (`CucumberIT`)

## Decisions

### D1 – One request-size filter with configured limits

`RequestSizeLimitFilter` (generalized from `PackageSizeLimitFilter`) is registered once for `/api/v1/*` after the security filter chain, so unauthenticated requests get 401 first. Limits come from configuration: `oea.web.request-size.default` (1 MB), `oea.web.request-size.entity-batch` (10 MB, path of `EntityBatchController.BATCH_PATH`) and the existing `oea.metamodel.import.max-size` (10 MB, problem type `package-too-large`). A declared `Content-Length` above the limit is answered 413 before the body is read; a body without declared length is counted while it is read and throws `RequestTooLargeException` (carries the problem type), which `ApiExceptionHandler` maps to 413 wherever it surfaces (also wrapped in `HttpMessageNotReadableException`). Requests without body (GET, DELETE) pass unchanged.

*Alternatives*: Tomcat `maxPostSize`/`max-swallow-size` – apply only to form posts or after the response; a Jackson `StreamReadConstraints` document limit – counts tokens/characters, not bytes, and reports the error only after parsing started. Rejected.

### D2 – Contract-violation responses declared at the operation

A method annotation `@ContractViolationResponse` in `io.oea.api` declares how an operation answers a contract violation: `status` (400 or 422), problem `type`, `title`, `detail` and the `shape` – `INVALID_FIELDS` (default), `BATCH_ERRORS` (entity batch) or `PACKAGE_POINTER` (package import, including the audit event `metamodel.package-rejected`). The exception handlers of `ApiExceptionHandler` take the `HandlerMethod` argument that Spring passes to `@ExceptionHandler` methods and read the annotation; without annotation the current default (400 `validation-failed` with `invalidFields`) applies. The annotations go on `EntityBatchController.batchWriteEntities`, `CatalogController.createCatalog` (422) and `AdminController.importMetamodelPackage`; the catalog query uses the default. `isCatalogs`, `isCatalogQuery`, `isPackageImport` and the `BATCH_PATH` comparison are removed.

*Alternatives*: controller-local `@ExceptionHandler` methods – `CatalogController` has two styles, so a per-method switch would remain and the field extraction would be duplicated; keeping the path branches – the finding F2. Rejected.

Compatibility is checked by the existing tests, which already assert status, problem type and fields of every case; a test asserts that `ApiExceptionHandler` no longer reads the request URI.

### D3 – Shared PostgreSQL server with databases cloned from a migrated template

`OeaPostgres.shared()` starts one provisioned PostgreSQL container per JVM; on first use the database `oea` is migrated with `oea migrate` and copied to the template `oea_template`. `OeaDatabases.freshMigrated()` returns an `OeaTestDatabase` handle instead of a container: it creates `oea_<n>` with `CREATE DATABASE … TEMPLATE oea_template`, runs the provisioning script for that database (database-level privileges and schemas; roles exist already) and exposes `getJdbcUrl()`, `asSuperuser()`, `asRole()` and `close()` (drops the database). The 26 users switch to the handle; their assertions stay unchanged.

Own containers remain for `MigrationStepTests` and `DatabaseProvisioningTests` (they test provisioning and migration on an empty server) and `DatabaseStartupChecksTests` (other PostgreSQL version, changes role privileges that are cluster-wide).

*Alternatives*: truncating tables between tests – the audit tables are append-only for the runtime role and hash-chained, so a reset would need the owner role and break the chain semantics; one container per test class kept but started in parallel – still ≈ 119 starts. Rejected.

### D4 – Two parallel CI jobs

The job `backend` is split into **Backend unit and integration tests** (`./mvnw -B -ntp verify -DskipITs`) and **Backend Cucumber** (`./mvnw -B -ntp verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`), both with the Maven cache and the Testcontainers start-time summary. Each job keeps `timeout-minutes: 30`. The required status checks of the branch protection change from "Backend build and tests" to the two new names (repository owner, after the merge).

## Data Model

No change.

## API

Delta to `api/openapi.yaml`:

- New shared response `PayloadTooLarge` (413, `Problem`, description: request body larger than the configured limit, problem type `urn:oea:problem:request-too-large`)
- `'413': { $ref: '#/components/responses/PayloadTooLarge' }` on every operation with a `requestBody` that has no 413 yet; the package import and the analytics query keep their own 413 descriptions

## Risks / Trade-offs

- [A refactored problem mapping changes a response unnoticed] → every existing branch is covered by tests that assert status, type and fields; they must pass unchanged
- [Tests on a shared server interfere through cluster-wide objects (roles, settings)] → only tests that do not change roles use the shared server; the provisioning script runs per database; `DatabaseStartupChecks` still verifies the runtime role's privileges at every application start
- [Template copy fails while a connection to `oea_template` is open] → the template is only connected to during its creation; the helper closes that connection before the first clone
- [1 MB is too small for a legitimate request] → largest regular bodies (catalog, provider, mapping) are a few KB; the limit is configurable and documented in `docs/operations.md`
- [Branch protection still requires the old check name] → the PR description names the new checks; the owner updates the protection rule

## Open Questions

- none
