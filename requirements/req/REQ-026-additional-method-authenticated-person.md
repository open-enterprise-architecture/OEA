---
id: REQ-026
title: Set up an additional authentication method for a logged-in person
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-25
last_updated: 2026-10-30
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-026: Set up an additional authentication method for a logged-in person

## Statement

The system SHOULD allow an already authenticated person to set up an additional local authentication method via their own security settings without needing an enrollment token; the session MUST serve as proof of authentication.

## Rationale

Not every enrollment need arises at first access (via invitation link). An already active person (logged in via OIDC or an existing local method) must be able to add a local method afterwards – e.g., a second passkey for a new device or TOTP as a fallback if the primary passkey is lost. UC-03 Alternative A3. Without this capability, recovery after device loss would only be possible through administrative intervention.

## Context

Requires a valid session (UC-01 Basic Flow or A1–A5 successful). No enrollment token required; instead CSRF protection of the session. The method-specific execution (TOTP, passkey, password) corresponds to REQ-022, REQ-023, and REQ-024 respectively, but without the token validation step at the beginning.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Authenticated session (cookie or bearer token)
- Selected new method and method-specific inputs (identical to REQ-022/023/024)

**Processing**:

- System checks that a valid session exists for the requesting person
- System shows the methods available for the instance that have not yet been set up
- Method-specific enrollment flow per REQ-022, REQ-023, or REQ-024 (identical, without the token step)
- CSRF protection: session-bound token or SameSite cookie policy

**Outputs**:

- List of available methods not yet set up
- Success message after completed enrollment
- Error message for invalid session or unavailable method

**Error Cases**:

- Session expired or invalid → redirect to login (UC-01)
- All methods already set up → informational note, no error
- Method not enabled by the instance → method is not offered

## Acceptance Criteria

**AC1**:

- Given: Kurt is logged in and has not yet set up a TOTP method
- When: he adds TOTP in the security settings and completes the setup
- Then: TOTP is persisted as an additional method; no enrollment token was needed

**AC2**:

- Given: Kurt is logged in and wants to add a second passkey
- When: he performs the passkey registration
- Then: the new public key is persisted; both passkeys are usable for login

**AC3**:

- Given: Kurt has already set up passkey, TOTP, and password
- When: he opens the security settings
- Then: the system shows that all methods are already set up (no error)

**AC4**:

- Given: a request to the enrollment endpoint without a valid session
- When: the request arrives
- Then: it is rejected; redirect to login

**AC5**:

- Given: a request without a CSRF token (in a POST form-based flow)
- When: the request arrives
- Then: it is rejected with a corresponding error

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: logged-in person adds methods; test unauthenticated access to the endpoint
- [x] Measurement tool: test suite of the enrollment module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: UC-01 (login must work); REQ-022, REQ-023, or REQ-024 (method-specific enrollment)
- **Downstream effects**: recovery after device loss (made easier, but not a complete recovery UC)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without this function, persons cannot set up new credentials after device loss; increased admin effort for token reissuance – medium severity

## Trade-offs

- vs. security: enrollment without an enrollment token minimally increases the attack surface (the session must be compromised); mitigated by CSRF protection and session validation

## Implementation Notes

- Realized for TOTP by OpenSpec change `us-021-person-totp` (AC1, AC3, AC4; AC5 through the existing CSRF filter): the same enrollment operations as at the first login, the list of own second factors on the profile page. AC2 (second passkey) follows with S5 change 4.
- Profile/security settings page: authenticated area, same structure as the enrollment page, but without a token URL
- CSRF: SameSite=Strict on the session cookie is sufficient for browser protection; an additional CSRF token on forms is defense in depth
- The endpoint should be the same enrollment code path as token-based enrollment (only the authentication gate step differs) to avoid duplication

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-021-person-totp`
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-03 Alternative A3. Prioritized as `should` instead of `must` because the primary enrollment path (invitation token) suffices; A3 is a substantial improvement of the user experience and the recovery scenario, but not a hard requirement for initial operation.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft from UC-03 |
| 0.2.0 | 2026-10-30 | requirements-engineer | Implementation note (change `us-021-person-totp`) |
