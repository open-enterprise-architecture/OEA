# Design

## Context

- **Paths:** `api/openapi.yaml` has 20 paths under `/metamodel` and 4 under `/admin/metamodel`, all four tagged `Admin` and implemented by `AdminController`:
  - `exportMetamodelPackage` (`GET /admin/metamodel/export`);
  - `importMetamodelPackage` (`POST /admin/metamodel/import`);
  - `listStarterPackages` (`GET /admin/metamodel/starter-packages`);
  - `importStarterPackage` (`POST /admin/metamodel/starter-packages/{packageId}/import`).
- **Path-based configuration:**
  - `RequestSizeConfiguration.IMPORT_PATH` (`/api/v1/admin/metamodel/import`) selects the package body limit `oea.metamodel.import.max-size` (default 10 MB, problem type `package-too-large`); every other path except the entity batch has the default limit of 1 MB.
  - The rate limits (`RateLimitFilter`) are per principal on every path; only the batch limit names a path (`BATCH_PATH`, the entity batch). Nothing names the package paths.
  - Authorization comes from the contract at runtime (`ContractAuthorizationRules`, `AuthorizationInterceptor`) by operation, so it follows the new paths without configuration. `SecurityConfiguration` names no package path.
- **Callers of the old paths:** `RequestSizeConfiguration`, 34 backend test classes and Cucumber steps (among them `RequestSizeLimitTests`, `BatchRateLimitTests`, `RepositoryInstance`, `PackageImportSteps`, `StarterPackageSteps`), `frontend/src/api/metamodel.ts` and `StarterConfigurationCard.spec.ts`, the manual pages "Metamodel packages" and "Operations" (en, de), ADR-026, REQ-032, REQ-033, REQ-035, REQ-058, REQ-059, US-032, US-033, `docs/walking-skeleton.md` and `docs/data-model.puml`. The main specs in `openspec/specs/` name no path.
- **Redocly:** CI runs `redocly lint api/openapi.yaml` with `@redocly/cli@2.63.0` from the repository root. There is no `redocly.yaml`; the default `recommended` ruleset applies, and `.redocly.lint-ignore.yaml` suppresses 5 problems.
- **Contract limits are enforced:** the generator runs with `useBeanValidation`, so `maxLength` becomes `@Size` on the generated model and is checked before the handler. A violation is answered by `ApiExceptionHandler.contractViolation` (400 `validation-failed` with `invalidFields`, or the contract-violation shape declared for the operation), not by the domain validation of the operation.
- **Authorization check:** `scripts/check_openapi_authorization.py` and its test run in CI. The script uses only the Python standard library and a line-based scan; CI installs no YAML library.
- **Measured on 2026-11-19 (230 operations, 43 planned):**
  - 27 implemented and 20 planned operations without `description`. With `operation-description: error`, Redocly 2.63.0 reports exactly these 47 errors and nothing else; the 5 ignored problems stay ignored.
  - 17 operations with an inline path parameter `id`:
    - 14 implemented with `{ type: string, format: uuid }`, identical to `UuidIdParam`: `getSolution`, `getSolutionLandscape`, `getSolutionLandscapeEntity`, `previewSolutionGoLive`, `goLiveSolution`, `listSolutionOpenIssues`, `resolveSolutionOpenIssues`, `resolveSolutionDeltaConflicts`, `getSolutionDiff`, `listSolutionDeltas`, `createSolutionDelta`, `updateSolutionDelta`, `deleteSolutionDelta`, `getCatalog`;
    - 3 planned that are not UUIDs: `getShapePalette` and `updateShapePalette` (viewpoint key, `type: string`), `getAuditEventChanges` (`type: integer`).
  - 9 response schemas with the paging member `page` (`PageMeta`): `items` in `MetamodelChangePage`, `BrowserItemPage`, `SolutionLandscapePage`, `SolutionDeltaList` and `AccessReview`; other names in `CatalogQueryResult` (`rows`), `SolutionDiff` (`entries`), `EntityHistoryResponse` (`versions`) and `ConformanceResult` (`violations`, planned operation `getConformanceResult`).
  - 260 string properties reachable from the request bodies of POST, PUT and PATCH: 74 declare `maxLength`, 112 are exempt (`enum`, `const` or a format), 74 have neither, 63 of them reachable from implemented operations (among them `EntityWrite`, `MetatypeWrite`, `PropertyDefinitionWrite`, `AuthProviderWrite`, `SetupRequest` and the package document `MetamodelPackage`).

