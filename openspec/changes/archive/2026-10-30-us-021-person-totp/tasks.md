# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design (`listOwnSecondFactors`, `removeOwnSecondFactor`, `resetPersonSecondFactors`, `SecondFactor.lastUsedAt`, the changed descriptions of the enrollment and login operations); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V12__person_totp.sql` (D4); verify with a schema test that a login transaction needs exactly one owner

## 2. Enrollment and login of persons (US-021, US-025, US-029, US-010, US-024)

- [x] 2.1 Extend `TotpEnrollment` and `LocalCredential` for persons with label default and failure audit (D1–D3); verify with integration tests for "Enrollment by a person", "Wrong code during the enrollment", "Expired enrollment", the label default, a person without local password (403) and that the system admin enrollment tests pass
- [x] 2.2 Add the TOTP step to the password login of persons (D4, V12); verify with integration tests for "Login with password and code" (privileged role effective), "Wrong code", "Second authenticator", five wrong codes, code replay, "Second factor stays required" after a password reset, an invited person activated only after the TOTP step, and that every failed password login still verifies exactly one hash
- [x] 2.3 Cucumber scenarios in `identity/person-totp.feature` (new, tagged `@UC-01 @UC-03`) for US-021, US-029, US-010 and US-024; verify they pass through `CucumberIT`

## 3. Own second factors and factor reset

- [x] 3.1 Add `SecondFactors` with `listOwnSecondFactors` and `removeOwnSecondFactor` (D5); verify with integration tests for "Two authenticators", "Lost phone" (other sessions ended), "Last factor of the system admin account", re-authentication required, and a foreign credential (404)
- [x] 3.2 Add `resetPersonSecondFactors` (D6) and share the admin-class check with `PasswordResets`; verify with integration tests for "Every device lost", "Reset of an administrator by another administrator", the system admin account resetting an administrator, the own person (403), a person without factors (409) and a service account (422)
- [x] 3.3 Cucumber scenarios in `identity/second-factor-management.feature` (new, tagged `@UC-03`) for the removal and the factor reset; verify they pass through `CucumberIT`

## 4. Frontend and screens (D7)

- [x] 4.1 Extend `SecondFactorPage.vue` (label, offer mode with "Skip"), `LoginPage.vue` and `ChangePasswordPage.vue` (offer as next step) and `ProfilePage.vue` (list and removal), with `api/session.ts`; verify with Vitest cases for the label, skipping the offer, the offer after the password login and after the password change, no offer with an existing factor, the list and the removal including `reauthentication-required`
- [x] 4.2 Add the screen stories `SCR-003-second-factor.stories.ts` (enrollment, offer, wrong code) and `SCR-005-profile.stories.ts` (with and without factors), render the images and add SCR-005 and the new SCR-003 images to `docs/screens/SCREENS.md`; verify `npm run test:stories` and `python3 scripts/check_screens.py`

## 5. Documentation

- [x] 5.1 Document setting up, using and removing an authenticator and the enrollment offer (`manual/usage/login.md`), and the factor reset (`manual/administration/identity-providers.md`) in English and German with US-021, US-025, US-010, US-029 and US-024 in `covers`; add `auth.factor-enrollment-failed`, `auth.factor-removed` and `person.second-factors-reset` to `docs/audit-event-catalog.md` and update `auth.factor-added` and `auth.login`; verify the manual coverage check and markdown lint pass

## 6. Wrap-up

- [x] 6.1 Set US-021, US-010, US-029 and US-024 to `done` and US-025 to `in-progress` (AC2 follows with change 4) with the realignment notes of the proposal; add notes to REQ-010, REQ-022, REQ-025, REQ-026, REQ-030, REQ-031 (AC8) and REQ-173 (AC5); record the S5 status in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 6.2 Update `docs/data-model.puml` and `business-objects/local-credential.md` (data model section); regenerate `requirements/traceability.md` and verify `--check` passes
- [x] 6.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-021-person-totp --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
