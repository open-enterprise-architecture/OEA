# Design

## Context

- **Authentication today:**
  - `OeaSessionFilter` authenticates only the session cookie and puts an `OeaAuthentication` (principal, session token) into the security context. `OeaSessionFilter.ACCEPTED_SCHEMES = {cookieAuth}`.
  - `SecuritySchemeCoverageCheck` stops the startup if the planned markers of the schemes disagree with it.
  - Many controllers cast the authentication to `OeaAuthentication` to get the principal.
- **CSRF:** `CsrfOriginFilter` checks `Origin` on state-changing requests and the `X-XSRF-TOKEN` for an `OeaAuthentication`. The token is derived from the session token.
- **Principal:**
  - `OeaPrincipal` knows the types `SYSTEM_ADMIN_ACCOUNT` and `PERSON`.
  - `PermissionResolver` resolves local assignments by `personId`, so a service account (a person row) already gets its roles.
  - `RateLimitFilter` keys by person or admin account.
  - `Sessions.actor` maps the principal to the audit actor.
- **Audit:**
  - `audit.events` has `actor_type` `service-account` (with `actor_person_id`) and the column `agent_id`, which nothing writes yet.
  - `AuditEvent.Actor(type, personId, adminId)`.
- **Throttling:** `LoginThrottle` stores failures in `auth_throttle` by key (`account:…`, `address:…`): exponential backoff per account from the sixth failure in 15 minutes, capped at 15 minutes, and 100 failures per address in 10 minutes.
- **Service accounts (change 3):**
  - `ServiceAccountStore.activeSecret(clientId)` returns the active secret of an active account, with its hash and expiry.
  - `machine_credentials.last_used_at` exists but is never written.
- **Contract:**
  - `issueClientCredentialsToken` is `planned`, with `TokenRequest`, `TokenResponse` and `OAuthError` (RFC 6749 §5.2).
  - The three schemes `bearerAuth` (described as a JWT), `oauth2ClientCredentials` and `clientSecretBasic` are `planned`.
- **Measurement:** `LoginLatencyIT` measures logins against the timer `oea.login.processing`.

## Goals / Non-Goals

**Goals:**

- The token endpoint and bearer authentication by ADR-050, with the limits decided on 2026-11-15.
- Revocation on rotation and deactivation, and cleanup of expired tokens.
- The audit actor of a service account with `agentId`.
- The measurement of US-008 AC2.

**Non-Goals:**

- Token issuance for persons and the Electron client.
- Introspection and client-side revocation.
- Sender-constrained tokens.

## Architecture Constraints

- [ADR-050](../../../adrs/ADR-050-service-account-access-tokens.md):
  - an opaque token with only its SHA-256 hash stored, 900 seconds of lifetime and no refresh token;
  - revocation on rotation and deactivation;
  - only `client_credentials` with `client_secret_basic`, and a uniform 401 `invalid_client`;
  - a dummy hash for unknown clients, a concurrency limit and throttling;
  - the events `token.issued`, `token.denied` and `token.revoked`;
  - `no-store`, and the `Authorization` header only.
- [ADR-028](../../../adrs/ADR-028-backend-layered-architecture.md):
  - the token store in `core/access`, next to `ServiceAccountStore`;
  - issuance, check and cleanup in `app/login` (`AccessTokens`, `AccessTokenCleanup`), next to `LoginThrottle`, since `app/login` already depends on `app/access`; the revocation in `app/access` (`ServiceAccounts`) through the core store;
  - the filter in `config/security`;
  - the endpoint in `IdentityController` (`api/identity`, the generated `IdentityApi`), with the RFC 6749 errors in `ApiExceptionHandler`.
- [ADR-032](../../../adrs/ADR-032-authorization-model.md) §7: the token carries no permissions, and `PermissionResolver` resolves them per request.
- [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md) §7 and REQ-174: `agentId` comes only from the authenticated client ID. `X-Agent-Id` stays informational.
- [ADR-035](../../../adrs/ADR-035-electron-security-baseline.md): the Electron client will present the same token through its main-process proxy. Nothing here may tie the check to service accounts beyond the issuing path.

## Decisions

### D1 – One token type, three schemes

`bearerAuth` is reworded to "opaque OEA access token (ADR-050), `oea_at_…`, in the `Authorization` header". `oauth2ClientCredentials` describes how service accounts obtain it, and `clientSecretBasic` the client authentication at the token endpoint.

- `ACCEPTED_SCHEMES` becomes `{cookieAuth, bearerAuth, oauth2ClientCredentials, clientSecretBasic}`, and all three lose `planned`.
- **Why:** the check is the same for every holder (owner decision 2026-11-15); a person login will later add a second issuing path.
- **Alternative:** keeping `bearerAuth` planned for Electron. That would leave a JWT description in the contract that contradicts ADR-050.

