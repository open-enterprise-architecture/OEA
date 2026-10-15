# Tasks

## 1. Data model and migration

- [x] 1.1 Update `docs/data-model.puml` and `business-objects/` (`session`, `local-credential`, `system-settings`, `auth-provider` or the provider section of `person`) for D1–D10: session token and CSRF hashes, credential status `pending`, `login_transactions`, `auth_throttle`, setup-phase columns, provider client settings; verify the PlantUML file renders and link validation passes
- [x] 1.2 Flyway migration `db/migration/V3__identity_and_sessions.sql` with `persons`, `person_identities`, `auth_providers`, `permissions`, `authorization_roles`, `role_permissions`, `idp_group_mappings`, `sessions`, `totp_credentials`, `login_transactions`, `auth_throttle`, the `system_settings` columns and the grants for `oea_app`; seed the permission catalog and the nine built-in roles; verify with tests that owners and grants are as designed and that the seed equals `x-oea-permissions` and `x-oea-builtin-roles` of `api/openapi.yaml`

## 2. Contract

- [x] 2.1 Extend `api/openapi.yaml`: cookie scheme `__Host-OEA_SESSION`; `localLogin`, `localLoginTotp` (public, allowlisted); `startTotpEnrollment`, `confirmTotpEnrollment`; `createPerson`, `listPersons`, `getPerson`, `listPersonIdentities`, `createPersonIdentity`, `deletePersonIdentity`; `AuthProvider.clientSecretSet` and `scopes`; `Me` fields; problem types `invalid-credentials`, `second-factor-required`, `reauthentication-required`, `csrf-rejected`; verify Redocly lint and `scripts/check_openapi_authorization.py` pass and the generated interfaces compile

## 3. Secret storage

- [x] 3.1 Implement key loading (`oea.security.kek.current`, `oea.security.kek.previous`), key IDs and AES-256-GCM encryption with additional authenticated data (D8); verify with unit tests for round trip, wrong key, swapped row and tampered ciphertext
- [x] 3.2 Extend guard G4 for the key file (missing, unreadable, shorter than 256 bit, known development key); add `dev/secrets/oea-kek-dev-only.key`, list its hash and extend `scripts/check_dev_secrets.py` to treat files under `dev/secrets/` as secrets; add the key as Compose secret in `compose.yaml` (required) and `compose.dev.yaml`, and `OEA_KEK_FILE` in `.env.example`; verify startup tests for the spec "Missing key" and that `check_dev_secrets.py` and `check_compose_secrets.py` pass
- [ ] 3.3 Implement `oea secrets reencrypt`; verify with a Testcontainers test for the spec "Rotation" (TOTP login works after rotation) and a dump search for the spec "Database dump"

## 4. Sessions and authorization

- [ ] 4.1 Add Spring Security with stateless configuration, security headers (REQ-187) and CORS for the allowed origins; implement `OeaSessionFilter` with token hash lookup, idle timeout, absolute lifetime and activity update (D1); verify with tests for the specs "Idle or expired session" and "Security headers"
- [ ] 4.2 Implement the CSRF and origin checks (D2); verify with tests for the spec "Missing CSRF token" including the login endpoint's origin check
- [ ] 4.3 Implement `ContractAuthorizationRules` from the packaged contract, the authorization interceptor and `PermissionResolver` with 60 s cache (D3, D4); verify with tests that a handler without rule is denied, that an unknown operation in a rule fails startup, and the specs "Request without session" and "Principal without the permission"
- [ ] 4.4 Write the generated authorization matrix test over all implemented handler methods; verify it passes and fails when a test-only controller without declaration is added (spec: Every implemented operation is covered)
- [ ] 4.5 Implement `GET /me` and `logout` with audit event `auth.logout`; verify with tests for the specs "Reading the own session" and "Request after logout"
- [ ] 4.6 Implement `GET /admin/permissions` and `GET /admin/roles`; verify with tests for the specs "Catalog and roles of a fresh instance" and "Reading without role:assign"

