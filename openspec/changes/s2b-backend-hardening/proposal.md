# Proposal

## Why

The walking-skeleton retrospective and the security re-review found three backend weaknesses that grow with every further change: the shared exception handler decides problem responses by request path (F2), an authenticated principal can exhaust heap memory with an oversized JSON body (WS-02, issue [#73](https://github.com/open-enterprise-architecture/OEA/issues/73)), and the backend CI job takes 11–14 minutes because the tests start about 119 PostgreSQL containers per run (F3). Package S2b resolves them before S3 adds further operations and tests.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (technical package, no user goal) |
| User Stories | – |
| Requirements | [REQ-186: Rate limits and resource quotas](../../../requirements/req/REQ-186-rate-limits-resource-quotas.md) (request-size limit; rate limits follow in #74), [REQ-184: Safe import parsing](../../../requirements/req/REQ-184-safe-import-parsing.md) (package limit kept), [REQ-187: HTTP security baseline](../../../requirements/req/REQ-187-http-security-baseline.md) (generic problem responses) |
| ADRs | [ADR-013](../../../adrs/ADR-013-api-style.md) (spec-first contract), [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md) (layers), [ADR-029](../../../adrs/ADR-029-cucumber-test-framework.md) (Cucumber), [ADR-030](../../../adrs/ADR-030-postgresql-only.md) (PostgreSQL with Testcontainers) |
| Findings | [Retrospective](../../../docs/retrospectives/2026-10-walking-skeleton.md) A2 (F2) and A3 (F3); [security re-review](../../../security-reviews/walking-skeleton-2026-10.md) WS-02 |
| Sprint / Milestone | S2b / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name follows the package (`s2b-`) instead of a user story, like `ws-01-project-setup`, because it implements retrospective actions and a security finding, not a story.

## What Changes

- **Request-size limit** (WS-02, #73): every request with a JSON body is limited before it is parsed; a declared length above the limit and a body that grows beyond it are answered with 413 and the problem type `urn:oea:problem:request-too-large`. The limits are configurable, with larger defaults for the entity batch and the existing package import limit kept. The contract declares the 413 response on every operation with a request body.
- **Problem responses without request-path branches** (A2): `ApiExceptionHandler` no longer compares request paths; how a contract violation (invalid body, parameter or JSON) is answered – status, problem type, field list or pointer – is declared at the operation and read from the handler method. Every existing problem response stays byte-for-byte compatible (status, `type`, `invalidFields`, `errors`, `pointer`, `location`).
- **Faster backend tests** (A3): tests that need an own database get a fresh database cloned from a migrated template on one shared PostgreSQL container per test JVM instead of a new container; the few tests that need an own server (other PostgreSQL version, provisioning, migration step) keep it. The CI backend job is split into a unit/integration job and a Cucumber job that run in parallel.

## Out of Scope

- Per-principal rate limits of REQ-186 (issue #74, S3)
- Auditing of permission denials (WS-01, issue #75, S3)
- Changes of problem formats or status codes; the refactoring of A2 keeps them as they are
- Frontend and CI actions A1, A4, A5, A9 (second S2b change)

## Capabilities

### New Capabilities

- `platform/request-limits`: limits that protect the instance against oversized or excessive requests – first the size of request bodies; the rate limits of REQ-186 extend it later

### Modified Capabilities

- none (the problem responses of existing operations do not change)

## Impact

- `backend/`: `io.oea.config.web` (general request-size filter replacing `PackageSizeLimitFilter`), `io.oea.api.ApiExceptionHandler` and the controllers with own validation responses (`EntityBatchController`, `CatalogController`, `AdminController` for the package import), test support in `io.oea.testsupport.containers` and every test that uses `OeaDatabases.freshMigrated()`
- `api/openapi.yaml`: shared response `PayloadTooLarge` on every operation with a request body
- `.github/workflows/ci.yml`: backend job split; the required status checks of the branch protection must be updated by the repository owner after the merge
- Docs: REQ-186 (request-size row), `docs/operations.md` (new settings), retrospective/issue #73 references
- No Flyway migration, no data model change