### D2 – Storage `access_tokens` (V31)

| Column | Meaning |
|---|---|
| `id` UUID | primary key; the token ID of the audit events |
| `token_hash` BYTEA | SHA-256 of the token, unique |
| `client_id` UUID | the service account; index |
| `credential_id` UUID | FK to `machine_credentials.id`: the secret version that obtained it |
| `issued_at`, `expires_at` | `expires_at = issued_at + 900 s`, enforced by a check |
| `revoked_at`, `revocation_reason` | `rotation`, `deactivation` |

- **Grants for `oea_app`:** SELECT, INSERT, UPDATE (revocation), and DELETE for the cleanup only.
- **Why `credential_id`:** an audit event can name the secret version, and the revocation on rotation finds the tokens of the rotated secret.
- **Why the revocation covers the whole client:** on a rotation, ADR-050 rule 4 revokes all tokens of the account, which is the stricter reading. On a deactivation it is the whole account anyway.

### D3 – Token endpoint

`IdentityController` (`api/identity`) implements `issueClientCredentialsToken`. Errors are RFC 6749 responses (`OAuthError`), not problem documents, written by `ApiExceptionHandler` for `OAuthRequestException` and `AccessTokens.InvalidClientException`; a missing `grant_type` (`MissingServletRequestParameterException`) is `invalid_request` there for operations marked `@OAuthErrorResponse`, read from the handler method and never from the path (the rule of `ApiExceptionHandlerTests`).

- **Parsing:**
  - the body must be `application/x-www-form-urlencoded` with exactly `grant_type`;
  - a missing `grant_type`, repeated parameters, or `client_id`/`client_secret` in the body or query give 400 `invalid_request`, except that credentials outside the header give 401 `invalid_client`, as the spec says;
  - a grant type other than `client_credentials` gives 400 `unsupported_grant_type`.
- **Client authentication:**
  - The `Authorization: Basic` header is decoded as `client_id:client_secret`, URL-decoded per RFC 6749 §2.3.1. A malformed header or a `client_id` that is not a UUID counts as an unknown client.
  - The throttle is checked first: 429 with `Retry-After`, without hashing.
  - Then a slot of the concurrency limit (D4) is taken and the hash compared:
    - the active secret of an active account, if one exists;
    - otherwise a fixed dummy argon2id hash, computed once at startup.
  - The outcome is 401 `invalid_client` with `WWW-Authenticate: Basic realm="oea"` for unknown, wrong, expired (`expires_at <= now`) and deactivated alike.
- **Success:**
  - insert the token, set `machine_credentials.last_used_at`, clear the throttle key of the client, and write `token.issued` (client ID, token ID, expiry);
  - answer 200 with `{access_token, token_type: "Bearer", expires_in: 900}`, `Cache-Control: no-store` and `Pragma: no-cache`.
- **Security filter chain:** the endpoint is public (`x-oea-authorization: public`) and exempt from CSRF and `Origin`, since it is not cookie-authenticated. A request with a session cookie is treated like any other.

### D4 – Limits

- **Throttle:** `LoginThrottle` is generalized to a key-based `AuthThrottle`, or gets client keys. The keys are `client:<client_id or the raw value>` (the backoff of `account:`: locked after the fifth failure within 15 minutes) and `address:<forwarded address>` (shared with logins, 100 per 10 minutes). The token endpoint uses the same rules as the login (owner decision 2026-11-15). A success resets only the client key.
- **Concurrency:** a fair `Semaphore(4)` around the hash check, with `tryAcquire(2, SECONDS)`. A timeout gives 429 with `Retry-After: 1`, and counts neither as a success nor as a failure.
- **Why:** the decision of 2026-11-15. The `auth_throttle` table works across several backend instances, while the semaphore is per instance, which is sufficient for a hashing guard.

### D5 – Bearer authentication

`OeaTokenFilter` runs before `OeaSessionFilter`:

- **Without a bearer header:** if the request has no `Authorization` header with the scheme `Bearer` (case-insensitive), nothing happens.
- **With a session cookie as well:** answer 401 (problem `unauthenticated`).
- **Checking the token:**
  - compute the hash and look up the token: not revoked, `expires_at > now`, its service account active;
  - on success, set an `OeaAuthentication` with the principal type `SERVICE_ACCOUNT` (`personId`, `clientId`) and no session token;
  - otherwise answer 401 with `WWW-Authenticate: Bearer error="invalid_token"` and the generic problem, and do not consult the cookie.
- **`access_token` in the query or a cookie:** ignored. The request stays unauthenticated and gets 401 where authentication is required.
- **CSRF:** `CsrfOriginFilter` checks the CSRF token only for authentications with a session token. Bearer requests skip both CSRF and `Origin`, since browsers do not attach the header by themselves.
- **Principal:**
  - `OeaPrincipal` gets `Type.SERVICE_ACCOUNT` and a `clientId`, set only for it;
  - `secondFactorVerified` is false, which does not matter, since service accounts hold no privileged role;
  - `authenticatedAt` is the issue time.
  - `RecentAuthentication` refuses service accounts, which never reach those operations anyway.
