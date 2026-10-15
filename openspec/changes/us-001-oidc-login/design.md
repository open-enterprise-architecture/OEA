# Design

## Context

Change 3a built the session infrastructure (opaque cookie `__Host-OEA_SESSION`, stateless Spring Security, CSRF and origin checks, contract-generated authorization with a 60 s permission cache), the provider, person, binding and mapping administration, and the secret encryption with the key-encryption key. The tables `sessions` (incl. `auth_provider_id`, `idp_refresh_token_encrypted`, `idp_claims_evaluated_at`, `idp_groups_claim_status`), `person_identities` (`UNIQUE (issuer, subject)`) and `idp_group_mappings` exist (V3). `session_idp_roles` is modeled in `docs/data-model.puml` but not migrated. The startup guards still read the property list `oea.security.oidc.providers`, which nothing else uses.

The development realm (`dev/keycloak/realm-oea-dev.json`) has the confidential client `oea-portal` (PKCE `S256` required, redirect URIs `http://localhost:5173/*` and `http://localhost:8080/*`), group identifiers as UUIDs in the `groups` claim, and an `amr` protocol mapper; the authentication flows carry no method references yet. Tests already start one Keycloak Testcontainer per JVM (`OeaKeycloak`) and log in headlessly through the real login forms (`OidcLogin`, ADR-036 §5).

## Goals / Non-Goals

**Goals:**

- A browser login through any enabled stored provider that ends in the same session type as the local login, without IdP tokens in the browser
- Roles from IdP group mappings that follow the IdP within 15 minutes and fail closed
- Startup guards and tests that work only with stored providers

**Non-Goals:**

- Generic support for several redirect hosts per instance (one public URL)
- Background refresh of idle sessions; re-evaluation happens on the next request
- Userinfo or introspection calls; the ID token is the only identity source

## Architecture Constraints

- ADR-006: generic OIDC client against OIDC Core, RFC 6749, RFC 8414 discovery; Entra ID and Authentik are the reference integrations, Keycloak is the development and test IdP (ADR-036)
- ADR-028: endpoints in `io.oea.api`, login and role derivation in `io.oea.app`, entities in `io.oea.core`, Spring Security wiring in `io.oea.config.security`; controllers see only app-layer views
- ADR-032 §7, SoD-1, SoD-8, DM-10, Q16, Q17: IdP group mappings, self-administration, MFA for privileged roles, roles recorded per session, 15-minute re-evaluation, fail closed
- ADR-033: every security event in the hash-chained audit log, pseudonymous actors
- ADR-036 §5–§7: real flows only in system tests, no profile-conditional `JwtDecoder`/`SecurityFilterChain` beans, startup guards before the web server accepts connections
- ADR-042: unchanged; persons only get access through bindings and mappings created after the setup phase
- REQ-188: IdP refresh tokens and PKCE verifiers encrypted with `SecretCipher`

## Decisions

### D1 – Own authorization code flow on Spring Security's OAuth2 building blocks

The login runs in two public endpoints (`startOidcLogin`, `completeOidcLogin`) of a controller in `io.oea.api.identity` backed by an `OidcLogin` service. It uses `spring-security-oauth2-client` and `spring-security-oauth2-jose` as libraries: discovery through `ClientRegistrations.fromIssuerLocation` (cached per provider, refreshed when the provider changes), the token request through the `RestClient`-based authorization code token client, and ID token validation through a `NimbusJwtDecoder` per provider with the default OIDC validators plus `nonce` and `azp`.

Alternative: `http.oauth2Login()` – rejected, it keeps the authorization request in the `HttpSession` and creates its own authentication; the session model of change 3a is stateless and opaque. Alternative: Nimbus OAuth SDK directly – more code for discovery, token client and validation that Spring already maintains.

Signature algorithms are restricted to the asymmetric `RS*`, `PS*` and `ES*` families in code; there is no configuration that enables `none` or `HS*` (G1 by construction).

### D2 – Login state in the database, bound to the browser

`startOidcLogin` creates a row in `oidc_login_states` (hash of `state`, provider, `nonce`, PKCE verifier encrypted, relative return path, expiry 10 minutes) and sets the cookie `__Host-OEA_LOGIN` (random value, `HttpOnly`, `Secure`, `Path=/`, `SameSite=Lax`, 10 minutes) whose hash is stored with the row. `completeOidcLogin` accepts the callback only if `state` and the login cookie belong to the same unexpired row, deletes the row (single use), and clears the cookie. `SameSite=Lax` is required because the callback is a top-level navigation from the IdP; the session cookie stays `Strict` and is set in the callback response.

