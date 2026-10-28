# Design

## Context

See proposal.md – Why. Relevant current state:

- **Login throttling** (`io.oea.app.login.LoginThrottle`) counts failures per login name and per source address in the table `auth_throttle` (fixed window from the first failure, lock in `locked_until`) and writes `auth.throttled` once per lock. `POST /setup` (`Bootstrapping`) does not use it; every rejected attempt is audited individually as `system.bootstrap-rejected`.
- **Request filters**: `OeaSessionFilter` authenticates inside the Spring Security chain; `RequestSizeLimitFilter` is a servlet filter registered after the security chain (spec `platform/request-limits`: authentication before the size check). There is no rate limiting.
- **Authorization**: `AuthorizationInterceptor` (L2, ADR-032 §8) answers 403 for undeclared handlers and missing permissions; `PermissionDeniedException` (contract conditions, e.g. `Catalogs`) and `SetupPhase.SecondFactorRequiredForAccessException` are answered with 403 by `ApiExceptionHandler`; `SelfAdministrationException` is answered with 403 and audited in `IdpGroupMappings`. Only the last one writes `authz.denied`.
- **Audit**: `AuditWriter.append`/`appendIndependently` take the source address and request ID from the current request; there is no scheduling in the backend.
- **Deployment**: one backend instance per installation (`compose.yaml`, ADR-027); horizontal scaling is not a v1.0 criterion (ADR-016).

## Goals / Non-Goals

**Goals:**

- One rate-limit mechanism for the REQ-186 limits that exist today, extensible for later limits (search, analytics) without a new filter
- Setup throttling with the existing per-address counter, no new table
- One flood-suppression component for security events, used by `authz.denied` and throttled setup attempts

**Non-Goals:**

- A rate-limit store shared by several backend instances
- Limits for unauthenticated public operations other than login and setup

## Architecture Constraints

- ADR-028: the rate-limit filter and its configuration live in `io.oea.config.web`; the suppression component lives in `io.oea.audit` next to `AuditWriter`; `Bootstrapping` (app layer) uses `LoginThrottle` (app layer)
- ADR-032 §8: L2 stays in `AuthorizationInterceptor`; auditing is added at the points that decide, not in a generic 403 hook
- ADR-033 §8: `authz.denied` and `auth.throttled` are records of the audit trail and its hash chain; the event catalog `docs/audit-event-catalog.md` is extended, no new event type is introduced
- ADR-013: the 429 responses are added to `api/openapi.yaml` first

## Decisions

### D1: Rate-limit counters in memory, per instance

The counters live in the backend's memory (a concurrent map of windows per principal and limit, idle windows removed periodically).

- **Alternative – counters in PostgreSQL** like `auth_throttle`: shared across instances, but one write per API request on a hot row; at 600 requests per minute and principal this costs more than the requests it protects. Login throttling counts only failures, which is why the table fits there.
- **Alternative – a library** (Bucket4j): adds a dependency for a counter of a few lines; a shared backend (Redis, JCache) would be the reason to use it and is not needed.
- **Consequence**: with several backend instances behind a load balancer the effective limit multiplies by the number of instances. This is documented in the operations page; a shared store follows only if horizontal scaling is supported.

### D2: Fixed window from the first request

Each principal and limit has a window of 60 seconds that starts with its first request; the window counts requests and is replaced by a new one after 60 seconds. `Retry-After` is the remaining time of the window, rounded up to whole seconds.

- **Alternative – token bucket**: smooths bursts, but "600 per minute" then means a refill of 10 per second, which the spec scenarios cannot state simply and which allows a sustained rate the operator did not configure as such.
- **Trade-off**: up to twice the limit in a short period across a window boundary; acceptable for protection against saturation, and identical to the window semantics of `LoginThrottle`.

### D3: Filter position and limits

A servlet filter `RateLimitFilter` is registered after the Spring Security chain and before `RequestSizeLimitFilter`. It counts only requests with an `OeaAuthentication` (unauthenticated requests are answered by the security chain or reach only public operations). The key is the principal type and ID (person or system admin account). Every request counts against the per-principal limit; `POST /api/v1/entities/batch` counts additionally against the batch limit (path-based like the size limits). A request is counted before it is processed; a rejected request does not count further.

Settings: `oea.web.rate-limit.requests-per-minute` (default 600) and `oea.web.rate-limit.entity-batch-per-minute` (default 10), validated to be ≥ 1 at startup.

