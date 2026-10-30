# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design (`getPasswordPolicy`, `updatePasswordPolicy`, `resetPersonPassword`, `suggestPassword`, `InitialPassword.generate`, generated password in `InitialPasswordResult`, problem types); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V11__password_policy.sql` (D1); verify with a schema test that the CHECKs reject a minimum length below 12, an expiry above 3650 and a history above 24, and that the defaults hold on a fresh database

## 2. Policy and generator (US-027, US-026)

- [x] 2.1 Add `PasswordPolicyFloors` (startup validation), `PasswordPolicies` (effective policy, `check` with history, D1/D3) and `PasswordGenerator` (D5); verify with unit tests for every rule including all ASCII special characters, the effective policy of floor and stored values, the history (current and previous passwords), 1000 generated passwords (compliant, special character present, all different) and the unsatisfiable policy
- [x] 2.2 Add `getPasswordPolicy` and `updatePasswordPolicy` with the switch field (D2); use the policy in `PersonPasswords`, `PasswordChange` and `LocalLogin` (switch, expiry D4) and add `suggestPassword` and `generate` of `setInitialPassword`; verify with integration tests for "Floor stricter than the stored policy", "Policy changed", "Below a floor", "Several unmet rules", "Default policy", "Expired password" (clock advanced), "Reused password", "Suggestion for the own change", "Generated initial password", "Switched off by an administrator", the switch not switchable on without the operator permission, and that the tests of `us-011-local-password` pass
- [x] 2.3 Cucumber scenarios in `identity/password-policy.feature` (new) for US-027 and US-026; verify they pass through `CucumberIT`

## 3. Reset (US-030)

- [x] 3.1 Add `PasswordResets` and `resetPersonPassword` (D6); verify with integration tests for "Password reset" (two sessions revoked, old password refused, required change, audit without password), "Reset of an administrator by another administrator", "Reset of an administrator by the system admin account", the own person (403), a person without password (409) and a rollback when storing the new credential fails
- [x] 3.2 Cucumber scenario in `identity/password-reset.feature` (new) for US-030 AC1–AC4; verify it passes through `CucumberIT`

## 4. Frontend and screens (D7)

- [x] 4.1 Add the suggestion and the rule messages to `ChangePasswordPage.vue` and `api/session.ts`; verify with Vitest cases (suggestion shown, policy violation lists the rules)
- [x] 4.2 Add the stories "Suggestion" and "Policy violation" to `SCR-004-change-password.stories.ts` and render the images again; verify `npm run test:stories` and `python3 scripts/check_screens.py`

## 5. Documentation

- [x] 5.1 Document the floors and the switch permission (`manual/administration/operations.md`), the new section *Password policy* and the reset (`manual/administration/identity-providers.md`), the generated initial password, and the suggestion and rules on the login page (`manual/usage/login.md`) in English and German with US-026, US-027 and US-030 in `covers`; add `settings.password-policy-changed` and `person.password-reset` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 6. Wrap-up

- [x] 6.1 Set US-026, US-027 and US-030 to `done` with the realignment notes of the proposal and update the US-011 note; add notes to REQ-027, REQ-028, REQ-031 and REQ-173 (AC3); record the S5 status in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 6.2 Update `docs/data-model.puml` and `business-objects/local-credential.md` and `business-objects/system-settings.md` (data model section); regenerate `requirements/traceability.md` and verify `--check` passes
- [x] 6.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-027-password-administration --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
