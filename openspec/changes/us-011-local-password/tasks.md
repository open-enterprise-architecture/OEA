# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design (`setInitialPassword`, `changeOwnPassword`, `localLogin` description, `requiredAction` on `LoginResult` and `Me`, problem type `password-change-required`); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V10__person_passwords.sql` (D3); verify with a schema test: the owner check rejects no owner and two owners, a second active password of a person is rejected, and the existing credential tests pass

## 2. Backend (US-011, US-023, US-006 AC2)

- [x] 2.1 Add the properties of D1 with startup validation and `PersonPasswords.setInitial` with the operation of D7; verify with integration tests for "Initial password set" (no password in response, audit or captured logs), "Password already set", "Own person and service account", the switch off (409) and a password outside the length limits (422)
- [x] 2.2 Extend `LocalLogin` with the person branch of D2 and the required action of D4 (`OeaPrincipal`, interceptor, `LoginResult`, `Me`); verify with integration tests for "Fresh instance", "Login with a self-chosen password", "Throttled like the system admin account", "Expired initial password", "First login with the initial password" and that the existing local-login tests of the system admin account pass unchanged
- [x] 2.3 Add `changeOwnPassword` (D5); verify with integration tests for "Password changed" (other sessions revoked, old password refused), a wrong current password (403, counted by the throttle), an unchanged new password (422), and the system admin account changing its password
- [x] 2.4 Verify D6 with a unit test of `LocalLogin` that counts the hash verifications for unknown name, switch off, person not active, expired credential and wrong password ("Unknown and existing accounts")
- [x] 2.5 Cucumber scenarios in `identity/person-password.feature` (new) for US-011 AC1/AC2/AC3, US-023 AC1/AC3 and US-006 AC1; verify they pass through `CucumberIT`

## 3. Frontend and screens (D8)

- [x] 3.1 Change the label of the local form and the redirect after a login with `requiredAction` in `LoginPage.vue`, add the router guard and `ChangePasswordPage.vue` (`/me/password`); verify with Vitest cases (redirect after login, guard, mismatch, server problem shown, success)
- [x] 3.2 Add the screen stories `SCR-001-login.stories.ts` and `SCR-004-change-password.stories.ts` with play functions and update `docs/screens/SCREENS.md` (SCR-001 `implemented` without legacy SVG links, SCR-004 new); verify `npm run test:stories` (Playwright image, see `docs/development.md`) and `python3 scripts/check_screens.py`

## 4. Documentation

- [ ] 4.1 Describe the switch (`manual/administration/operations.md`), setting the initial password (`manual/administration/identity-providers.md`), the first login and the password change (`manual/usage/login.md`) in English and German with US-011, US-023 and US-006 in `covers`; add `person.password-set` and `auth.password-changed` and the person details of `auth.login`/`auth.login-failed` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 5. Wrap-up

- [ ] 5.1 Set US-011, US-023 and US-006 to `done` with the realignment notes of the proposal (US-006 AC2 verified structurally, design D6); add notes to REQ-011, REQ-024 and REQ-173 (AC1, AC2); record the S5 status in `docs/sprint-plan.md`; verify markdown lint passes
- [ ] 5.2 Update `docs/data-model.puml` and `business-objects/local-credential.md` (data model section); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 5.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict` and `openspec validate us-011-local-password --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
