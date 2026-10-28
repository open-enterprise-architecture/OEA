# Proposal

## Why

The security re-review of the walking skeleton signed off under the condition that two Medium findings are resolved before the first public release: permission denials leave no audit trace (WS-01, issue [#75](https://github.com/open-enterprise-architecture/OEA/issues/75)), and one principal can saturate the instance because there are no per-principal rate limits (WS-02, issue [#74](https://github.com/open-enterprise-architecture/OEA/issues/74)). The setup-token endpoint is the only local authentication endpoint without throttling (WS-04, issue [#61](https://github.com/open-enterprise-architecture/OEA/issues/61)). All three are counting and auditing problems at the request boundary and are resolved together as the first change of S3, before S3 adds remote bootstrapping and further endpoints.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-02: System admin bootstrapping](../../../requirements/use-cases/UC-02-system-admin-bootstrapping.md) (setup-token throttling); the rate limits and denial audit are cross-cutting |
| User Stories | – (security findings, no story) |
| Requirements | [REQ-186: Rate limiting and resource quotas](../../../requirements/req/REQ-186-rate-limits-resource-quotas.md) AC1; [REQ-168: Brute-force protection for local authentication](../../../requirements/req/REQ-168-local-auth-throttling.md) AC3 (setup-token endpoint); [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md) AC1, AC5 |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (§8 L2), [ADR-033: Audit trail](../../../adrs/ADR-033-audit-trail-hardening.md) (§8 event catalog), [ADR-013](../../../adrs/ADR-013-api-style.md) (spec-first contract), [ADR-029](../../../adrs/ADR-029-cucumber-test-framework.md) (Cucumber) |
| Findings | [Security re-review](../../../security-reviews/walking-skeleton-2026-10.md) WS-01, WS-02 (rate limits), WS-04; issues #61, #74, #75 |
| Sprint / Milestone | S3 / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name follows the package (`s3-`) instead of a user story, like `s2b-backend-hardening`, because it resolves security findings, not a story.

**Realigned REQ sections** (non-binding details made concrete by this change):

- REQ-186 target values: "Requests per principal 600 / min" and "Batch write requests per service account 10 / min" are implemented per authenticated principal; service accounts do not exist yet (US-142), so the batch limit applies to every principal. Search, traversal, event stream and analytics limits apply when those endpoints exist.
- REQ-168 "Failed attempts per source address … setup-token endpoints": failed setup-token attempts count towards the same per-address counter as failed local logins.
- REQ-175 AC5: the summary event is an `authz.denied` record with the suppressed count, not a separate event type.

## What Changes

- **Per-principal rate limits** (#74): every authenticated request counts against a per-principal limit of 600 requests per minute; `POST /entities/batch` additionally counts against 10 requests per minute. A request above a limit is answered with 429, the problem type `urn:oea:problem:too-many-requests` and `Retry-After`, before the request body is read; other principals are unaffected. Reaching a limit writes one `auth.throttled` event with scope `principal` per limit window. Both limits are configurable by the operator. The contract declares the 429 response on every implemented authenticated operation, checked in CI.
- **Setup-token throttling** (#61): a failed setup-token attempt counts towards the existing per-source-address counter of local logins (100 failures within 10 minutes). While the address is throttled, `POST /setup` is answered with 429 and `Retry-After` without evaluating the token; the throttled attempt is audited as `system.bootstrap-rejected` with reason `throttled`, and reaching the threshold writes `auth.throttled` with scope `address` as for logins. The setup page shows the waiting time.
- **Audit of permission denials** (#75): every 403 caused by an authorization decision writes `authz.denied` with principal, operation ID, target ID if present, source address and request ID – the contract permission check, permission checks inside handlers, the second-factor requirement of the setup phase and, as before, the separation-of-duties rule. Per principal, source address and operation, the first 20 denials per minute are recorded individually; further denials of that minute are recorded as one summary event with their count. The audit event catalog describes the extended event.
- **Frontend**: a 429 answer shows "Too many requests. Try again in N seconds." on the setup page and the catalog pages instead of a generic failure.

## Out of Scope

- Search, traversal-depth, event-stream and analytics limits of REQ-186 (the endpoints do not exist yet; the limiter is built so they can be added)
- A rate-limit store shared between several backend instances: OEA runs one backend instance per installation; the limits are per instance (design D1)
- 403 answers that are not authorization decisions: the CSRF/`Origin` check and the deny-by-default for undeclared handlers remain unaudited (design D4)
- Throttling of unauthenticated public endpoints other than login and setup (health, version, setup status)
- Remote bootstrapping (US-014) and the other UC-02 stories (second S3 change)

## Capabilities

### New Capabilities

- none

### Modified Capabilities

- `platform/request-limits`: per-principal rate limits with 429 and `Retry-After`, configurable, audited when reached
- `identity/bootstrapping`: throttling of the setup-token endpoint per source address; throttled attempts audited
- `platform/authorization`: permission denials are audited as `authz.denied` with flood suppression

## Impact

- `backend/`: new rate limiter and filter in `io.oea.config.web` (after authentication, before the request-size filter reads the body); `io.oea.app.login.LoginThrottle` used by `io.oea.app.bootstrapping.Bootstrapping`; `AuthorizationInterceptor`, `ApiExceptionHandler` (`PermissionDeniedException`, `SecondFactorRequiredForAccessException`) and `IdpGroupMappings` write `authz.denied` through a new suppression component in `io.oea.audit`; `SetupController` maps the throttled rejection to 429
- `api/openapi.yaml`: `429` (`TooManyRequests`) on every implemented authenticated operation and on `POST /setup`; `scripts/check_openapi_authorization.py` checks it
- `frontend/`: `SetupPage.vue`, `CatalogPage.vue`, `CatalogsPage.vue` show the waiting time of a 429
- Docs: `docs/audit-event-catalog.md` (`authz.denied`, `auth.throttled` scope `principal`, `system.bootstrap-rejected` reason `throttled`); `manual/administration/operations.md` and `manual/de/administration/operations.md` (rate-limit settings); `manual/setup/first-administrator.md` and its German page (throttled setup); REQ-186, REQ-168, REQ-175 realignment notes; security re-review WS-01/WS-02/WS-04 status
- No Flyway migration (the setup throttle uses the existing `auth_throttle` table), no data model change, no screen added