## Goals / Non-Goals

**Goals:**

- One path root for the metamodel.
- Every operation described.
- `UuidIdParam` for every UUID `id`.
- Rules that keep descriptions, shared `id` parameters, string limits and envelope names for new operations and schemas.

**Non-Goals:**

- Behavior changes, including a changed error response.
- Renaming existing envelopes.
- New limits, or `maxLength` on existing request properties.

## Architecture Constraints

- **ADR-013:** the contract first. The generated server interfaces and client types follow `api/openapi.yaml`, and the contract changes before the code.
- **ADR-032:** `x-oea-authorization` stays unchanged on the moved operations; the authorization check passes with the same counts.
- **ADR-026:** names the package paths and gets a note of 2026-11-19 for the new root.

## Decisions

### D1 Move the package paths under `/metamodel`

| Old path | New path |
|---|---|
| `/admin/metamodel/export` | `/metamodel/export` |
| `/admin/metamodel/import` | `/metamodel/import` |
| `/admin/metamodel/starter-packages` | `/metamodel/starter-packages` |
| `/admin/metamodel/starter-packages/{packageId}/import` | `/metamodel/starter-packages/{packageId}/import` |

- The tag `Admin`, the `operationId`s, `x-oea-authorization` (`metamodel:write`), responses and limits stay. Keeping the tag keeps the operations in the generated `AdminApi` and in `AdminController`, and in the manual reference page "Admin".
- The new paths do not collide with the existing `/metamodel/...` paths: no existing path has a template in the second segment (`/metamodel/labels/{locale}` and the `{id}` paths sit below fixed segments). Redocly 2.63.0 lints the moved contract without a new problem (checked on 2026-11-19).
- `RequestSizeConfiguration.IMPORT_PATH` becomes `/api/v1/metamodel/import`. No other path-based configuration names these paths (see Context).
- The old paths have no handler and answer like any unknown path under `/api/v1`. There is no alias.
- **Verification:**
  - the existing tests of the four operations, moved to the new paths, keep their results: export 200, import 200, 409 `package-conflict` and 413, starter package list 200 and import 200, and 403 without `metamodel:write`;
  - for each old path, an authenticated request with a valid CSRF header gets the answer of an unknown path (404) and changes no state;
  - `RequestSizeLimitTests` on the new import path: a package at the configured package limit is accepted, one byte above it is rejected with 413 `package-too-large`; the percent-encoded new path keeps the package limit.
- **Alternative:** move the whole metamodel under `/admin/metamodel`. It was rejected: 20 paths move instead of 4, and `listArchitectureLayers` and the metatype reads serve readers with `entity:read`, who are not administrators.

### D2 Descriptions

- Each of the 47 operations without a `description` gets one or two factual sentences, taken from its behavior: permission conditions not visible in `x-oea-authorization`, error cases and paging. Planned operations are included because `operation-description` cannot exclude them.
- A new `redocly.yaml` at the repository root holds `extends: [recommended]` and `rules: { operation-description: error }`. Redocly reads it and the existing `.redocly.lint-ignore.yaml` from the root, as CI runs it; the CI command does not change.
- The descriptions of implemented operations appear in the generated API reference of the manual (`npm run generate:api` in `manual/`), en and de, in English as before.

### D3 Shared parameter for `id`

- The 14 inline UUID parameters `id` become `$ref: '#/components/parameters/UuidIdParam'`; none of them carries a description.
- The 3 planned non-UUID parameters stay inline and are allowlisted (reason: not a UUID; the shared parameter is chosen when the operation is implemented).
- **The rule:** a path parameter named `id`, at path or operation level, is a `$ref` to a component parameter. Redocly resolves references before configurable rules run, so the rule is checked in the source by a script:
  - `scripts/check_openapi_conventions.py` uses only the Python standard library, like the authorization check. It reads a JSON bundle of the contract that keeps the references: `redocly bundle api/openapi.yaml --ext json -o <file>` (checked on 2026-11-19: the bundle keeps all 64 references to `UuidIdParam`).
  - It has a test `scripts/test_check_openapi_conventions.py` with a positive and a negative example per rule, an exempt and an allowlisted example, and a stale allowlist entry.
  - It runs in the CI job `openapi-lint` after the Redocly lint, which already installs Redocly: a bundle step, the script on the bundle, and the test.
