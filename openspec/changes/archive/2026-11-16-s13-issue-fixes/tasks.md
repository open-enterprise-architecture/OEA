# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml`:
  - the 409 of `importStarterPackage` and `importMetamodelPackage` as `application/problem+json` with `package-conflict` and `conflictList` (D3);
  - the limits of `SolutionDeltaWrite` and `SolutionDeltaUpdate` (D4);
  - `getOwnPasswordRules` with `PasswordRules`, and the list of operations allowed during a password change in `Forbidden` (D5);
  - the 422 naming `solutionId` of `createMetamodelConfiguration` (D6).

  Verify: Redocly lint and both authorization scripts pass.

## 2. #256 – redaction without an entity

- [x] 2.1 `PropertyAccess.Snapshot.readableWithoutEntity`, and its use in `SolutionDeltas` (list, capture response) and in `SolutionDiff` for deltas `new` (D1).

  Verify with `PropertyAccessTests`, `SolutionDeltasTests` and the diff tests:
  - a `connection-scoped` value is redacted for a person with an account link and returned for an override holder (with `property-override-access`);
  - a public value is returned;
  - a stored default of a `role-restricted` property is redacted without a read role and returned with one;
  - the diff redacts the same value.

## 3. #263 T-1 – limits on the decoded path

- [x] 3.1 Test first: `/api/v1/entities/b%61tch` above the batch rate limit and above the batch size limit; it fails before the fix. Then `RequestPaths.normalized` in both filters (D2).

  Verify:
  - the encoded path gives 429 and 413;
  - the plain path still gives 429 and 413 at the same limits and is accepted below them;
  - another encoded path is not counted as batch.

## 4. #262 – contract clean-up

- [x] 4.1 API-3: the conflict problem in `AdminController`; `api/metamodel.ts` reads `conflictList` from the problem (D3).

  Verify with the package import tests and `metamodel.spec.ts`:
  - a conflict gives 409 `application/problem+json` with `package-conflict` and `conflictList`, and stores nothing;
  - the screen lists the conflicts.
- [x] 4.2 API-5: delta limits (D4).

  Verify the boundaries: a name of 255 characters is accepted and 256 rejected; a description of 2000 is accepted and 2001 rejected, both with the declared status.
- [x] 4.3 API-6: `getOwnPasswordRules`, `AuthorizationInterceptor`, `api/session.ts` and `ChangePasswordPage.vue` (D5).

  Verify with controller tests and `ChangePasswordPage.spec.ts`:
  - the rules name the minimum length 14 of the policy;
  - they are allowed during a required change;
  - 403 without a local password;
  - the form accepts 14 characters and names the length rule for 13;
  - it names a missing digit;
  - it still checks the repetition.
- [x] 4.4 Cucumber: the scenarios of `metamodel/package-import` and `identity/password-policy` in their features. Verify that they pass.

## 5. #237 – extensions name an existing solution

- [x] 5.1 `V34__metamodel_extension_solution.sql` with the orphan check and the foreign key; `MetamodelConfigurations.create` with the 422 (D6). The US-037 tests create their solution first.

  Verify:
  - the migration test aborts with the name of an orphaned extension and leaves the schema unchanged;
  - without one, it adds the key;
  - an unknown solution gives 422 naming `solutionId` and stores nothing;
  - `solution-extensions.feature` and `lock-mode.feature` pass.

## 6. Documentation and wrap-up

- [x] 6.1 `docs/data-model.puml` (foreign key); user manual for the password change and the package import in English and German; a resolution note in `security-reviews/application-audit-2026-11.md` (AA-02, T-1) and `docs/reviews/architecture-2026-11.md` (API-3, API-5, API-6).
- [x] 6.2 S13 row of `docs/sprint-plan.md`. The implementation PR closes #256 and #237 and comments on #262 and #263 with what remains open.
- [x] 6.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.
