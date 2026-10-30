# Tasks

## 1. Contract and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design; verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Flyway migration `V14__second_factor_policy.sql` (D1); verify with a schema test the defaults on a fresh database

## 2. Backend

- [x] 2.1 Add `SecondFactorProperties`, `SecondFactorPolicies` and `getSecondFactorPolicy`/`updateSecondFactorPolicy` (D1, D2); verify with integration tests for "Enforcement switched on", "Forced by the operator", "Admin role as exemption" (also an unknown role) and `settings:manage` required
- [x] 2.2 Set and lift `enroll-second-factor` at the login, after the password change and at the enrollment, and restrict the session (D3, D4); verify with integration tests for "First login under enforcement" (TOTP and passkey), "Only exempt roles", "One role not exempt", a person without roles, "No enforcement", the required password change first, a login with second factor without action, an IdP session and the system admin account unaffected
- [x] 2.3 Keep the last factor under enforcement (D5); verify with integration tests for "Last factor under enforcement", an exempt person removing the last factor, and a factor reset followed by the required enrollment
- [x] 2.4 Cucumber scenarios in `identity/second-factor-enforcement.feature` (new, tagged `@UC-01 @UC-03`) for US-031 and US-028; verify they pass through `CucumberIT`

## 3. Frontend and screens (D6)

- [x] 3.1 Extend `router/guards.ts`, `SecondFactorPage.vue`, `LoginPage.vue` and `ChangePasswordPage.vue`; verify with Vitest cases for the guard, the page without "Skip", the redirect after the login and after the password change
- [x] 3.2 Add the story "Required by the organization" to `SCR-003-second-factor.stories.ts` and render the images; verify `npm run test:stories` and `python3 scripts/check_screens.py`

## 4. Documentation

- [x] 4.1 Document the second-factor policy (`manual/administration/identity-providers.md`), the operator setting (`manual/administration/operations.md`) and the required enrollment (`manual/usage/login.md`) in English and German with US-031 and US-028 in `covers`; add `settings.second-factor-policy-changed` to `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 5. Wrap-up

- [x] 5.1 Set US-031 and US-028 to `done` with the realignment notes of the proposal; add notes to REQ-020, REQ-029 and REQ-173 (AC5); record the S5 status in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 5.2 Update `docs/data-model.puml` and `business-objects/system-settings.md` (data model section); regenerate `requirements/traceability.md` and verify `--check` passes
- [x] 5.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-031-second-factor-enforcement --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