- **Own account:** the operations under `/me/**` except `GET /me`, and `/auth/**`, answer 403 for `SERVICE_ACCOUNT`. An interceptor rule names these operation IDs, and `GET /me` shows `principalType: service-account`. A list in the interceptor beats checks scattered over the handlers.
- **Rate limit:** the `RateLimitFilter` key is `service-account:<client_id>`.
- **Database read per request:** accepted by ADR-050.

### D6 – Audit actor with agent ID

`AuditEvent.Actor` gets a fourth component, `agentId`, with the 3-argument constructor kept.

- `Sessions.actor` maps `SERVICE_ACCOUNT` to `("service-account", personId, null, clientId)`.
- `AuditWriter` writes `agent_id` from the actor. It is already part of the hash chain.
- `token.denied` uses the actor `unauthenticated` and the details `claimedClientId` (marked `unverified: true`) and `reason`. The reason is internal and never in the response.
- `token.denied` goes through `AuditFloodGuard` like `authz.denied`.

### D7 – Revocation and cleanup

- `ServiceAccounts.rotate` and `.deactivate` call `AccessTokenStore.revokeAll(clientId, reason, now)` in their transaction. It sets `revoked_at` and writes one `token.revoked` per token (token ID, client ID, reason).
- `AccessTokenCleanup` is `@Scheduled` with the interval `oea.access-tokens.cleanup-interval`, default PT1H. It deletes rows with `expires_at <= now - 1 day`, with an injected `Clock`. Deletion writes no audit event, since `token.issued` already recorded the token.

### D8 – Measurement

- `AccessTokens` records the timer `oea.token.processing`, tagged `outcome` (`issued`, `denied`, `throttled`), around the whole request handling.
- `LoginLatencyIT` creates 1,000 active service accounts through SQL. They share one secret, whose argon2id hash is computed once and stored for every account, since a hash string carries its own salt and stays valid on any row. It then measures 30 token requests after 5 for warm-up, through the timer difference as for logins, against p95 < 300 ms (first result: p95 33 ms).
- **Why one check per request suffices:** the argon2id check dominates the processing time, and the endpoint runs exactly one per request; the measurement shows whether the parameters of REQ-169 fit the target.

## Data Model

- **New table `access_tokens`** (V31, D2). New class in `docs/data-model.puml`, with the relations `machine_credentials 1 — 0..* access_tokens` and `persons (service account) 1 — 0..* access_tokens` (through the client).
- **`business-objects/machine-credential.md`:** a section "Access tokens" (attributes, lifecycle issued → expired | revoked → deleted after one day), and BR-03 refers to ADR-050.
- **`machine_credentials.last_used_at`:** now written on every grant.
- **`audit.events.agent_id`:** written for service accounts. No migration of the audit schema.

## API

The delta to `api/openapi.yaml`:

- **`issueClientCredentialsToken`:**
  - loses `planned` and 501;
  - gets `429` (Retry-After) and `Pragma` in the 200 headers, and `WWW-Authenticate` in 401;
  - its description names the uniform 401, the limits and the audit.
- **`bearerAuth`:**
  - `bearerFormat` becomes `opaque`;
  - the description becomes: opaque access token (ADR-050), prefix `oea_at_`, issued to service accounts by `/oauth2/token` and later to the Electron client, only in `Authorization`, at most 900 s.
  - It loses `planned`, as do `oauth2ClientCredentials` and `clientSecretBasic`.
- **Info text:** "Implementation status: this release accepts only `cookieAuth`…" is replaced by a description of the accepted schemes, plus a pointer to the manual page on API access without a browser.

## Risks / Trade-offs

- **[A database read per bearer request]** It uses an index on `token_hash`, and ADR-050 accepts it. → Mitigation: a unique index, and only one row per request.
- **[Semaphore per instance]** With N backend instances, up to 4·N hash checks run at once. → One instance per deployment (ADR-050 context). The per-address throttle in the database limits the total.
- **[`OeaAuthentication` casts]** All handlers get a principal of a new type. → Handlers branching on `Type.PERSON` versus `SYSTEM_ADMIN_ACCOUNT` are reviewed (`Sessions.actor`, `RateLimitFilter`, `MeController`). The own-account operations are refused centrally.
- **[A dummy hash for unknown clients]** It costs the same as a real check, so an attacker can use up the semaphore with unknown clients. → The per-address throttle stops this after 100 failures.

## Migration Plan

- One forward migration V31 with an empty table.
- Rollback means redeploying the previous version. Tokens become unusable, since that version does not accept bearer tokens, and service accounts stay intact.