Alternative: state in a signed cookie only – no single-use guarantee, and the PKCE verifier would travel in the cookie.

### D3 – Public URL and callback through the frontend origin

New property `oea.web.public-url` (e.g. `https://oea.example.org`; development `http://localhost:5173`). The redirect URI is `<public-url>/api/v1/auth/oidc/callback`, so the callback and the session cookie belong to the origin of the web portal (the Vite dev server proxies `/api`). After the callback the browser is redirected to `<public-url><returnTo>` (default `/`), or to `<public-url>/login?error=login-failed`. Guard G5 is extended: outside `dev`, the public URL must be `https` and not loopback; it is required as soon as an enabled provider exists.

### D4 – Provider source: database only

The property list `oea.security.oidc.providers` and its binding class are removed. A new startup check `StoredProviderGuard` (a `SmartInitializingSingleton` in `io.oea.config.startup`, so it runs after the data source is available and before the web server starts) reads the enabled providers and applies G2/G3 outside `dev` and the inverse loopback rule in `dev`. Disabled providers are ignored and never offered. The development realm is provisioned through the admin API by `scripts/dev_seed.py` (see D11).

### D5 – Identity binding and person status

The pair (`iss` of the validated token, `sub`) is looked up in `person_identities`; the issuer of the token must equal the stored provider's issuer. Only persons with status `invited` or `active` get a session; `invited` becomes `active` in the login transaction (REQ-007, `status_changed_*` set, audit event `person.activated`). `person_identities.last_login_at` is updated. E-mail claims are not read.

### D6 – Session of a person and its lifetime

The session row gets `person_id`, `auth_method = oidc`, `auth_provider_id`, `second_factor_verified` from `amr` (D8), `idp_refresh_token_encrypted`, and the new column `idp_token_expires_at` (from `refresh_expires_in` when the IdP sends it, otherwise NULL). The session filter ends a session with reason `idp-refresh-failed` when `idp_token_expires_at` has passed; together with the existing idle and absolute checks this implements "the first of the three ends the session". Access tokens are not stored.

### D7 – Role derivation and re-evaluation on the request path

At login and at every re-evaluation the `groups` claim of the ID token is classified (`present`, `missing`, `truncated` for Entra overage indicators `_claim_names`/`hasgroups`), the matching active mappings of the provider's issuer are written to `session_idp_roles` (replace), the group identifiers are stored in the new column `sessions.idp_group_identifiers` (needed for SoD-1, D9), and `auth.idp-roles-derived` is audited when the set changes.

Re-evaluation happens synchronously in the session filter when `idp_claims_evaluated_at` is older than 15 minutes: the session row is locked (`FOR UPDATE`), the refresh grant is sent with the decrypted refresh token, the rotated refresh token is stored, and the new ID token (or, if the IdP returns none, the access token's claims are not used – the derived roles are dropped) is evaluated as at login. Results:

| IdP answer | Effect |
|---|---|
| New ID token | Roles replaced, `idp_claims_evaluated_at` = now |
| `invalid_grant` (expired, revoked) | Session ended, reason `idp-refresh-failed`, request 401 |
| Network error, 5xx, no refresh token stored | IdP-derived roles removed, `auth.idp-groups-unusable`, retry at the next request after 1 minute |

`PermissionResolver` resolves the roles of a person session as the union of the rows in `session_idp_roles` joined with mappings that are still active and not deleted; with the existing 60 s cache a deleted mapping stops applying within 60 s (US-147 AC7).

Alternative: a scheduled job refreshing all IdP sessions every few minutes – it would also refresh idle sessions and would keep IdP sessions alive without user activity. Rejected.

### D8 – Second factor from `amr`

`second_factor_verified` is true when the `amr` claim contains `mfa`, or a password value together with one of `otp`, `hwk`, `swk`, `sc`, `fpt`, `face`, `iris` (RFC 8176). A privileged role (ADR-032 admin-class permissions) derived for a session without it is reported with `effective: false`, `notEffectiveReason: mfa-required` and grants nothing. The development realm gets method references on its authentication flow (`pwd` for the password form, `otp` for the OTP form) so that `amr` is emitted; a test asserts the claims of `dev-system-admin` and `dev-admin-no-mfa`.

### D9 – SoD-1 check on mapping writes

`IdpGroupMappings.create/update` reject with `403`, reason `self-administration`, when the caller is a person whose session's `idp_group_identifiers` (issuer of the session's provider) contain the group identifier of the request; audit event `authz.denied`. The system admin account has no IdP groups and is never affected.

