# Tasks

## 1. Data model and migration

- [x] 1.1 Update `docs/data-model.puml` and `business-objects/` (`session`, `auth-provider`) for `sessions.idp_token_expires_at`, `sessions.idp_group_identifiers`, `session_idp_roles` and the new table `oidc_login_states` (design, Data Model); verify the PlantUML file renders and link validation passes
- [x] 1.2 Flyway migration `db/migration/V4__oidc_login.sql` with the session columns, `session_idp_roles`, `oidc_login_states` and the grants for `oea_app`; verify with schema tests for columns, constraints, owners and grants

## 2. Contract and provider source

- [x] 2.1 Extend `api/openapi.yaml`: `listLoginProviders`, `startOidcLogin`, `completeOidcLogin` (public, allowlisted), `__Host-OEA_LOGIN` cookie, problem property `reason: self-administration`, provider descriptions (database only, `offline_access` for Entra ID); verify Redocly lint, `scripts/check_openapi_authorization.py` and that the generated interfaces compile
- [x] 2.2 Remove `oea.security.oidc.providers` and its binding; add `StoredProviderGuard` (G2/G3 outside `dev`, inverse loopback rule in `dev`, enabled providers only) and the G5 rule for `oea.web.public-url`; adapt `dev/backend/application-dev.yml`; verify with startup tests for the specs "Stored provider with an unsafe issuer", "Disabled provider ignored" and "Development profile with a real issuer"

## 3. OIDC login

- [x] 3.1 Configure the development realm: method references `pwd`/`otp` on the browser flow so that `amr` is emitted, redirect URI `http://localhost:5173/api/v1/auth/oidc/callback`; verify with a test that the ID tokens of `dev-system-admin` and `dev-admin-no-mfa` carry the expected `amr` and that `check_dev_secrets.py` passes
- [x] 3.2 Implement discovery and ID token validation per provider (D1, asymmetric algorithms only, `nonce`, `azp`); verify with tests for the spec "Invalid identity token" using real tokens of `oea-test-foreign-app` and crafted `none`/`HS256` tokens
- [x] 3.3 Implement `startOidcLogin` and `completeOidcLogin` with `oidc_login_states`, the login cookie and PKCE (D2, D3); extend the test helper `OidcLogin` to drive the application's flow; verify with tests for the specs "Successful login", "Existing session", "Login without PKCE" and "Only enabled providers offered"
- [x] 3.4 Implement the identity binding lookup, the person status rules and the transition `invited → active` with `person.activated` (D5) and the session of a person with `idp_token_expires_at` (D6); verify with tests for the specs "First login of an invited person", "Matching e-mail with a different subject" (`dev-email-twin`) and "Person not active"
- [x] 3.5 Implement the generic failures and audit events of D10; verify with tests for the spec "Unknown and inactive identities" and a provider that cannot be reached
- [x] 3.6 Write the Cucumber feature `identity/oidc-login.feature` for the `identity/oidc-login` spec against the running application and the Keycloak Testcontainer; verify it passes through `CucumberIT` with the REQ-175 AC3 log scan (no token, code or verifier in the logs)

## 4. IdP-derived roles

- [x] 4.1 Implement the classification of the groups claim, the derivation into `session_idp_roles`, `sessions.idp_group_identifiers` and `auth.idp-roles-derived` (D7); extend `PermissionResolver` and `GET /me` with roles of source `idp`; verify with tests for the specs "Role derived at login", "Person without any role", "Groups claim missing or truncated" and "Same group identifier from another issuer"
- [x] 4.2 Implement the re-evaluation in the session filter with the refresh grant, refresh token rotation and the three outcomes of D7; verify with tests (injectable clock, group removal and session revocation through the Keycloak Admin API) for the specs "Removal from the group in the IdP", "IdP unreachable at re-evaluation", "Mapping deleted in OEA" and "IdP refresh token expired or rejected"
- [x] 4.3 Implement the second-factor rule for privileged derived roles (D8); verify with tests for the specs "Privileged role without second factor" (`dev-admin-no-mfa`) and "Privileged role with second factor" (`dev-system-admin`)
- [ ] 4.4 Implement the SoD-1 check on mapping writes (D9); verify with a test for the spec "Own group rejected"
- [ ] 4.5 Write the Cucumber feature `identity/idp-roles.feature` for the `identity/idp-roles` spec and the session and mapping scenarios changed by this change; verify it passes through `CucumberIT`

## 5. Development seed and frontend

- [ ] 5.1 Write `scripts/dev_seed.py` (D11); verify against the development environment that it is idempotent and that `dev-viewer` can log in afterwards
- [ ] 5.2 Extend the login page with the provider buttons and the error messages, and the profile with role sources and the REQ-003 notice (D12); verify with Vitest tests; SCR-001 keeps the status `legacy-mockup` without screen story (screen stories with change 6, as decided for change 3a)
- [ ] 5.3 Verify the walking-skeleton steps 3–4 manually in the development environment: bootstrapping, seed, OIDC login as `dev-viewer` (role `viewer`), as `dev-admin-no-mfa` (`system-admin` not effective) and as `dev-system-admin` (effective), removal of `dev-viewer` from its group in Keycloak and loss of `viewer` after 15 minutes (shortened by an expired evaluation time), `oea audit verify` OK; record the result in the PR

## 6. Wrap-up

- [ ] 6.1 Document the login configuration (`oea.web.public-url`, provider scopes per vendor, redirect URI, removal of `oea.security.oidc.providers`) in `docs/operations.md`, the new event types in `docs/audit-event-catalog.md`, and the provider source in `docs/walking-skeleton.md`; clarify REQ-001 (context "Token validity") and US-001 AC3 with the owner decision of 2026-10-15; verify markdown lint and link validation pass
- [ ] 6.2 Update the user stories: US-001 AC1–AC3 implemented (status `review`), US-147 AC2, AC3, AC5, AC6 and AC7 implemented; the implementation hash is added when the change is archived after the merge
- [ ] 6.3 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (unchanged) with the implementation; regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [ ] 6.4 Run `python3 scripts/validate_links.py`, the markdown lint, `scripts/check_screens.py`, `scripts/sync_agent_config.py --check` and the OpenAPI checks and verify all pass
