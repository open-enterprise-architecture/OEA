# Proposal

## Why

The application security audit 2026-11 and the architecture review 2026-11 left findings that are still present in the code:

- [#256](https://github.com/open-enterprise-architecture/OEA/issues/256) (AA-02): values of `connection-scoped` properties in deltas `new` are returned unredacted. This is a sign-off condition before the go-live of solutions (S14).
- [#263](https://github.com/open-enterprise-architecture/OEA/issues/263) (T-1): a percent-encoded path may bypass the batch rate limit and the batch size limit.
- [#262](https://github.com/open-enterprise-architecture/OEA/issues/262), three of its findings:
  - API-3: package conflicts are answered as `application/json` instead of a problem document;
  - API-5: the limits of the delta schemas exist only as prose;
  - API-6: the password form hard-codes 12–128 characters.
- [#237](https://github.com/open-enterprise-architecture/OEA/issues/237): metamodel extensions reference their solution without a foreign key.

This is S13 change 3. The owner chose these issues on 2026-11-15.

## Traceability

| Artifact | Reference |
|---|---|
| Issues | [#256](https://github.com/open-enterprise-architecture/OEA/issues/256), [#263](https://github.com/open-enterprise-architecture/OEA/issues/263) (T-1 only), [#262](https://github.com/open-enterprise-architecture/OEA/issues/262) (API-3, API-5, API-6), [#237](https://github.com/open-enterprise-architecture/OEA/issues/237) |
| Reviews | [Application security audit 2026-11](../../../security-reviews/application-audit-2026-11.md) (AA-02, T-1), [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md) (API-3, API-5, API-6) |
| Requirements | [REQ-160](../../../requirements/req/REQ-160-visibility-on-all-read-channels.md), [REQ-186](../../../requirements/req/REQ-186-rate-limits-resource-quotas.md), [REQ-114](../../../requirements/req/REQ-114-atomic-import-conflict-handling.md), [REQ-028](../../../requirements/req/REQ-028-password-policies.md), [REQ-173](../../../requirements/req/REQ-173-account-recovery-temporary-passwords.md) |
| User Stories | US-040 (deltas `new`), US-027 (password policy), US-037 (solution extensions) |
| Sprint / Milestone | S13 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-15:**

- **#237:** before adding the foreign key, the migration checks for extensions whose solution does not exist. If it finds any, it aborts with a message that lists them, and the operator decides. No data is deleted or invented.
- **#262 API-6:** the password form reads the current password rules through a new endpoint that every person with a local password may call, also while a password change is required. The form checks length and character classes before sending.
- **#256, stored defaults:** defaults of properties the writer may not write stay stored in a delta `new`. They are redacted for every reader who may not read them; the redaction rule below covers this.

**Delta values without an entity (#256, `solution`):**

- A value of a delta `new` has no entity yet. A `connection-scoped` property SHALL therefore be readable there only through an override role. A `role-restricted` one stays readable through a read role.
- This holds for the delta list, the capture response and the diff of the solution.

**Rate and size limits on the decoded path (#263 T-1, `platform`):**

- The batch rate limit and the batch size limit compare the decoded, normalized request path instead of the raw URI.
- `/api/v1/entities/b%61tch` therefore counts as the batch path.
- An integration test first proves the bypass, then its fix.

**Package conflicts as a problem document (#262 API-3, `metamodel`):**

- A rejected package import answers 409 with `application/problem+json`, the problem type `urn:oea:problem:package-conflict`, and the conflicts in the extension member `conflictList`.
- The frontend reads the conflicts from there.

**Delta limits in the contract (#262 API-5):** `SolutionDeltaWrite` and `SolutionDeltaUpdate` declare `minLength`/`maxLength` for `name` (1–255) and `maxLength` for `description` (2000). The behavior is unchanged.

**Own password rules (#262 API-6, `identity`):**

- `GET /me/password/rules` returns the minimum and maximum length and the required character classes of the current policy. It is allowed while the session must change its password, and a principal without a local password gets 403.
- SCR-004 checks against these rules, and the server still checks everything.

**Extensions name an existing solution (#237, `metamodel`):**

- `metamodel_configurations.solution_id` gets a foreign key to `solutions.id`.
- `createMetamodelExtension` answers an unknown solution with 422 naming `solutionId`.
- The US-037 tests create their solution first.

## Out of Scope

- #263 T-2 to T-4 and the go-live question. T-3 and T-4 need repository admin rights, and T-2 needs a measurement.
- #262 API-7 (metamodel path root) and API-8 (Redocly rules, operation descriptions, envelopes).
- #69, #257 (optimistic locking) and the refactoring issues #258 to #261 and #271.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `solution/solution-deltas`: the redaction of `connection-scoped` values of deltas `new`.
- `platform/request-limits`: the batch limits on the decoded path.
- `metamodel/package-import`: the conflict answer as a problem document.
- `identity/password-policy`: reading the own password rules, and the check of SCR-004.
- `metamodel/solution-extensions`: an extension names an existing solution.

## Impact

- **api/openapi.yaml:**
  - the 409 of `importStarterPackage` and `importMetamodelPackage`;
  - the delta schema limits;
  - the new `getOwnPasswordRules` with the schema `PasswordRules`;
  - the 422 of `createMetamodelExtension`;
  - the list of operations allowed during a password change.
- **backend:**
  - `SolutionDeltas`;
  - `RateLimitFilter` and `RequestSizeLimitFilter`;
  - `AdminController` and `ApiExceptionHandler`;
  - `PasswordPolicies`, `IdentityController` and `AuthorizationInterceptor`;
  - `MetamodelConfigurations`;
  - migration `V34__metamodel_extension_solution.sql`.
- **frontend:** `api/metamodel.ts`, `api/session.ts` and `ChangePasswordPage.vue`.
- **Documentation:**
  - the user manual (password change, package import);
  - `docs/data-model.puml`;
  - the security audit and the architecture review get a resolution note;
  - the issues are closed by the implementation PR.