### D10 – Failures and audit

All login failures redirect to `/login?error=login-failed` (one message, REQ-006); the reason goes only to the audit log: `auth.login-failed` with `mechanism: oidc`, `providerId` and `reason` (`state-invalid`, `provider-error`, `cancelled`, `token-invalid`, `identity-unknown`, `person-not-active`). An unreachable provider at start redirects to `/login?error=provider-unavailable` (technical message, retry possible). Successful logins write `auth.login` with `method: oidc`, `providerId`, `secondFactor`.

### D11 – Development seed

`scripts/dev_seed.py` (Python standard library only) logs in as the local system admin, enrolls and confirms TOTP if the setup phase still needs it (printing the secret once), creates the provider `http://localhost:8180/realms/oea-dev` with client `oea-portal` and its development secret, one person with a binding per realm user (subjects from the realm file), and the eight group mappings of ADR-036 §4. It is idempotent and talks only to the API, so no code path exists only for `dev` (ADR-036 §4).

### D12 – Frontend

The login page loads `listLoginProviders` and shows one button per provider above the local login; a button navigates to `/api/v1/auth/oidc/{providerId}/start?returnTo=…`. `?error=login-failed` and `?error=provider-unavailable` are shown as messages. The profile lists roles with their source and the `mfa-required` reason, and the REQ-003 notice when no role is effective.

## Data Model

Changes to `docs/data-model.puml` and `business-objects/`:

- `sessions`: new `idp_token_expires_at TIMESTAMPTZ` (expiry of the IdP refresh token, NULL if unknown) and `idp_group_identifiers TEXT[]` (group identifiers of the last evaluation, only for SoD-1 and access review; NULL for local logins)
- `session_idp_roles` (modeled): created as modeled (`session_id`, `idp_group_mapping_id`, `role_id`, `derived_at`)
- New `oidc_login_states`: `state_hash BYTEA PK`, `browser_binding_hash BYTEA NN`, `auth_provider_id UUID NN FK`, `nonce TEXT NN`, `code_verifier_encrypted TEXT NN`, `code_verifier_key_id TEXT NN`, `return_to TEXT NN`, `created_at`, `expires_at`; rows deleted on use and by the existing cleanup of transient tables
- Business objects: `session.md` (IdP lifetime, derived roles), `auth-provider.md` (database is the only source)

Flyway `db/migration/V4__oidc_login.sql` (owner `oea_owner`): the three changes above, grants for `oea_app` (`SELECT, INSERT, UPDATE, DELETE` on `session_idp_roles` and `oidc_login_states`).

## API

Delta to `api/openapi.yaml`:

- `listLoginProviders` `GET /auth/oidc/providers` (public): `[{id, name}]` of enabled providers
- `startOidcLogin` `GET /auth/oidc/{providerId}/start?returnTo=` (public): `302` to the IdP, sets `__Host-OEA_LOGIN`; `returnTo` must be a relative path
- `completeOidcLogin` `GET /auth/oidc/callback?code=&state=&error=` (public): `302` to the portal; sets `__Host-OEA_SESSION` on success
- `Me.roles[]`: `RoleGrant.source` already allows `idp` and `mappingId`; no schema change
- `IdpGroupMappingWrite` description already names the `403` for SoD-1; problem property `reason: self-administration` documented
- `AuthProvider` description: the provider list is the only login source; `scopes` must include `offline_access` for Entra ID to obtain refresh tokens

## Risks / Trade-offs

- [Synchronous refresh adds IdP latency to one request per 15 minutes per session] → row lock prevents parallel refreshes; timeout 5 s, on timeout the "unreachable" path applies
- [IdPs without refresh tokens (missing `offline_access`)] → derived roles drop after 15 minutes (fail closed), documented per provider in `docs/operations.md`
- [`SameSite=Lax` login cookie] → valid only 10 minutes, single use, carries no authority; the session cookie stays `Strict`
- [Keycloak `amr` depends on flow configuration] → realm change plus a test asserting `amr` for TOTP and password-only users
- [Removing `oea.security.oidc.providers`] → only the development configuration used it; startup ignores unknown properties, the operations guide states the change

## Open Questions

- None that change the plan
