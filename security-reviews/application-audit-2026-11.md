# Security Review: Application Audit (2026-11)

| Field | Value |
|---|---|
| Review type | Code and configuration audit of the implemented application after sprints S1–S11; follow-up of the [walking-skeleton review](walking-skeleton-2026-10.md) and the [requirements baseline review](requirements-baseline-2026-10.md) |
| Reviewer | Security Engineer (agent) |
| Date | 2026-11-13 |
| Branch / commit | `main` @ `1e8ebe46` (feat(solution): warn of conflicts with parallel solutions, US-044) |
| Scope | `backend/` (authentication, sessions, passkeys, OIDC, authorization per operation, property visibility, solution endpoints – deltas, diff, landscape, conflicts –, SQL construction, CSRF, CORS, headers, error handling, audit chain, secrets, Flyway migrations V1–V28 and audit V1–V5), `frontend/` (XSS sinks, session and token storage), `api/openapi.yaml` (authorization declarations), `compose.yaml`, `compose.dev.yaml`, `backend/Dockerfile`, `.github/workflows/` |
| Method | Static review of code and configuration on `main`; comparison of `x-oea-authorization` in the contract with the interceptor and the service-level checks; tracing of every read path of the solution endpoints against `PropertyAccess`; search of the frontend history for `v-html`, `innerHTML`, storage APIs and navigation sinks. Nothing was built or run; no penetration test, no dependency vulnerability scan (Dependabot alerts are the only source) |
| Out of scope | Operations declared but answered by `NotImplementedInterceptor` (service accounts, webhooks, analytics, audit API, entity CRUD outside the batch); Electron client (does not exist yet); the user manual site |
| Result | **No Critical or High findings.** One Medium and one Low finding; sign-off see [Sign-off](#sign-off) |

## Summary

The application keeps the strong baseline of the walking skeleton: every contract operation carries an authorization declaration that the interceptor enforces deny-by-default, sessions are opaque 256-bit tokens in a `__Host-` cookie with a signed double-submit CSRF token, OIDC uses PKCE, nonce and an issuer/subject binding, passkeys require user verification and a growing counter, and the audit trail is append-only with a database hash chain and encrypted values of restricted properties. SQL is built from constants and typed identifiers only; all user input is bound.

The newer solution endpoints were checked in depth. Deltas, diff and the baseline entity apply the reader's visibility per property and – for `connection-scoped` properties – per entity; the conflict lists only reveal solutions that every holder of `entity:read` can list anyway. One gap remains: values of `connection-scoped` properties in deltas `new` are shown to every caller with an account link (AA-02).

The main new finding is operational but has a security impact: the backend uses the TCP peer address as the client address, while the only supported deployment puts it behind a reverse proxy. The per-address login throttling therefore counts all users as one address, so an unauthenticated attacker can block every local login of the instance, and the audit trail records the proxy address for every event (AA-01).

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 1 |
| Low | 1 |
| **Total** | **2** |

## Status of Previous Findings

### Walking-skeleton review (2026-10)

| ID | Status | Evidence |
|---|---|---|
| WS-01 | **Resolved** (verified) | `AuthorizationInterceptor.java:93-98` records `authz.denied` with operation and target; `ApiExceptionHandler.java:418-426` for handler-enforced conditions |
| WS-02 | **Resolved** (verified) | `RequestSizeLimitFilter` (default 1 MB, batch and package 10 MB) and `RateLimitFilter` (600 requests and 10 batches per minute and principal, 429 with `Retry-After`); see T-1 for a residual path-matching question |
| WS-03 | **Resolved** (verified) | The backend serves the portal (`PortalConfiguration`, `backend/Dockerfile:37`) with `PORTAL_CONTENT_SECURITY_POLICY` (`default-src 'self'; script-src 'self'; … frame-ancestors 'none'`), HSTS, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`; `style-src 'unsafe-inline'` is documented in `SecurityConfiguration.java:46-49` and acceptable while `script-src` stays `'self'` |
| WS-04 | **Resolved** (verified), but see AA-01 | `Bootstrapping.java:111,125` counts wrong setup tokens per source address; the source address is the proxy's behind a reverse proxy |

### Requirements baseline (2026-10)

Only findings whose features are now implemented were re-checked; the others are unchanged (condition 2 of the baseline sign-off applies).

| ID | Status | Evidence / remaining work |
|---|---|---|
| SEC-02 | **Resolved for the implemented read channels** | Catalog columns, filters and sorts (`CatalogQueries.java:490,516-532`, `CatalogQueryStore.java:211-218` scope wrapped into every value and predicate), baseline entity (`SolutionLandscape.java:87`), delta changes (`SolutionDeltas.java:616`), diff (`SolutionDiff.java:249`), audit values encrypted (`AuditWriter.java:93-117`). Gap for deltas `new`: AA-02 |
| SEC-03 | **Resolved** | `visibility:configure` separate from `metamodel:write`; connections of a scoping type need `visibility-scope:assign` (`PropertyVisibility.java:203-214`); own account link refused (`PersonEntityLinks.java:70-74`); `writeRoles ⊆ readRoles` enforced (`PropertyVisibility.java:164-166`); self-benefiting changes flagged in the audit |
| SEC-04 | Partially | Batch write and deltas check write roles and scope (`PropertyWriteAccess`); restore and `PUT` are not implemented yet |
| SEC-05 | **Resolved by decision Q1** | No anonymous operation besides health, setup, login and client compatibility (contract lines 3711–4500) |
| SEC-09 | Resolved, weakened by AA-01 | Per-account backoff and per-address limit (`LoginThrottle`); the per-address part is ineffective behind a proxy |
| SEC-11 | Partially | Unchanged (re-authentication for TOTP enrollment and provider changes) |
| SEC-12 | Implemented, not re-verified | Password resets, second-factor reset and break-glass CLI exist (`PasswordResets`, `AdminRecovery`); not reviewed in this audit |
| SEC-18 | Not exploitable yet | No `v-html`, `innerHTML` or rich content rendering in production frontend code (history search of `frontend/src`); Markdown/diagram rendering not implemented |
| SEC-19 | Not applicable yet | No Electron code in the repository |
| SEC-21 | Resolved for packages | YAML via Jackson 3 `jackson-dataformat-yaml` (no arbitrary type instantiation), size limit 10 MB |
| SEC-29 | Partially | All actions pinned by SHA, base images pinned by digest, `npm ci --ignore-scripts` in the Dependabot job; release integrity still pending |
| SEC-30 | **Resolved** | `screens.yml` and `dependabot-licenses.yml`: build jobs read-only, commit jobs copy only allow-listed files and push with a GitHub App token to the PR branch; fork PRs get no commit job |

## Findings

### AA-01: Client address is the reverse proxy's – per-address login throttling blocks all local logins, audit addresses are meaningless

| Field | Value |
|---|---|
| Severity | **Medium** (CVSS 3.1 `AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L` – 5.3) |
| Location | `backend/src/main/java/io/oea/api/identity/IdentityController.java:266` (local, TOTP and passkey logins) and `:129` (OIDC callback); `backend/src/main/java/io/oea/api/setup/SetupController.java:93`; `backend/src/main/java/io/oea/audit/AuditWriter.java:154`; `backend/src/main/java/io/oea/app/login/LoginThrottle.java:32-34,61,67`; `backend/src/main/resources/application.yml` (no `server.forward-headers-strategy`); `compose.yaml:63` ("Publish behind a TLS reverse proxy only") |
| Requirement | REQ-168 (per-address throttling), REQ-175 (source address in security events), ADR-036 §8 |

**Description**: every component takes the client address from `HttpServletRequest.getRemoteAddr()`. The supported deployment (`compose.yaml`, `manual/setup/installation.md:58`) binds the backend to `127.0.0.1` and requires a TLS reverse proxy in front of it. No forwarded-header handling is configured anywhere in the repository (a history search for `forward-headers`, `X-Forwarded-For`, `RemoteIpValve` and `internal-proxies` finds nothing), and Spring Boot does not enable it by default outside cloud platforms. Behind the proxy, the TCP peer of every request is the proxy (or the Docker gateway), so all users share one throttle key `address:<proxy>`.

**Attack scenario**: an unauthenticated attacker on the internet (owner decision Q2: the portal is internet-facing) sends 100 failed local logins, invalid passkey assertions or wrong setup tokens within 10 minutes. `LoginThrottle.recordFailure` / `recordAddressFailure` locks the shared address key for 10 minutes; `retryAfterSeconds` (`LoginThrottle.java:61`) and `retryAfterSecondsForAddress` (`:67`, used by `PasskeyLogin`) then answer every local, TOTP and passkey login of every user with 401 and `Retry-After`, including the local system admin account. Repeating about 100 requests every 10 minutes keeps all local authentication unavailable; only OIDC logins and the break-glass CLI still work. In addition, every audit event (`audit.events.source_ip`) and every `authz.denied` / `auth.login-failed` record names the proxy, so an incident responder cannot attribute probing or password spraying to a source (REQ-175), and the flood suppression of denials groups all users together.

A related effect to confirm in a deployment test: `CsrfOriginFilter.originAllowed` (`CsrfOriginFilter.java:56`) compares `Origin` with `request.getScheme()://getServerName():getServerPort()`, which behind a TLS-terminating proxy is `http://…:80` instead of `https://…:443`. Browsers send `Origin` on same-origin `POST`, so operators may be pushed to work around rejected requests – e.g. by enabling forwarded headers for all sources, which would let attackers spoof `X-Forwarded-For` and bypass the per-address limit entirely.

**Recommendation**:

1. Configure forwarded headers explicitly and safely: `server.forward-headers-strategy=native` with `server.tomcat.remoteip.internal-proxies` (and `trusted-proxies`) restricted to the proxy address(es), set by an operator variable in `compose.yaml` (e.g. `OEA_TRUSTED_PROXIES`, default: the Compose gateway / loopback only). Never trust `X-Forwarded-For` from arbitrary peers.
2. Document the proxy requirements in `manual/setup/installation.md` (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` set and overwritten by the proxy).
3. Add a startup warning (or guard outside `dev`) when `oea.web.public-url` is `https` but no trusted proxy is configured.
4. Tests: a login throttling test with two different forwarded client addresses behind a trusted proxy (one locked, the other still accepted), and one with a spoofed `X-Forwarded-For` from an untrusted peer (ignored); an Origin check test behind a proxy with `X-Forwarded-Proto: https`.

### AA-02: Values of connection-scoped properties in deltas `new` are not redacted for callers with an account link

| Field | Value |
|---|---|
| Severity | **Low** (CVSS 3.1 `AV:N/AC:H/PR:L/UI:N/S:U/C:L/I:N/A:N` – 3.1) |
| Location | `backend/src/main/java/io/oea/app/solution/SolutionDeltas.java:576` (`access.readable(property)`); `backend/src/main/java/io/oea/app/metamodel/PropertyAccess.java:124-126`; consumers `SolutionDiff.java:112-116` (`created`), `SolutionController.java:248` (delta responses); contributing: `backend/src/main/java/io/oea/app/entity/EntityValidator.java:115-119` |
| Requirement | REQ-127, REQ-160; spec `solution/solution-deltas` line 73 ("When the deltas are read, a value the reader may not read SHALL be returned as redacted") |

**Description**: values of deltas `new` are redacted with `PropertyAccess.Snapshot.readable(propertyDefId)`. For a `connection-scoped` property this method returns `true` for every caller with an effective account link (`scope(...).isPresent()`), because its contract is "readable per entity – the caller checks the scope". The entity of a delta `new` does not exist yet, so no scope check can follow; the value is returned unredacted. All other read paths of the solution feature use `readableFor(property, entityId)`, which is correct.

A delta `new` can hold such a value in two ways: (a) the property was `public` or `role-restricted` when the delta was captured and the security administration later switches it to `connection-scoped` (the typical move to protect it); (b) `EntityValidator` applies the mapping's default value for a new entity to every mapped property, writable or not (`EntityValidator.java:115-119`), so a default of a `connection-scoped` property is stored in the delta (low impact on its own, as defaults are visible through the metamodel).

**Attack scenario**: a solution architect with the write role of the `role-restricted` property "Salary band" captures a delta `new` "Contractor X" with the value `E3`. Security administration changes "Salary band" to `connection-scoped` (scoped by a reporting-line connection). A viewer with `entity:read` whose account is linked to any person element calls `GET /api/v1/solutions/{id}/deltas` or `GET /api/v1/solutions/{id}/diff` and reads `E3`, although no entity is in their scope.

**Recommendation**: for values without an entity, treat `connection-scoped` properties as readable only through the override role list, e.g. a method `readableWithoutEntity(propertyDefId)` = `override || allowed(propertyDefId, false)`, used in `SolutionDeltas.values`. Do not apply defaults of properties the writer may not write to a delta `new` (or redact them consistently). Add a scenario to `solution-deltas.feature`: a caller with an account link reads a delta `new` whose property became `connection-scoped` → `redacted: true` without value; plus the positive case for a holder of an override role.

## Checked Without Finding

- **Conflicts (US-044)**: `SolutionConflicts.of` returns solution name, status, owner ID/name and delta type of other active solutions. All solutions and their deltas are readable with `entity:read` (`listSolutions`, `getSolution`, `listSolutionDeltas`), the same permission that guards every endpoint returning conflicts, and conflicts contain no property values. No information beyond what the caller can already read is disclosed.
- **Diff and baseline**: current values are redacted per entity with `readableFor` (`SolutionDiff.java:249`, `SolutionLandscape.java:87`); a change is redacted if either the recorded change or the current value is unreadable (`SolutionDiff.java:169-173`); the drift flag is computed only for readable values, so it is no oracle.
- **Modified deltas**: write roles are checked before the before-values are recorded (`SolutionDeltas.java:467`); `writeRoles ⊆ readRoles` is enforced, so the "nothing changes" answer (422) is no oracle on unreadable values; hidden changes of other editors are kept on edit (`SolutionDeltas.java:279-282`); audit events carry field names only.
- **Object scoping of deltas**: update and delete select by `id` and `solution_id` (`SolutionDeltas.java:249,375-379`); a delta of another solution answers 404.
- **SQL construction**: dynamic parts are constants, enum-derived column names, integers from the validated contract (`LIMIT`/`OFFSET`), or UUID/int literals (`ScopeStore.inScope`); cursors are HMAC-protected and type-parsed before binding (`CatalogCursors`); filter values are bound parameters with `ESCAPE` for `LIKE`.
- **Authorization contract**: every operation declares `x-oea-authorization`; public operations are limited to health, setup, login flows, client compatibility and the client-credentials token endpoint (planned); privileged administration requires dedicated permissions.
- **Sessions and CSRF**: `__Host-OEA_SESSION` (HttpOnly, Secure, SameSite=Strict), only the SHA-256 of the token stored, idle and absolute timeouts, inactive persons rejected on every request (`Sessions.java:150-161`); CSRF token HMAC-derived from the session token and kept in memory by the frontend (`frontend/src/api/session.ts`); CORS empty by default.
- **OIDC and passkeys**: state hashed and single-use, browser binding cookie, encrypted PKCE verifier, `returnTo` restricted to relative paths and always prefixed with the public URL; WebAuthn with UV and UP required, origin and RP ID checked, counter regression rejected.
- **Error responses**: domain exceptions mapped to RFC 9457 problems with fixed texts; other exceptions fall back to the Spring Boot error response without message or stack trace.
- **Frontend**: no `v-html`/`innerHTML` in production code; `sessionStorage` holds only the expanded navigation nodes; `window.location.assign` only with the server-provided OIDC start URL.
- **Database roles and migrations**: V25–V28 grant `oea_app` only `SELECT, INSERT, DELETE` and column-level `UPDATE` on the new tables; nothing on `audit` beyond V5's documented `UPDATE (wrapped_key, kek_id)` for key rotation.
- **CI/CD**: top-level `permissions: contents: read` in every workflow; `persist-credentials: false`; no `pull_request_target`; secrets only in commit jobs that run no package code and whose `git add`/`git status` checks allow only `docs/screens/SCR-NNN-(light|dark).png` or `THIRD-PARTY-LICENSES.md`.
- **Container and Compose**: non-root user `10001`, read-only JAR, images pinned by digest; every secret mandatory without default; dev ports bound to `127.0.0.1`.

## To Check (Not Verified)

| # | Topic | Why it is not a finding yet | Suggested check |
|---|---|---|---|
| T-1 | Batch rate limit by exact URI | `RateLimitFilter` and `RequestSizeLimitFilter` compare `request.getRequestURI()` (raw) with `/api/v1/entities/batch`. Spring MVC matches decoded path segments, so a percent-encoded variant such as `/api/v1/entities/b%61tch` may reach the batch handler while counting only against the general request limit (600/min instead of 10 batches/min). The size limit is then the stricter 1 MB default, so only the batch rate limit would be bypassed | Integration test with an encoded path; if confirmed, key the limits on the matched operation ID (e.g. in a `HandlerInterceptor`) or on `UrlPathHelper`'s decoded lookup path (Low) |
| T-2 | Cost of per-entity scope checks | `readableFor` runs one `inScope` query per value of a `connection-scoped` property and entity in the diff and the baseline entity (up to 200 deltas per page) | Measure a diff page with several connection-scoped properties; batch the scope lookup like `ScopeStore.inScope(anchor, type, ids)` if it is slow |
| T-3 | GitHub App "OEA license bot" | The app token has `contents: write` on the repository; the workflows push only to the PR branch, but if the app is a bypass actor of the `main` ruleset, a leaked token could push to `main` | Confirm in the repository settings that the app is not in any bypass list and that its private key is a Dependabot/Actions secret limited to these workflows |
| T-4 | Dependency vulnerabilities | No scan was run in this audit | Review open Dependabot alerts for Spring Boot 4.1.2, WebAuthn4J, Bouncy Castle and the frontend lockfile before the next release |

## Observations (No Severity)

- **Any holder of `solution:write` changes any solution**: deltas of solutions owned by others can be captured, edited and removed (stated in the contract for `createSolutionDelta`; audited). Acceptable in project mode because nothing reaches the catalog; before the go-live (S14) writes deltas into the catalog, decide whether owner or approval checks and a re-check of the go-live actor's write roles are required.
- **`oea_app` may rewrite wrapped audit value keys** (`migration-audit/V5__audit_value_keys.sql`): a compromised runtime could make the encrypted values of restricted properties in the audit unreadable (not alter the hash chain). Documented trade-off for KEK rotation; consider running `oea secrets reencrypt` under a separate role later.
- **`listSolutions` is unpaged**: fine at current scale, but it returns all solutions with owner names in one response.

## What Is Done Well

- Authorization is generated from the contract and denied by default; condition checks in services are audited like interceptor denials.
- Property visibility is enforced inside the SQL for catalog values, filters and sorts (scope wrapped into every expression), not only on the response.
- The solution feature applies visibility per entity on every channel except the one noted in AA-02, and records before values server-side instead of trusting the client.
- Cursors are HMAC-protected with a per-start key and type-checked before they reach the database.
- The CI design keeps third-party code away from write tokens and limits what the bot commits to an allow-list of paths.

## Status Update 2026-11-14

| Finding | Status | Resolution |
|---|---|---|
| AA-01 | **Resolved** | Change `s11-review-hardening` ([#266](https://github.com/open-enterprise-architecture/OEA/pull/266), merge commit `2826f0cc`): `server.forward-headers-strategy=native` installs Tomcat's `RemoteIpValve`; trusted proxies default to loopback and the private networks (owner decision 2026-11-13) and are replaced by `OEA_TRUSTED_PROXIES`, an invalid list stops the start; the login throttling, the audit source address and the Origin check use the forwarded client address, scheme, host and port (spec `platform/deployment`). Recommendation 3 (startup warning without a trusted proxy) does not apply, because the default list always covers the Compose gateway; the manual names `OEA_TRUSTED_PROXIES` for a backend reachable from a private client network. Tests: two forwarded addresses throttled separately (100 and 99 failures), a spoofed `X-Forwarded-For` from an untrusted peer ignored, the Origin check behind a TLS proxy (`ForwardedHeadersTests`, `UntrustedPeerTests`, `platform/reverse-proxy.feature`) |

With AA-01 resolved, condition 1 of the sign-off is met; WS-04 and SEC-09 are no longer weakened behind a reverse proxy.

## Sign-off

**Sign-off for the application state S1–S11: granted**, under the following conditions:

1. **AA-01** is resolved before the first release that operators deploy behind a reverse proxy (any runnable release), because it makes the brute-force protection of REQ-168 a denial-of-service lever and empties the source address of every audit record.
2. **AA-02** is resolved before the go-live of solutions (S14) or earlier with the next solution change; it requires a visibility reconfiguration after capture to be exploitable.
3. T-1 and T-3 are checked by the next audit; conditions 2 and 3 of the baseline review continue to apply to features not yet implemented (service accounts, webhooks, analytics, rich content, Electron).
