# Security Review: Walking Skeleton (2026-10)

| Field | Value |
|---|---|
| Review type | Re-review of the implemented walking skeleton against the [requirements baseline review](requirements-baseline-2026-10.md); action A8 of the [walking-skeleton retrospective](../docs/retrospectives/2026-10-walking-skeleton.md) |
| Reviewer | Security Engineer (agent) |
| Date | 2026-10-22 |
| Branch / commit | `main` @ `b4b2409` |
| Scope | Code of `backend/` and `frontend/`, `api/openapi.yaml`, main specs `openspec/specs/`, `compose.yaml`, `compose.dev.yaml`, CI workflows, the eight archived walking-skeleton changes |
| Method | Static review of code and configuration, comparison with the baseline findings and proposed requirements; evidence from the automated tests and the end-to-end checks of changes 6 and 7 in the development environment. No penetration test, no dependency scan beyond Dependabot |
| Out of scope | Features not implemented yet (solutions, visibility configuration, service accounts, webhooks, analytics, rendering of user content, Electron client) |
| Result | **Sign-off granted for the walking skeleton**; WS-01 and WS-02 are resolved before the first public release, WS-03 before the web portal is delivered (see [Sign-off](#sign-off)) |

## Summary

The six findings that the baseline required to be resolved **before** the walking skeleton (condition 1: SEC-01, SEC-06, SEC-07, SEC-13, SEC-20, SEC-31) are resolved in the implementation and covered by tests. Authorization is deny-by-default and generated from the contract; sessions, OIDC and the audit trail follow ADR-032 to ADR-037 without shortcuts. The implemented features also resolve SEC-08, SEC-09 (local logins), SEC-10, SEC-26, SEC-30 and SEC-32.

The re-review found four new findings, none Critical or High: permission denials are not audited (WS-01), there are no rate limits and no general request-size limit (WS-02), the production delivery of the web portal and its security headers are undefined (WS-03), and the setup-token endpoint is not throttled (WS-04, already tracked as #61).

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 0 |
| Medium | 2 |
| Low | 2 |
| **Total** | **4** |

## Status of the Baseline Findings

### Condition 1 – Required Before the Walking Skeleton

| ID | Status | Evidence |
|---|---|---|
| SEC-01 | **Resolved** | ADR-032; every one of the 126 contract operations declares `x-oea-authorization` (`scripts/check_openapi_authorization.py` in CI); `AuthorizationInterceptor` denies handlers without a rule; `AuthorizationMatrixTests` and `UncoveredOperationTests` check 401/403 for every served operation; object-level rule for personal catalogs (404 for other persons, spec `catalog/catalog-definition`, `catalog/catalog-query`) |
| SEC-06 | **Resolved** | Spec `identity/sessions`: `__Host-` session cookie (HttpOnly, Secure, SameSite=Strict), idle and absolute timeouts (`Sessions`), logout and server-side revocation (`session.revoked`); IdP-derived roles re-evaluated within 15 minutes, fail closed (spec `identity/idp-roles`) |
| SEC-07 | **Resolved** | Spec `identity/oidc-login`: authorization code flow with PKCE (`code_verifier` stored encrypted in `OidcLoginFlow`), `state` and `nonce`, signature algorithm allowlist (`OidcClient.ALGORITHMS`), issuer guards G1–G3, person found by the immutable issuer/subject binding instead of e-mail |
| SEC-13 | **Resolved** | ADR-033, ADR-037; specs `platform/database-roles` and `audit/hash-chain`: owner/runtime role separation from the first migration, runtime role without UPDATE/DELETE on audit data (startup fails closed otherwise), hash chain with `oea audit verify` and checkpoints outside the database |
| SEC-20 | **Resolved** | ADR-036: real Keycloak in development and tests, no authentication stub; `TestOnlyAuthenticationArchitectureTests` keep test-only code out of the artifact; spec `platform/startup-safety`: development profile only with loopback issuers, known development secrets rejected outside `dev` |
| SEC-31 | **Resolved** | Authentication endpoints (setup, local login, TOTP, OIDC, logout, `me`) are in the contract with 401/403 responses; the CI check fails on a missing declaration |

### Further Findings Touched by the Skeleton

| ID | Status | Evidence / remaining work |
|---|---|---|
| SEC-02, SEC-03, SEC-04 | Not exploitable yet | The batch write rejects properties with restricted visibility (`EntityValidator`: "Properties with restricted visibility cannot be written yet"), the catalog query returns only `public` properties; enforcement on all read channels follows with REQ-160 (#68) and the visibility configuration (S10) |
| SEC-08 | **Resolved** | IdP group mappings accept only immutable UUID group identifiers (US-147 AC4); mapping one's own group is rejected (SoD-1, spec `identity/access-administration`) |
| SEC-09 | Resolved for logins | Throttling of local logins with audit (`auth.throttled`, spec `identity/local-login`), TOTP replay protection (`TotpCredential.lastUsedTimeStep`); the setup-token endpoint remains → WS-04 |
| SEC-10 | **Resolved** | SoD-8: privileged roles are effective only in sessions with a verified second factor (`PermissionResolver`, `AuthenticationMethods`); the setup-phase exception of ADR-042 ends with the first access of another identity |
| SEC-11 | Partially | Re-authentication within 5 minutes for provider changes and TOTP enrollment (`RecentAuthentication`); further sensitive actions follow with their features |
| SEC-14 | Partially | Login, logout, revocation, throttling, factor, mapping, provider, import and bootstrapping events are written; permission denials are not → WS-01 |
| SEC-21 | Resolved for packages | Package import with size limit (413, `PackageSizeLimitFilter`, default 10 MB), schema validation and error pointer; other request bodies → WS-02 |
| SEC-23 | Open | Batch limit 500 and page size 200 are enforced; per-principal rate limits of REQ-186 are missing → WS-02 |
| SEC-24 | Resolved for the API | HSTS, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, CORS allowlist, CSRF token plus `Origin` check (`CsrfOriginFilter`); the web portal's own delivery → WS-03 |
| SEC-26 | **Resolved** | Spec `platform/secret-storage`: secrets encrypted with an external key-encryption key, rotation (`oea secrets reencrypt`), key checked at startup |
| SEC-29 | Partially | License policy and check (ADR-043), dependency graph with Maven dependency submission, Dependabot, CI actions pinned by commit SHA; release integrity (checksums, provenance, SBOM) follows with the first release |
| SEC-30 | **Resolved** | `.github/workflows/screens.yml`: read-only render job, separate commit job limited to `docs/screens/SCR-*.png` (REQ-192 AC2) |
| SEC-32 | **Resolved** | `SECURITY.md` names GitHub private vulnerability reporting as channel |

The remaining baseline findings (SEC-05, SEC-12, SEC-15 to SEC-19, SEC-22, SEC-25, SEC-27, SEC-28) concern features that are not implemented yet; condition 2 of the baseline sign-off applies to them unchanged.

## Findings

### WS-01: Permission denials are not audited

| Field | Value |
|---|---|
| Severity | Medium |
| Location | `backend/src/main/java/io/oea/config/security/AuthorizationInterceptor.java:36` and `:50`; `docs/audit-event-catalog.md` (`authz.denied`) |
| Requirement | [REQ-175](../requirements/req/REQ-175-security-event-logging.md) AC1, baseline SEC-14 / PR-19 |

**Risk**: a compromised account with a low role (e.g. `viewer`) can probe administrative operations; every attempt is answered with 403, but no audit record is written – `authz.denied` is written only for the separation-of-duties rule of IdP group mappings. An incident responder cannot reconstruct the probing, and the audit event catalog describes `authz.denied` only for separation-of-duties refusals, which contradicts REQ-175.

**Recommendation**: write `authz.denied` (principal, operation ID, target ID if present, source address, request ID) where the interceptor and the handler-enforced conditions refuse a request, with the suppression rule of REQ-175 (individually up to 20 per minute and principal/source/operation, then one summary event per minute with the count); extend the event description in `docs/audit-event-catalog.md`; extend `AuthorizationMatrixTests` to assert the event.

### WS-02: No rate limits and no request-size limit outside the package import

| Field | Value |
|---|---|
| Severity | Medium |
| Location | `backend/src/main/java/io/oea/config/web/` (only `PackageSizeLimitFilter` for `POST /admin/metamodel/import`); no rate limiting in the backend |
| Requirement | [REQ-186](../requirements/req/REQ-186-rate-limits-resource-quotas.md), baseline SEC-23 / PR-30 |

**Risk**: an authenticated principal can send a JSON body of several hundred megabytes to `POST /entities/batch` or `POST /catalogs`; the body is parsed completely before the 500-entity limit or the field validation applies, which exhausts heap memory and affects all users of the instance. Without per-principal rate limits a single account can also saturate the database with catalog queries (each page runs a query and a count).

**Recommendation**: in the short term, a general request-body limit for JSON requests (filter like `PackageSizeLimitFilter`, configurable, e.g. 5 MB for regular operations and a larger value for the batch) answered with 413; before the first public release, the per-principal rate limits of REQ-186 with 429 and `Retry-After`.

### WS-03: Production delivery and security headers of the web portal are undefined

| Field | Value |
|---|---|
| Severity | Low |
| Location | `compose.yaml` (only `postgres`, `migrate`, `backend`); `frontend/` is served only by the Vite development server of `compose.dev.yaml` |
| Requirement | [REQ-187](../requirements/req/REQ-187-http-security-baseline.md) (HTTP security baseline), owner decision Q2 of the baseline (portal safe to expose to the internet) |

**Risk**: the backend sets a strict CSP for API responses, but the portal's HTML and assets have no defined server and therefore no defined `Content-Security-Policy` (`connect-src 'self'` is the premise of ADR-035 and of the icon bundling, #62), HSTS or caching rules. An operator who serves `frontend/dist` with an arbitrary web server would expose the portal without these headers.

**Recommendation**: before the web portal is delivered, decide how it is served (by the backend or a dedicated static server in `compose.yaml`) and specify its headers (CSP with `default-src 'self'`, `connect-src 'self'`, `frame-ancestors 'none'`, HSTS); test the headers like those of the API.

### WS-04: The setup-token endpoint is not throttled

| Field | Value |
|---|---|
| Severity | Low |
| Location | `POST /setup` (`backend/src/main/java/io/oea/api/setup/`, `app/bootstrapping/`) |
| Requirement | [REQ-168](../requirements/req/REQ-168-local-auth-throttling.md) (setup-token endpoint: 100 failed attempts per source address within 10 minutes), baseline SEC-09 / PR-12 |

**Risk**: low – the token has 256 bits, is single-use, expires and exists only until the bootstrapping; guessing it is infeasible. Unthrottled failed attempts still generate load and rejection audit events (`system.bootstrap-rejected`) without bound.

**Recommendation**: implement the throttling of REQ-168 for the endpoint as planned in S3 (issue #61).

## Accepted Risks (No Finding)

- **Shared catalog namespace**: a name collision reveals that some catalog with that name exists, even another person's personal catalog; required by REQ-043, documented in the spec `catalog/catalog-definition` and the business object.
- **Password-only system admin during the setup phase**: ADR-042, throttled and audited; ends irreversibly with the first access of another identity.

## What Is Done Well

- Authorization generated from the contract with a test per served operation – an unprotected endpoint cannot be merged unnoticed.
- No authentication stub anywhere: development and tests log in through a real Keycloak, including TOTP and `amr`.
- Audit integrity independent of the application: role separation, hash chain, external checkpoints and a fail-closed startup check.
- Secrets never in plain text in the database; development secrets are hash-listed and rejected outside `dev`.
- Restricted properties are rejected on write instead of being stored without protection – the visibility feature can be added without a data migration of leaked values.

## Sign-off

**Sign-off for the walking skeleton: granted.** Condition 1 of the baseline review is met. Further conditions:

1. WS-01 and WS-02 are resolved before the first public release (v0.x tag); the request-size limit of WS-02 is a small change and is recommended for package S2b.
2. WS-03 is resolved before the web portal is delivered to operators.
3. WS-04 is resolved in S3 as planned (#61).

Tracking (owner decision 2026-10-22): WS-01 [#75](https://github.com/open-enterprise-architecture/OEA/issues/75) (S3), WS-02 request-size limit [#73](https://github.com/open-enterprise-architecture/OEA/issues/73) (S2b) and rate limits [#74](https://github.com/open-enterprise-architecture/OEA/issues/74) (S3), WS-03 [#76](https://github.com/open-enterprise-architecture/OEA/issues/76) (S9), WS-04 [#61](https://github.com/open-enterprise-architecture/OEA/issues/61) (S3).
4. Conditions 2 and 3 of the baseline review apply unchanged to the features that follow.

## Status Update 2026-10-28

| Finding | Status | Resolution |
|---|---|---|
| WS-01 | **Resolved** | Change `s3-throttling-and-denials` (#75): `authz.denied` at every authorization decision with principal, operation ID, target ID, source address and request ID; flood suppression per REQ-175 AC5; `AuthorizationMatrixTests` asserts the event for every implemented operation (spec `platform/authorization`) |
| WS-02 | **Resolved** | Request-size limit in `s2b-backend-hardening` (#73); per-principal request and batch rate limits with 429 and `Retry-After` in `s3-throttling-and-denials` (#74, spec `platform/request-limits`); counters per backend instance (design D1) |
| WS-03 | Open | Planned for S9 (#76) |
| WS-04 | **Resolved** | Change `s3-throttling-and-denials` (#61): failed setup-token attempts count towards the per-address throttling of local logins; a throttled address gets 429 without the token being evaluated (spec `identity/bootstrapping`) |

Condition 1 of the sign-off ("before the first public release (v0.x tag)"): the tag `v0.1.0` was created on 2026-10-27 before WS-01 and the rate limits of WS-02 were resolved. That tag publishes only the user manual; no GitHub release and no container image was published. With this update, WS-01 and WS-02 are resolved before the first release that distributes runnable software.