The first rejection in a window writes `auth.throttled` with actor = the principal, details `scope: principal`, `limit: requests|entity-batch`, `limitPerMinute`; further rejections in the window are not audited (they are bounded by the limit itself).

### D4: Where `authz.denied` is written

`authz.denied` is written at each authorization decision:

| Decision point | Operation ID | Target ID |
|---|---|---|
| `AuthorizationInterceptor`, permission missing | handler method name (= operation ID) | path variable `id` if present |
| `ApiExceptionHandler` for `PermissionDeniedException` | handler method of the request | path variable `id` if present |
| `ApiExceptionHandler` for `SecondFactorRequiredForAccessException` | handler method of the request | – |
| `IdpGroupMappings`, separation of duties (existing) | as today, now through the suppression | as today |

Details: `operation`, `reason` (`missing-permission`, `condition`, `second-factor-required`, `self-administration`). The deny-by-default answer for handlers without a contract rule and the CSRF/`Origin` rejection are not audited: neither is a decision about a principal's permissions (the first is a programming error caught by `AuthorizationMatrixTests`, the second happens before the principal is known to be the caller).

### D5: Flood suppression

`AuditFloodGuard` in `io.oea.audit` decides per key (event type, actor, source address, operation) whether an event is written individually: the first 20 per minute window are, further ones are counted. A scheduled task (every 10 seconds, enabled with `@EnableScheduling` in a small configuration class) writes one summary event per closed window with a count > 0: the same event type, the same actor, `details.summary = true`, `details.suppressedCount`, `details.windowStart`, `details.windowEnd`, `operation`. Because the summary is written outside the request, `AuditWriter` gets an overload that takes the source address explicitly; the request ID of a summary is empty.

Used for `authz.denied` and for `system.bootstrap-rejected` with reason `throttled`. Counters are in memory like D1; on shutdown, open windows with suppressed counts are written by a `@PreDestroy` flush so no count is lost on a regular stop.

- **Alternative – write the summary lazily with the next event of the key**: no scheduler, but a burst followed by silence would never produce its summary (REQ-175 AC5 fails).

### D6: Setup throttling through `LoginThrottle`

`Bootstrapping.bootstrap` checks `LoginThrottle` for the source address before the transaction (only the address key; there is no login name), and records a failure with the address key only when the reason is `INVALID_SETUP_TOKEN`. `LoginThrottle` gets two methods for address-only use (`retryAfterSecondsForAddress`, `recordAddressFailure`) that reuse the existing window and lock logic, so login and setup failures of one address share one counter (REQ-168 table). A throttled attempt is rejected with the new reason `THROTTLED` (with the waiting time), audited as `system.bootstrap-rejected` with reason `throttled` through `AuditFloodGuard`, and mapped by `SetupController` to 429 with `Retry-After`. Reaching the threshold writes `auth.throttled` with scope `address` as today.

### D7: Contract check for 429

`scripts/check_openapi_authorization.py` gets a rule: every operation with `x-oea-authorization` other than `public` and without `x-oea-status: planned` declares a `429` response. `POST /setup` declares 429 explicitly.

## Data Model

No change. The setup throttling uses the existing table `auth_throttle` with the existing key `address:<ip>`; rate-limit and suppression counters are not persisted.

## API

Delta to `api/openapi.yaml`:

- `429: { $ref: '#/components/responses/TooManyRequests' }` on every implemented operation that requires authentication and does not declare it yet (6 operations declare it today)
- `POST /setup`: `429` with `TooManyRequests`
- `TooManyRequests` description: "Rate limit or throttling exceeded (REQ-186, REQ-168); `Retry-After` gives the seconds until the next request is accepted"; problem type `urn:oea:problem:too-many-requests`

## Risks / Trade-offs

- [Several backend instances multiply the limits] → documented in the operations page (D1); one instance is the supported deployment
- [Counters are lost on restart] → a restart resets the windows, which at most gives a principal one fresh window; suppressed counts are flushed on a regular shutdown (D5)
- [A shared NAT address throttles several users of the setup page] → setup is a one-time action of one operator; the waiting time is at most 10 minutes
- [Test instances drive one principal through many requests and batches per minute and would hit the limits] → the shared Cucumber instances raise both limits unless a feature sets its own; `RateLimitTests`, `BatchRateLimitTests` and the request-limits feature check the limits
- [Audit volume from denials] → bounded by the suppression (at most 20 events plus one summary per minute and key)
