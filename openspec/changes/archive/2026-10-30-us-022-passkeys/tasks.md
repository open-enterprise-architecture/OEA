# Tasks

## 1. Contract, dependencies and migration

- [x] 1.1 Update `api/openapi.yaml` as in the API section of the design (six operations, `LoginProblem.secondFactors`, schemas); verify Redocly lint, `scripts/check_openapi_authorization.py`, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Add `webauthn4j-core` (and `webauthn4j-test` in test scope) to `backend/pom.xml` and `@simplewebauthn/browser` to `frontend/package.json` in the current releases; regenerate `THIRD-PARTY-LICENSES.md` (ADR-043); verify the license check passes with `--check`
- [x] 1.3 Flyway migration `V13__passkeys.sql` (D2); verify with a schema test the unique credential ID and user handle, the challenge purposes and the session method `password-passkey`

## 2. Backend

- [x] 2.1 Add `PasskeyProperties` and the WebAuthn4J configuration (D1); verify with unit tests the defaults from `oea.web.public-url`, the startup validation of RP ID and origins, and 409 `passkeys-unavailable` without them
- [x] 2.2 Add `PasskeyRegistration` with `startPasskeyRegistration` and `confirmPasskeyRegistration` (D3); verify with integration tests using the software authenticator for "Passkey registered", "Wrong origin", a wrong RP ID, missing user verification, an expired registration, "Second device", the label default, a person without local password (403) and re-authentication
- [x] 2.3 Add `PasskeyLogin` with `startPasskeyLogin` and `passkeyLogin` (D4) and the session methods; verify with integration tests for "Login with a passkey" (privileged role effective, invited person activated), "Unknown passkey", a wrong signature, missing user verification, a counter going backwards, a removed passkey, password login of persons switched off, the required password change, and the throttling per source address
- [x] 2.4 Add the passkey second step (D5) with `secondFactors` in the second-factor answer; verify with integration tests for "Password and passkey", "Methods named", five failures invalidating the transaction, a passkey of another person rejected, and that the TOTP and system admin login tests pass
- [x] 2.5 Cucumber scenarios in `identity/passkeys.feature` (new, tagged `@UC-01 @UC-03`) for US-022, US-009 and US-025 AC2 with the software authenticator; verify they pass through `CucumberIT`

## 3. Frontend and screens (D6)

- [x] 3.1 Add `api/passkey.ts`, the passkey choice and registration in `SecondFactorPage.vue`, the passkey login and second step in `LoginPage.vue`, and the type in `ProfilePage.vue`; verify with Vitest cases (stubbed `@simplewebauthn/browser`) for registration, cancellation, passkey login, the second step with and without TOTP, and a browser without WebAuthn
- [x] 3.2 Update the screen stories `SCR-001-login.stories.ts` (passkey button, second step with passkey) and `SCR-003-second-factor.stories.ts` (choice, passkey label), render the images, replace the passkey mockup in `docs/screens/SCREENS.md` and delete its SVGs; verify `npm run test:stories` and `python3 scripts/check_screens.py`

## 4. Documentation

- [x] 4.1 Document registering and using a passkey and the second step (`manual/usage/login.md`) and the relying party settings (`manual/administration/operations.md`) in English and German with US-022, US-009 and US-025 in `covers`; update `auth.factor-added`, `auth.factor-enrollment-failed`, `auth.login` and `auth.login-failed` in `docs/audit-event-catalog.md`; verify the manual coverage check and markdown lint pass

## 5. Wrap-up

- [x] 5.1 Set US-022, US-009 and US-025 to `done` and add the passkey part to US-024 with the realignment notes of the proposal; add notes to REQ-009, REQ-023, REQ-025 and REQ-026; record the S5 status in `docs/sprint-plan.md`; verify markdown lint passes
- [x] 5.2 Update `docs/data-model.puml` and `business-objects/local-credential.md` (data model section); regenerate `requirements/traceability.md` and verify `--check` passes
- [x] 5.3 Run `python3 scripts/validate_links.py`, `python3 scripts/check_screens.py`, the OpenAPI checks, the license check, `scripts/sync_agent_config.py --check`, `openspec validate --specs --strict`, `openspec validate us-022-passkeys --strict`, markdownlint as in CI, `./mvnw verify` and the frontend type check, tests and build, and verify all pass