## 5. System admin login and second factor

- [ ] 5.1 Implement the password login (D5) with session cookie, CSRF token, generic `invalid-credentials`, equal timing for unknown names, argon2id rehash, state change `bootstrapped → active` and audit events `auth.login`, `auth.login-failed`; verify with tests for the specs "Login after bootstrapping", "Unknown login name and wrong password" and "Cookie after login"
- [ ] 5.2 Implement throttling in the database (D6) with `auth.throttled`; verify with tests for the specs "Repeated wrong passwords" and "Many names from one address", using an injectable clock
- [ ] 5.3 Implement TOTP enrollment, confirmation and replay protection (D7) with encrypted secrets and audit event `auth.factor-added`; verify with tests for the specs "Enrollment and confirmation", "Enrollment in an old session" and "Code replay"
- [ ] 5.4 Implement the setup phase in login and the TOTP login step (D5, D9); verify with tests for the specs "Login during the setup phase", "Login after the setup phase" and "Wrong codes in one login transaction"
- [ ] 5.5 Write the Cucumber feature `identity/local-login.feature` for the `identity/local-login` and `identity/sessions` specs against the running application on a fresh database; verify it passes through `CucumberIT` with the log scan of REQ-175 AC3 (no password, TOTP secret or code in the logs)

## 6. Access administration

- [ ] 6.1 Implement the provider operations with issuer rules G2/G3 outside `dev`, encrypted write-only client secret, fresh-authentication check and audit event `auth-provider.changed` (D10); verify with tests for the specs "Provider with an unsafe issuer" and "Client secret never returned"
- [ ] 6.2 Implement persons and identity bindings with audit events `person.created`, `person.identity-bound`, `person.identity-unbound`; verify with tests for the specs "Binding created" and "Pair already bound"
- [ ] 6.3 Implement IdP group mappings (UUID identifiers, soft delete, audit events `idp-mapping.created|changed|deleted`); verify with tests for the specs "Mapping created", "Display name instead of identifier" and "Mapping deleted"
- [ ] 6.4 Implement the setup-phase gate for bindings and mappings with `system.setup-phase-ended` (D9); verify with tests for the specs "First mapping without second factor" and "First mapping with second factor"
- [ ] 6.5 Write the Cucumber feature `identity/access-administration.feature` covering the spec scenarios, including the walk from login during the setup phase through TOTP enrollment to the first mapping; verify it passes through `CucumberIT`
- [ ] 6.6 Document the new event types in `docs/audit-event-catalog.md`, the key file, `oea secrets reencrypt`, the setup phase and the provider/person/mapping administration via API (with `curl` examples for the walking skeleton) in `docs/operations.md`; verify markdown lint and link validation pass

## 7. Frontend

- [ ] 7.1 Add the login page `/login` with the TOTP step and the generic error message, and send `X-XSRF-TOKEN` on state-changing requests; verify with Vitest tests for success, wrong credentials, TOTP step and throttling message
- [ ] 7.2 Add the TOTP enrollment page (QR code rendered locally from the `otpauth://` URI, manual secret, confirmation, re-authentication message), the profile view and logout; verify with Vitest tests; SCR-001 and SCR-003 keep the status `legacy-mockup` without screen story, as decided for SCR-002 (screen stories with change 6)
- [ ] 7.3 Verify the walking-skeleton flow manually in the development environment: bootstrapping, login during the setup phase, provider and person with binding to a Keycloak dev user via `curl`, rejected first mapping without second factor, TOTP enrollment, accepted mapping, login with TOTP afterwards, `oea audit verify` OK; record the result in the PR

## 8. Wrap-up

- [ ] 8.1 Update the user stories: US-013 AC2 implemented (status `review`), US-143 AC1/AC2/AC4 and US-147 AC1/AC4/AC7 noted as implemented in this change; the implementation hash is added when the change is archived after the merge
- [ ] 8.2 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (unchanged) with the implementation; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 8.3 Run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check` and the OpenAPI checks and verify all pass
