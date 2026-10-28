# Tasks

## 1. Contract

- [x] 1.1 Extend `api/openapi.yaml` as in the API section of the design: `429` with `TooManyRequests` on every implemented operation that requires authentication and on `POST /setup`, updated `TooManyRequests` description with the problem type `urn:oea:problem:too-many-requests`; verify Redocly lint, the backend compiles and `npm run generate:api` succeeds in `frontend/` and `manual/`
- [x] 1.2 Add the 429 rule of D7 to `scripts/check_openapi_authorization.py` with a unit test for a compliant and a non-compliant operation; verify the script passes on the extended contract and fails when one 429 is removed

## 2. Flood suppression of security events (D5)

- [x] 2.1 Add `AuditFloodGuard` in `io.oea.audit` (20 individual events per key and minute window, counted remainder), the scheduled summary writer with `@EnableScheduling` in its own configuration class, the shutdown flush and the `AuditWriter` overload with an explicit source address; verify with unit tests using a fixed `Clock` (20 individual, 30 suppressed → one summary with count 30; two operations counted separately; no summary for a window without suppression; flush on shutdown) and an integration test that a summary record is part of the hash chain (`oea audit verify` passes)

## 3. Audit of permission denials (D4)

- [x] 3.1 Write `authz.denied` through `AuditFloodGuard` in `AuthorizationInterceptor` (missing permission), in `ApiExceptionHandler` for `PermissionDeniedException` and `SecondFactorRequiredForAccessException`, and route the existing separation-of-duties event of `IdpGroupMappings` through the guard; details `operation`, `reason`, target ID from the path variable `id`; verify with integration tests for the spec scenarios "Viewer calls an administrative operation" (`listIdpGroupMappings`) and "Denial inside a contract condition", and that the 403 bodies are unchanged
- [x] 3.2 Extend `AuthorizationMatrixTests` to assert one `authz.denied` with the operation ID for every call answered with 403 (spec scenario "Every implemented operation audits its denial"); verify it passes for all implemented operations
- [x] 3.3 Cucumber scenarios in `platform/authorization.feature` (new) for "Viewer calls an administrative operation" and "Fifty denials within one minute" (with a short window configured for the test instance); verify they pass through `CucumberIT`
- [x] 3.4 Update `docs/audit-event-catalog.md`: `authz.denied` for all authorization decisions with `reason`, `operation`, target and the summary fields; verify markdown lint passes

## 4. Per-principal rate limits (D1–D3)

- [x] 4.1 Add `RateLimiter` (fixed windows per principal and limit, idle cleanup) and `RateLimitFilter` in `io.oea.config.web`, registered after the security chain and before `RequestSizeLimitFilter`, with the settings `oea.web.rate-limit.requests-per-minute` and `oea.web.rate-limit.entity-batch-per-minute` validated at startup; 429 with problem type `urn:oea:problem:too-many-requests` and `Retry-After`; `auth.throttled` with scope `principal` once per window; raise both limits for the shared Cucumber instances of `RepositoryInstance` unless a feature sets its own (the MockMvc tests stay within the defaults); verify with unit tests of the window logic (fixed `Clock`) and integration tests for the spec scenarios "Principal above the limit", "Other principals unaffected", "Requests accepted again after the waiting time", "Eleventh batch within a minute", "Limit lowered by the operator" and "Throttled principal in the audit trail"
- [x] 4.2 Extend `platform/request-limits.feature` with "Principal above the limit" and "Eleventh batch within a minute" on an instance with lowered limits; verify they pass through `CucumberIT`
- [x] 4.3 Show "Too many requests. Try again in N seconds." for a 429 in `CatalogPage.vue` and `CatalogsPage.vue` (from `Rejection.retryAfterSeconds`); verify with Vitest cases in `CatalogPage.spec.ts` and `CatalogsPage.spec.ts`
- [x] 4.4 Document the rate-limit settings, the per-instance counting of D1 and the 429 answer in `manual/administration/operations.md` and `manual/de/administration/operations.md`; update `docs/audit-event-catalog.md` (`auth.throttled` scope `principal`); verify `npm test` and the manual coverage check in `manual/` pass

## 5. Setup-token throttling (D6)

- [x] 5.1 Add the address-only methods to `LoginThrottle`, check and record them in `Bootstrapping` (only `INVALID_SETUP_TOKEN` counts), the reason `THROTTLED` audited as `system.bootstrap-rejected` through `AuditFloodGuard`, and the 429 mapping with `Retry-After` in `SetupController`; verify with integration tests for the spec scenarios "Setup attempts above the threshold", "Other addresses unaffected", "Setup accepted again after the waiting time" and "Throttled attempt in the audit trail" (no record contains the token), and that the existing login throttling tests pass unchanged
- [x] 5.2 Extend `identity/bootstrapping.feature` with "Setup attempts above the threshold"; verify it passes through `CucumberIT`
- [x] 5.3 Show "Too many failed attempts. Try again in N seconds." for a 429 on `SetupPage.vue`; verify with a Vitest case in `SetupPage.spec.ts` (spec scenario "Setup page shows the waiting time")
- [x] 5.4 Describe the throttled setup in `manual/setup/first-administrator.md` and `manual/de/setup/first-administrator.md`; update `docs/audit-event-catalog.md` (`system.bootstrap-rejected` reason `throttled`); verify the manual coverage check passes

## 6. Wrap-up

- [x] 6.1 No user story is referenced, so no story status changes; add realignment notes to REQ-186 (implemented limits, batch limit per principal), REQ-168 (setup failures share the address counter) and REQ-175 (summary event); record WS-01, WS-02 and WS-04 as resolved in `security-reviews/walking-skeleton-2026-10.md`; reference issues #61, #74 and #75 in the PR; verify markdown lint passes
- [x] 6.2 `docs/data-model.puml`, `business-objects/` and `docs/screens/SCREENS.md` are unchanged (no data or screen change); regenerate `requirements/traceability.md` with `python3 scripts/generate_traceability.py` and verify `--check` passes
- [x] 6.3 Run `python3 scripts/validate_links.py`, the OpenAPI checks, `scripts/sync_agent_config.py --check`, `openspec validate s3-throttling-and-denials` and `./mvnw verify` and verify all pass
