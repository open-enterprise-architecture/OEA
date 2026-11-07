---
id: REQ-187
title: HTTP security headers, CORS, CSRF and cookie attributes
type: interface
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-11-07
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-07
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-008
    - ADR-013
supersedes: []
superseded_by: []
---

# REQ-187: HTTP Security Headers, CORS, CSRF and Cookie Attributes

## Statement

The server **MUST** send `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` (or `strict-origin`), `frame-ancestors 'none'` and, for authenticated API responses, `Cache-Control: no-store`; CORS **MUST** allow only operator-configured origins (never `*` with credentials); state-changing requests authenticated by cookie **MUST** be protected by a CSRF token or a strict origin check; session cookies **MUST** be `HttpOnly`, `Secure`, `SameSite=Lax` or stricter and use the `__Host-` prefix.

## Rationale

The web portal may be exposed to the internet (owner decision Q2). A permissive CORS policy lets any website read the API with a logged-in user's cookies; missing framing protection enables clickjacking on actions such as go-live (REQ-086).

## Context

Source: security review SEC-24 (proposal PR-31); owner decision Q2 (priority must).

## Type-Specific Fields

### For type=interface

**Interface**: REST (HTTP)

**Specification**: `api/openapi.yaml` (security schemes for bearer and cookie authentication)

**Contracts**:

- Endpoint/path: all API and web portal responses
- Request schema: n/a
- Response schema: headers as stated above
- Error codes: 403 for CSRF or origin failures

## Acceptance Criteria

**AC1**:

- Given: any API and portal response
- When: headers are inspected
- Then: the listed headers are present with the specified values

**AC2**:

- Given: a request with `Origin: https://evil.example`
- When: sent with credentials
- Then: no `Access-Control-Allow-Origin` header is returned

**AC3**:

- Given: a cookie-authenticated `POST` without a valid CSRF token or with a foreign `Origin`
- When: processed
- Then: it is rejected with 403

**AC4**:

- Given: the session cookie after login to the web portal
- When: inspected
- Then: it has the `__Host-` prefix and the attributes `HttpOnly`, `Secure` and `SameSite=Lax` or `Strict`

## Verification Method

- [ ] Method: test (automated) + scan (e.g. OWASP ZAP baseline in CI)
- [ ] Pass criterion: AC1–AC4 green; no high findings in the baseline scan
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-129 (TLS)
- **Follow-on effects**: REQ-026 (relies on SameSite), REQ-165
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: cross-site data theft, clickjacking – medium to high on internet exposure

## Implementation Notes

- The origin of the Electron client (custom scheme) must be handled explicitly in the CORS configuration
- Web portal responses (AC1): implemented by the OpenSpec change `s9-portal-catalogs` – the backend serves the built portal from the same origin as the API with HSTS, `nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY` and its own content security policy (`default-src 'self'`, scripts `'self'` only, `frame-ancestors 'none'`; styles also `'unsafe-inline'` for the style elements of Nuxt UI); `index.html` is revalidated (`no-cache`), hashed assets are immutable. Tests: `PortalDeliveryTests`, `npm run test:csp`, `scripts/compose_smoke_test.sh`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-24 (PR-31), owner decision Q2 |
| 0.2.0 | 2026-11-07 | Solution Architect | Implementation note for the web portal responses (s9-portal-catalogs, WS-03) |
