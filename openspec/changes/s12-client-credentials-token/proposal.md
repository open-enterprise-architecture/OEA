# Proposal

## Why

Service accounts exist since change 3 (`us-142-service-accounts`), but they cannot call the API yet: the backend authenticates only the session cookie, and the token endpoint and the token schemes of the contract are `planned`. A script or integration without a browser cannot use the API ([#264](https://github.com/open-enterprise-architecture/OEA/issues/264), finding API-1 of the [architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md)). [REQ-176](../../../requirements/req/REQ-176-machine-credential-lifecycle.md) and [ADR-050](../../../adrs/ADR-050-service-account-access-tokens.md) define the token; this is S12 change 4 and closes the acceptance criteria of US-142 and US-149 that need a token.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-02: System Administration Bootstrapping](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md), [UC-22: Import current state from a source system](../../../requirements/use-cases/UC-22-import-current-state-from-source-system.md) |
| User Stories | [US-142: Create a Service Account for External Systems](../../../requirements/user-stories/US-142-create-service-account.md) (AC2, the token side of AC3 and AC4), [US-149: Assign Roles to a Service Account](../../../requirements/user-stories/US-149-assign-roles-to-service-account.md) (AC2, AC5), [US-008: Meet and Measure Login Latency Targets](../../../requirements/user-stories/US-008-login-latency.md) (AC2) |
| Requirements | [REQ-176: Machine credential lifecycle and scoping](../../../requirements/req/REQ-176-machine-credential-lifecycle.md) (AC2–AC4), [REQ-155: Service Account Management](../../../requirements/req/REQ-155-service-account-management.md) (AC2, AC3), [REQ-008: Login latency](../../../requirements/req/REQ-008-login-latency.md), [REQ-168: Throttling](../../../requirements/req/REQ-168-local-auth-throttling.md), [REQ-174: Audit tamper resistance](../../../requirements/req/REQ-174-audit-tamper-resistance.md), [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md) |
| ADRs | [ADR-050: Access tokens of service accounts](../../../adrs/ADR-050-service-account-access-tokens.md), [ADR-026: External system integration](../../../adrs/ADR-026-external-system-integration.md), [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md), [ADR-033: Audit trail hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-035: Electron security baseline](../../../adrs/ADR-035-electron-security-baseline.md) |
| Sprint / Milestone | S12 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-15:**

- **Limits of the token endpoint (ADR-050 rules 6 and 7):** the same as for the local login.
  - Per `client_id`: exponential backoff from the sixth failure within 15 minutes, capped at a 15-minute lock.
  - Per client address: 100 failures within 10 minutes.
  - Above a limit, the answer is 429 with `Retry-After`.
  - At most 4 secret checks run at the same time; a request that waits longer than 2 seconds for a slot gets 429.
- **One token type:** `bearerAuth` describes the opaque access token of ADR-050 instead of a JWT, and is accepted from this change on. Service accounts obtain it through `oauth2ClientCredentials`. The Electron client (ADR-035) will later obtain the same token through a person login; the check and the header stay the same, so it needs no endpoint or token type of its own.
- **US-008 AC2:** the measurement of the token request with 1,000 service accounts is part of this change.

**Token endpoint (`identity`):** `issueClientCredentialsToken` (`POST /oauth2/token`, public) loses `planned`.

- Only `grant_type=client_credentials` with HTTP Basic client authentication. Another grant type is answered with 400 `unsupported_grant_type`; a missing grant type, credentials in the body or in the query string with 400 `invalid_request` or 401.
- Unknown client, wrong secret, expired secret and deactivated account all get the same 401 `invalid_client` with `WWW-Authenticate: Basic`. For an unknown client, the check runs against a fixed dummy hash.
- The token is `oea_at_` plus 256 random bits. Only its SHA-256 hash is stored, with the client, issue time, expiry (900 seconds) and revocation time. The response has `token_type: Bearer`, `expires_in: 900`, no refresh token, and `Cache-Control: no-store` and `Pragma: no-cache`.
- A successful grant sets `lastUsedAt` of the service account.
- Audit: `token.issued`, `token.denied` (the claimed client marked as unverified, the reason internal) and `token.revoked`. Neither secret nor token is ever logged.

**Bearer authentication (`platform`):**

- A request with `Authorization: Bearer <token>` is authenticated as the service account of a stored, unexpired, unrevoked token of an active account. Otherwise it is answered with 401 and `WWW-Authenticate: Bearer`.
- The token is accepted only in that header, never in a query parameter or cookie. A request that carries both a session cookie and a bearer token is answered with 401.
- Permissions come from the roles of the service account, resolved per request. A bearer request needs no CSRF token, since no cookie authenticates it.
- The operations of the own account (`/me/**` except `GET /me`, `/auth/**`) are refused for service accounts with 403.
- Audit events of a service account carry the actor type `service-account` and `agentId` = its client ID (REQ-174).
- The per-principal rate limit applies to service accounts as to persons.

**Revocation:** rotating the secret and deactivating the account revoke every token of the account at once (ADR-050 rule 4). Expired tokens are deleted by a cleanup job one day after their expiry.

**Contract:** the schemes `bearerAuth`, `oauth2ClientCredentials` and `clientSecretBasic` lose `planned`. The info text no longer says that only `cookieAuth` is accepted.

**Measurement:** `LoginLatencyIT` additionally measures the token request with 1,000 active service accounts against p95 < 300 ms (US-008 AC2), with a metric `oea.token.processing`.

**Realigned sections:**

- US-142 AC2: "an access token identifying the service account by its `client_id`" is the opaque token of ADR-050. It carries no claims; the server links it to the `client_id`.
- REQ-155 AC2: "receive JWT" becomes "receive an access token".
- US-008 AC2: measured on the token request as described.

## Out of Scope

- Token issuance for persons and the Electron client: login, refresh tokens and session binding (ADR-035, a later change).
- Token introspection (RFC 7662) and revocation by the client (RFC 7009).
- Sender-constrained tokens (DPoP, mTLS), deferred by ADR-050.
- `upsertEntityByExternalId` and the other planned write operations of ADR-026; a service account uses the operations implemented today, such as the entity batch.
- A screen for issued tokens.

## Capabilities

### New Capabilities

- `identity/access-tokens`: the token endpoint with its client authentication, limits and errors, the opaque access token with storage, lifetime, revocation and cleanup, bearer authentication of service accounts, and its audit events.

### Modified Capabilities

- `platform/login-latency`: a new requirement for the processing time of the token request (US-008 AC2).

## Impact

- **api/openapi.yaml:**
  - `issueClientCredentialsToken` loses `planned` and gets 429 and the response headers;
  - `bearerAuth` is reworded to the opaque token, and the three schemes lose `planned`;
  - the info text on authentication is updated.
- **backend:**
  - migration V31 `access_tokens`;
  - `core` for the token store and the active secret of `ServiceAccountStore`;
  - `app/access` (or `app/login`) for the token issuance, check and revocation, with the throttle and the concurrency limit;
  - `config/security` for the bearer filter next to `OeaSessionFilter`, the accepted schemes and the CSRF exemption;
  - `OeaPrincipal` gets the type `SERVICE_ACCOUNT` with the client ID;
  - `AuditEvent.Actor` gets the `agentId`;
  - `ServiceAccounts` revokes tokens on rotation and deactivation and shows `lastUsedAt`;
  - the cleanup job.
- **frontend:** none. `lastUsedAt` is already shown on SCR-041.
- **Documentation:**
  - the user manual: API access without a browser in `api/` (EN and DE), and the token on `administration/service-accounts`;
  - `docs/audit-event-catalog.md`;
  - `docs/data-model.puml` and `business-objects/machine-credential.md` (access tokens);
  - US-008, US-142, US-149, REQ-155 and REQ-176.
