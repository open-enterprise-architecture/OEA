# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D1, section API):
  - `issueClientCredentialsToken` loses `planned` and 501, and gets `429`, `Pragma`, `WWW-Authenticate` and the description;
  - `bearerAuth` is reworded to the opaque token (`bearerFormat: opaque`);
  - `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic` lose `planned`;
  - the info text on the accepted schemes is updated.

  Verify: Redocly lint passes, the generated API compiles, and `check_openapi_authorization.py` passes. `SecuritySchemeCoverageCheck` starts with the four accepted schemes and refuses a set with `bearerAuth` missing.

## 2. Backend: storage and audit actor

- [ ] 2.1 Flyway migration `V31__access_tokens.sql` (D2): `access_tokens` with the unique `token_hash`, the index on `client_id`, the FK to `machine_credentials`, the check `expires_at = issued_at + interval '900 seconds'`, and grants for `oea_app` (SELECT, INSERT, UPDATE, DELETE).

  Verify with `AccessTokenSchemaTests` on the runtime role: a second row with the same hash is rejected; an expiry of 900 seconds is accepted and one of 901 is rejected; the owner is `oea_owner`.
- [ ] 2.2 `AccessTokenStore` in `core/access` (D2, D7): insert, find a valid token by hash with its active service account, revoke all tokens of a client, delete tokens expired before a time, and set `last_used_at` of a secret.

  Verify with `AccessTokenStoreTests`:
  - a token is found 1 second before its expiry and not at its expiry;
  - a revoked token and a token of a deactivated account are not found;
  - the cleanup with "now − 1 day" deletes a token expired exactly 1 day ago and keeps one expired 23:59 hours ago.
- [ ] 2.3 `AuditEvent.Actor` with `agentId`, `Sessions.actor` for `SERVICE_ACCOUNT`, and `AuditWriter` writing `agent_id` (D6). `OeaPrincipal.Type.SERVICE_ACCOUNT` with `clientId`; `RateLimitFilter` key `service-account:<client_id>`.

  Verify with unit tests:
  - the actor of a service account principal is `service-account` with person and client ID;
  - an appended event stores `agent_id`, and the hash chain verifies (`AuditIntegrity` test);
  - persons and the system admin account keep `agent_id` null.

## 3. Backend: token endpoint and bearer authentication

- [ ] 3.1 `AccessTokens` in `app/access` (D3, D4, D7, D8):
  - issuing with the throttle, the semaphore, the dummy hash and the uniform refusal;
  - checking a bearer token;
  - `revokeAll`;
  - the audit events `token.issued`, `token.denied` (through the flood guard) and `token.revoked`;
  - the timer `oea.token.processing`.

  The throttle keys `client:` and `address:` reuse the login rules. Verify with `AccessTokensTests`:
  - positive: a token starts with `oea_at_`, is stored as its SHA-256 hash, and expires after 900 seconds; `last_used_at` is set; the throttle key of the client is cleared on success;
  - secret expiry boundary: 1 second before the expiry a token is issued, at the expiry it is refused;
  - the same refusal for an unknown client, a wrong secret, an expired secret and a deactivated account, each with one `token.denied` and with the dummy hash checked for the unknown client;
  - throttle boundaries: the 5th and 6th failure within 15 minutes are checked, the next request in the backoff gives 429; the 100th failure of an address is checked, the 101st request gives 429; another address is not affected;
  - concurrency: with 4 checks held, a fifth waits; it gets a slot within 2 seconds and gets 429 after 2 seconds;
  - no log line and no audit event contains the secret or the token.
- [ ] 3.2 `AccessTokenController` (`api/identity`, D3) with the RFC 6749 answers, and `OeaTokenFilter` with the changes to `CsrfOriginFilter`, `ACCEPTED_SCHEMES` and the security chain (D5). Add the 403 rule for the own-account operations of service accounts, and the revocation in `ServiceAccounts.rotate` and `.deactivate`.

  Verify with controller tests:
  - 200 with `no-store` and `Pragma`; `grant_type=password` gives 400 `unsupported_grant_type`; a missing `grant_type` gives 400 `invalid_request`; credentials in the body or the query give 401 `invalid_client` with `WWW-Authenticate: Basic`;
  - a bearer request without a CSRF token is accepted; a token in the query or a cookie gives 401; cookie plus bearer gives 401; an unknown token gives 401 with `WWW-Authenticate: Bearer`;
  - `GET /me` shows `service-account`; a password change gives 403;
  - after a rotation and after a deactivation, the old token gives 401, and one `token.revoked` is written per token.
- [ ] 3.3 `AccessTokenCleanup` (`@Scheduled`, `oea.access-tokens.cleanup-interval`, D7). Verify with a test on the boundary of 1 day with a fixed `Clock`.
- [ ] 3.4 Cucumber `features/identity/access-tokens.feature` with the scenarios of the spec `identity/access-tokens`:
  - "Token issued", "Other grant type", "Credentials outside the header";
  - "Old secret after rotation", "Expiry boundary of the secret", "Same answer for every refusal";
  - "Backoff per client", "Limit per address";
  - "Stored as a hash";
  - "Write with integration-writer", "Token lifetime boundary", "Unknown token";
  - "Write without integration-writer", "Administration refused", "Own account";
  - "Token in the query string", "Cookie and bearer token";
  - "Rotation revokes tokens", "Deactivation revokes tokens", "Cleanup boundary".

  Verify that the feature passes.
- [ ] 3.5 `LoginLatencyIT` with 1,000 active service accounts and the token request against p95 < 300 ms (D8; spec `platform/login-latency`). Verify that it passes and log the measured p95.

## 4. Documentation

- [ ] 4.1 Realign US-142 AC2, REQ-155 AC2 and US-008 AC2 as listed in the proposal, with a change history entry in REQ-155. Add an implementation note to REQ-176 (AC2–AC4) and to ADR-050 (the limits of rules 6 and 7, as decided on 2026-11-15).
- [ ] 4.2 `docs/audit-event-catalog.md`: `token.issued`, `token.denied`, `token.revoked`, and `agentId` for service accounts. `docs/data-model.puml` and `business-objects/machine-credential.md`: `access_tokens` (section Data Model).
- [ ] 4.3 User manual in English and German:
  - a page `api/access-without-browser.md`: request a token with `curl`, keeping the secret out of the command line; call the API with the token; renew the token before the 900 seconds; the errors 401 and 429; and what the audit log records;
  - `administration/service-accounts.md` without the notice "follows in a later version";
  - `operations.md` with the cleanup interval.

  Verify the manual build, its tests and `scripts/check_manual.py`.

## 5. Wrap-up

- [ ] 5.1 Set US-142, US-149 and US-008 to done with the implementation hash; update the S12 status in `docs/sprint-plan.md`; close #264 in the pull request.
- [ ] 5.2 Synchronize `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` (no screen change expected); regenerate `requirements/traceability.md` (`python3 scripts/generate_traceability.py`).
- [ ] 5.3 Run `python3 scripts/validate_links.py`, markdownlint and `openspec validate --strict`, and verify they pass.
