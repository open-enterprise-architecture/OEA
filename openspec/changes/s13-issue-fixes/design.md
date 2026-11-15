# Design

## Context

Four open issues are fixed in one change. Each touches a different module, so the decisions are per issue.

- **#256:** `SolutionDeltas.values` decides readability with `PropertyAccess.Snapshot.readable(property)`. For a `connection-scoped` property, that method answers `true` for any caller with an account link, because the scope is checked per entity, and a delta `new` has no entity.
- **#263 T-1:** `RateLimitFilter` and `RequestSizeLimitFilter` compare `request.getRequestURI()` (raw, not decoded) with `/api/v1/entities/batch`.
- **#262 API-3:** the package imports answer 409 with `ImportResult` as `application/json`. API-5 and API-6 concern the contract and the form SCR-004.
- **#237:** `metamodel_configurations.solution_id` has no foreign key since `solutions` was created (V25).

## Goals / Non-Goals

**Goals:**

- Close #256 and #237.
- Fix #263 T-1 and #262 API-3, API-5 and API-6, and record the rest of those issues as open.

**Non-Goals:**

- #263 T-2 to T-4, #262 API-7 and API-8.

## Architecture Constraints

- **Layers (ADR-028, ArchUnit):** `api` → `app` → `core`.
- **No request-path branching in `ApiExceptionHandler`:** the conflict answer is built in the controller from the exception.
- **Contract first (ADR-013):** Redocly lint and the authorization scripts pass.

## Decisions

### D1 – Redaction without an entity (#256)

`PropertyAccess.Snapshot` gets `readableWithoutEntity(property)`:

- `true` for a public property;
- `true` through a read role or the override for a `role-restricted` one;
- `true` only through the override for a `connection-scoped` one (`throughOverride`).

`SolutionDeltas.values` uses it, and so do the capture response and `SolutionDiff` for deltas `new` (they read through `values` or the same rule; the implementation checks every call site with `grep readable(`). A value read only through the override writes the existing `property-override-access` event, as on the other read channels.

Defaults stay stored (owner decision). The rule redacts them like any other value.

### D2 – Decoded path for the limits (#263 T-1)

Both filters use `RequestPaths.normalized(request)`, a new helper in `config/web`. It returns `UrlPathHelper.getPathWithinApplication` with URL decoding and the removal of `;` segments, and normalizes `//` and dot segments.

Order of the work:

1. An integration test sends a batch above the limit to `/api/v1/entities/b%61tch` and shows the bypass. The test is first marked `@Disabled` with the issue number, or written red.
2. The fix.
3. The test without the mark.

Paths the firewall of Spring Security already rejects (encoded `/`, `%25`, `;`) stay rejected.

### D3 – Conflict problem document (#262 API-3)

`AdminController` builds the 409 itself:

- a `ProblemDetail` with the type `package-conflict` and `conflictList` from `MetamodelPackages.result(ex)`;
- `batchId` and `labelCount` are left out of the problem.

The contract describes the problem with an extension member `conflictList`. `ImportResult` keeps `conflictList` for the successful result with `completed-with-conflicts`.

The frontend `api/metamodel.ts` reads `conflictList` from the problem.

### D4 – Delta limits in the contract (#262 API-5)

`SolutionDeltaWrite.name` and `SolutionDeltaUpdate.name` get `minLength: 1`, `maxLength: 255`; `description` gets `maxLength: 2000`.

The generated validation then rejects a longer value with 400 before the business check. The current answers stay:

- 422 with `invalidFields` through `@ContractViolationResponse`, where the operation declares it;
- otherwise the existing 400.

The spec scenarios of the boundaries are checked in the existing tests, and adjusted to the status the contract declares.

### D5 – Own password rules (#262 API-6)

`GET /me/password/rules` (`getOwnPasswordRules`, `access: authenticated`) returns `PasswordRules { minLength, maxLength, requireUppercase, requireLowercase, requireDigits, requireSpecialChars }` from `PasswordPolicies`. The maximum length is the fixed 128.

- `AuthorizationInterceptor.PASSWORD_CHANGE_OPERATIONS` gains the operation.
- A principal without a local password, such as an IdP-only person or a service account, gets 403, as `suggestPassword` does.

`ChangePasswordPage.vue` loads the rules once, checks the length and the classes, and names each unmet rule with the texts of the server's rules. It keeps the check of the repetition.

### D6 – Foreign key of extensions (#237)

`V34__metamodel_extension_solution.sql` runs a `DO` block first. If extensions without a solution exist, it raises an exception whose message lists their IDs and names, and the migration aborts. Then it adds `FOREIGN KEY (solution_id) REFERENCES solutions (id)`, with no `ON DELETE`; solutions are not deleted yet.

`MetamodelConfigurations.create` checks the solution first and throws `MetamodelChangeException.invalid("solutionId", …)`, which gives 422.

The US-037 tests (`MetamodelConfigurationsTests`, `solution-extensions.feature`, `lock-mode.feature`) create a solution first.

### D7 – Tests

- **#256:** `SolutionDeltasTests` and the diff tests:
  - a value redacted for an account link;
  - a value read through the override, with its event;
  - a stored default redacted without a read role and shown with one.
- **#263:** `RequestLimitIntegrationTests`:
  - the encoded path above the rate limit gives 429;
  - the encoded path above the size limit gives 413;
  - the plain path keeps working.
- **#262 API-3:** controller and Cucumber tests on the problem document; `metamodel.spec.ts`.
- **API-5:** the boundaries 255/256 and 2000/2001.
- **API-6:**
  - `getOwnPasswordRules` with minimum length 14;
  - allowed during a required change;
  - 403 without a local password;
  - `ChangePasswordPage.spec.ts` with the boundaries 13/14 and a missing digit.
- **#237:**
  - the migration test with and without an orphaned extension;
  - an unknown solution gives 422 naming `solutionId`.

## Data Model

`metamodel_configurations.solution_id` gets `{FK→solutions.id}` in `docs/data-model.puml`.

## Risks / Trade-offs

- **The migration may stop an installation:** an instance with orphaned extensions does not start until the operator resolves them. This is intended: the message names them, and there is no silent loss.
- **Stored defaults stay in the database:** they never reach a reader without permission, and their audit stays encrypted as before.
- **Decoding in a filter:** the helper must match how the dispatcher resolves the path. The test sends the same encoded path through the full filter chain.

## Open Questions

- None.