- **Allowlists:** every allowlist of the script holds the reason per entry, and the script fails on an entry that no longer matches a violation (the element is gone or now follows the rule), so the allowlists only shrink.

### D4 String limits in request bodies

- **Measured scope:** 74 request properties without a limit, 63 of them on implemented operations (see Context). Since the contract limit is enforced by bean validation before the handler, adding `maxLength` to an existing property changes the answer to an over-long value (status or problem body) where the domain validation answers today, and introduces a limit where the server enforces none. Both are behavior changes and out of scope.
- **Decision:** the rule is introduced with the existing gaps as a baseline; no `maxLength` is added to existing properties in this change.
- **The rule:** every `type: string` property reachable from a request body (`requestBody` of POST, PUT and PATCH, following `$ref`, `allOf`, `oneOf`, `anyOf`, `items` and `additionalProperties`) declares `maxLength`.
- **Exempt properties:**
  - properties with `enum` or `const`;
  - properties with a `format` of `uuid`, `date`, `date-time`, `uri`, `email` or `binary`.
- **Baseline allowlist:** the 74 existing gaps, keyed by component schema and property path (or `operationId` and property path for an inline schema), grouped by reason, for example "package document, limited by the package size", "value typed by its property definition", "limit enforced by the domain validation; aligning it changes the error response", "no limit enforced". New properties and new schemas are not allowlisted.
- **Follow-up:** the pull request lists the 63 gaps on implemented operations; aligning them, each with its error response, is a follow-up issue.
- **Verification:** the script's test covers a property with `maxLength` (accepted), without (rejected), one reached only through `allOf` and one through `items` (rejected without limit), with `enum` and with `format: uuid` (exempt), an allowlisted one (accepted) and a stale allowlist entry (rejected).

### D5 Envelope names

- **The rule:** a component schema with the paging member `page` (`PageMeta`) has an array property `items`. Other arrays next to it (`actors`, `openIssues`, `columns`, `joins`) are allowed.
- The allowlist holds `CatalogQueryResult` (`rows`), `SolutionDiff` (`entries`), `EntityHistoryResponse` (`versions`) and `ConformanceResult` (`violations`, planned). This is part of `check_openapi_conventions.py`.
- **Verification:** the test covers a new envelope with `items` (accepted), with `rows` (rejected) and an allowlisted one (accepted).

### D6 Documentation

- **The manual:** the pages "Metamodel packages" and "Operations" in en and de; the API reference (`manual/api/reference/` and `manual/de/api/reference/`) regenerated from the contract.
- **ADR-026:** a note of 2026-11-19 on the path root.
- **Realigned paths:** REQ-032, REQ-033, REQ-035, REQ-058, REQ-059, US-032, US-033, `docs/walking-skeleton.md` and `docs/data-model.puml`.
- **The review document:** API-7 and API-8 resolved on 2026-11-19, with the baseline of D4 named.
- **The sprint plan:** the status of change 8.
- **The traceability matrix:** regenerated if needed.
- The security reviews of 2026-10 and the archived changes keep the old paths as history.

## Data Model

No change. `docs/data-model.puml` only changes path names in comments.

## API

- The four paths move (D1).
- 47 descriptions are new (D2).
- 14 `id` parameters become references (D3).

No operation, schema property or limit is added or removed.

## Risks / Trade-offs

- **[Risk]** A missed old path in a client or test.
  - **Mitigation:** search the repository for `admin/metamodel` after the change. Only the review document, the security reviews of 2026-10, the archived changes and changelogs may still name it.
- **[Risk]** The import body limit no longer applies to the import path if `IMPORT_PATH` is missed.
  - **Mitigation:** `RequestSizeLimitTests` runs on the new path with both sides of the package limit.
- **[Trade-off]** 74 request properties keep no contract limit for now, and old envelope names remain. They are baselined with reasons, the baselines can only shrink, and new properties and envelopes follow the rules.
