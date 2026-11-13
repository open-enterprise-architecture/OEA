# Architecture Review: Module Structure and API First (2026-11)

| Field | Value |
|---|---|
| Review type | Read-only review of the implemented application after sprints S1–S11, requested by the repository owner on 2026-11-13 |
| Reviewer | Solution Architect (agent) |
| Date | 2026-11-13 |
| Branch / commit | `main` @ `1e8ebe46` |
| Scope | Module structure of `backend/src/main/java/io/oea/` and `frontend/src/`; `api/openapi.yaml` as the single contract; parity of the UI and the API; consistency of the API |
| Method | Static review; import graphs and cross-package references by search; comparison with [ADR-012](../../adrs/ADR-012-backend-stack.md), [ADR-013](../../adrs/ADR-013-api-style.md), [ADR-027](../../adrs/ADR-027-project-setup-monorepo.md) and [ADR-028](../../adrs/ADR-028-backend-layered-architecture.md). Every finding was checked in the code |
| Companion | [Security review: application audit (2026-11)](../../security-reviews/application-audit-2026-11.md) |

## Summary

**Module structure**: the layering `api → app → core` is clean and enforced by ArchUnit. The feature packages in `app` are cut sensibly, and writes to shared tables have a clear owner. The weak points:

- ArchUnit does not cover the cross-cutting packages (`config`, `audit`, `integration`) or cycles between features.
- There are three real dependency cycles.
- `app` runs most of its SQL directly, contrary to ADR-028.
- A few classes are too large.
- ADR-027 and ADR-028 no longer describe the actual structure.

**API first**: the API-first approach is consistent:

- Every controller implements only interfaces generated from `api/openapi.yaml`; there is no hand-written request mapping.
- Authorization is read from `x-oea-authorization` in the contract at runtime.
- The frontend uses only contract paths, and no path exists only in the UI.

The weak points:

- The global security schemes promise bearer and OAuth2 authentication, but only the session cookie is implemented.
- Optimistic locking is used inconsistently.
- Some contract details (limits, error format) are inconsistent.

## Measurements

| Measure | Value |
|---|---|
| Files in `app` | 141, of which 1 `public interface` (ADR-028 asks for stable interfaces with commands and queries) |
| `app` classes using `JdbcTemplate` directly | 55 (`core` has 6 store classes) |
| Cross-feature imports in `app` | `access → login` 3, `login → access` 7, `metamodel → access` 3, `access → metamodel` 1, `solution → catalog/entity/metamodel` 11, `security` from every feature (> 110) |
| Contract operations | 187, all with `x-oea-authorization`; 129 implemented, the other 58 are `x-oea-status: planned` and answer 501 (checked by `scripts/check_openapi_authorization.py`) |
| Frontend paths not in the contract | 0 |

## Findings: Module Structure

| ID | Severity | Location | Finding | Recommendation |
|---|---|---|---|---|
| ARCH-1 | High | `backend/src/test/java/io/oea/architecture/LayerRules.java:17` | `consideringOnlyDependenciesInLayers()` leaves out `config`, `audit` and `integration`, so the cycle `app ↔ config` goes undetected: `config/security/SecurityConfiguration.java:3-5` and `config/database/StatementTimeoutConfiguration.java:4` import `app`, while 24 `app` classes import `config.oidc`, `config.webauthn` and `config.secrets`. `api → config` (18 imports) is not regulated either | Rules for `config` and `audit`; move the adapters `OidcClient`, `WebAuthnVerifier` and `SecretCipher` from `config` into an infrastructure package |
| ARCH-2 | High | `app/access/PasswordPolicies.java:3` ↔ `app/login/LocalLogin.java:3`; `app/access/PersonEntityLinks.java:3` ↔ `app/metamodel/PropertyVisibility.java:3` | Feature cycles `access ↔ login` (through `PersonPasswordProperties` and `PasswordPolicies`) and `access ↔ metamodel` (metamodel uses the generic `access.InvalidAccessRequestException`) | ArchUnit rule `slices().matching("io.oea.app.(*)..").should().beFreeOfCycles()`; move the shared exception and the password properties to a neutral package |
| ARCH-3 | Medium | `adrs/ADR-027-project-setup-monorepo.md:64-67,94`, package tree of ADR-028 | The ADRs prescribe application-service interfaces, `command/` and `query/` subpackages, `api/v1/controller` with `dto`, and `integration/BatchWriteService`. The code has concrete service classes, DTOs generated from the contract, an `integration/` package with only `package-info.java`, and `EntityBatch` in `app/entity` | New ADR (or ADR-028 superseded) documenting the feature-package cut in use and the rules for access across features |
| ARCH-4 | Medium | `app/solution/SolutionLandscape.java:78-85`, `app/solution/SolutionDiff.java:237-243`, `app/solution/SolutionDeltas.java:540-605` | The solution module reads `entities`, `entity_property_values` and `metatypes` (owned by `core/entity`) with its own SQL. The value query is duplicated almost verbatim, with two value records (`SolutionDeltas.Value`, `SolutionDiff.Typed`). The same pattern exists in `access` and in `metamodel` (`MetamodelChanges.java:79-103` reads `audit.events`) | A read API in `core/entity` (e.g. values of several entities) shared by solution and diff; an ArchUnit rule that confines `JdbcTemplate` to `core`, or an explicit decision that allows it in `app` |
| ARCH-5 | Medium | `app/catalog/Catalogs.java` (1148 lines, 17 public methods), `core/catalog/CatalogQueryStore.java` (926), `app/solution/SolutionDeltas.java` (700), `api/metamodel/MetamodelController.java` (576, 22 operations) | Oversized classes; `Catalogs` covers catalogs, columns, joins, filters and views | Split `Catalogs` into columns, joins, saved filters and saved views (`CatalogColumns` exists as a pattern); split `SolutionDeltas` into capture and read |
| ARCH-6 | Low | `api/ApiExceptionHandler.java` (560 lines, 41 handlers, imports from 8 `app` packages) | 40 feature-specific runtime exceptions without a common base; every new feature extends the central handler | Common base exceptions (validation with invalid fields, conflict, not found); feature-specific mapping only where the problem type requires it |
| ARCH-7 | Low | `app/login/PasskeyRegistration.java:83,167` | Mixes JPA (`PersonRepository`) and JDBC (`UPDATE persons`) on the same table in one service | One access style per table |
| ARCH-8 | Low | `frontend/src/api/*.ts` | The 9 API modules each define their own JSON headers, `read()` helper and credentials; only `problem.ts` is shared | One shared HTTP client (see API-4) |
| ARCH-9 | Low | `frontend/src/pages/CatalogPage.vue` (612 lines); `frontend/src/components/Catalog*.vue` at the root while `metamodel/`, `solutions/`, `roles/` and `visibility/` are subfolders | Inconsistent feature organization; the catalog page holds URL sync, paging and column mapping | Organize by feature; extract the query state of the catalog page into a composable |

## Findings: API First

| ID | Severity | Location | Finding | Recommendation |
|---|---|---|---|---|
| API-1 | High | `api/openapi.yaml:82-85,135-160`; `config/security/OeaSessionFilter.java` | The contract declares `bearerAuth` and `oauth2ClientCredentials` as global alternatives, but the backend authenticates only the session cookie; `/oauth2/token` and service accounts are `planned`. A client without a browser cannot use the API today, and the contract contradicts the implementation | Mark the schemes as planned in the contract, or declare them per operation; prioritize service accounts and token authentication |
| API-2 | Medium | `updateSolutionDelta`, `deleteSolutionDelta`, `/catalogs/{id}/columns`, `/joins`, `/views`, `/tree-nodes/{nodeId}`, `/admin/property-visibility`, `/metamodel/settings/reimport-policy`, `/metamodel/labels/{locale}` | Of 37 implemented `PUT`, `PATCH` and `DELETE` operations, only the 12 of the metamodel use `If-Match`. Solution deltas are edited by several architects without protection against lost updates | A rule (`If-Match` required on shared mutable resources) with named exceptions (personal catalogs, `me/*`); add it to solution deltas, tree nodes and instance catalogs |
| API-3 | Medium | `api/openapi.yaml:9286,9348`; `api/admin/AdminController.java:225-227,239` | `importStarterPackage` and `importMetamodelPackage` answer 409 with `application/json` `ImportResult` instead of `application/problem+json`, the only deviation from the problem model among the implemented operations | A problem type for package conflicts with the conflict list as an extension member |
| API-4 | Medium | `frontend/src/api/solutions.ts:36-80` and the other modules | Types come from the generated `schema.d.ts`, but paths, methods and query parameters are hand-written strings; a renamed path or parameter is only noticed at runtime | A client typed against the contract paths (e.g. `openapi-fetch`) |
| API-5 | Low | `api/openapi.yaml:2465-2466,2479-2480` | The limits of `SolutionDeltaWrite` and `SolutionDeltaUpdate` (`name` 1–255, `description` ≤ 2000) are stated in prose only, while `SolutionCreate` uses `maxLength`. The backend enforces them (`EntityValidator.java:28-30`) | `minLength` and `maxLength` in the schemas; a lint rule for string limits |
| API-6 | Low | `frontend/src/pages/ChangePasswordPage.vue:29` | The client hard-codes 12–128 characters, while the minimum length is configurable (`PasswordPolicies.java:92,152`). The server checks, so this is a duplicated rule that will drift, not a bypass | Read the limits from the password policy, or check only that the repetition matches |
| API-7 | Low | `/metamodel/*` and `/admin/metamodel/{import,export,starter-packages}` | The metamodel lives under two path roots | One convention before 1.0 |
| API-8 | Low | `api/openapi.yaml` | 24 implemented operations have no `description`; 14 operations define the UUID path parameter `id` inline instead of `UuidIdParam`; page envelopes name their list `items`, `rows`, `entries` or `versions` | Lint rules for operation descriptions and shared parameters; `items` in new envelopes |

## What Is Done Well

- Controllers implement only the generated interfaces; `PlannedOperationsController` answers planned operations with 501, and CI checks that `planned` and 501 match.
- Authorization comes from the contract at runtime (`ContractAuthorizationRules`), so the UI and the API cannot diverge.
- Paging is uniform (`PageParam`, `PageSizeParam`, `CursorParam`, `PageMeta`).
- The API reference of the user manual is generated from the contract, and CI fails on a mismatch; `schema.d.ts` is regenerated before every build, test and type check.
- Client-side checks in the frontend mirror contract limits, and the server enforces all of them; no path exists only in the UI.
- No production code imports test fakes; `api` contains no SQL; transactions are declared in `app`; `EntityValidator` is reused by solution deltas.
- The ArchUnit rules have fixture tests that prove they detect violations.

## Follow-Up Actions

Agreed with the repository owner on 2026-11-13:

1. A hardening change before S12: ArchUnit extension and the three cycles resolved (ARCH-1, ARCH-2), the security schemes of the contract corrected (API-1, first part), and the forwarded client address (AA-01 of the security review).
2. GitHub issues for the remaining findings: concurrency rule and `If-Match` (API-2), the module ADR (ARCH-3), the refactorings (ARCH-4 to ARCH-9), the frontend client (API-4) and the contract clean-up (API-3, API-5 to API-8).
3. Service accounts and token authentication (API-1, second part) as a candidate topic for the sprint planning.
