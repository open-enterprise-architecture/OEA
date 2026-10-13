---
id: REQ-010
title: Username/password login with TOTP as second factor
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-01
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

# REQ-010: Username/password login with TOTP as second factor

## Statement

The system SHOULD allow persons to log in with username/password and a TOTP code as second factor when the OEA instance offers local authentication without passkey support.

## Rationale

Concept §21.8: TOTP as second factor is intended for environments that do not support WebAuthn/passkey (e.g., older clients). UC-01, Alternative A4.

## Context

Assumes that the person has previously set a password and registered a TOTP secret (enrollment is a separate use case).

## Type-Specific Fields

### For type=functional

**Inputs**:
- Username, password
- TOTP code (6 digits, time-based)

**Processing**:
- Check the password hash against the person's stored hash
- If the password is correct: request the TOTP code
- Verify the TOTP code against the person's registered secret (including time window tolerance)

**Outputs**:
- On successful verification of both factors: continue with person/role lookup (see REQ-001, step 6)

**Error Cases**:
- Wrong password → generic error message (see REQ-006), no indication whether the username exists
- Wrong or expired TOTP code → generic error message, no indication that the password was correct (prevents partial information disclosure)

## Acceptance Criteria

**AC1**:
- Given: a person with a correct password and a valid TOTP code
- When: they log in
- Then: they receive a session according to their active role assignments

**AC2**:
- Given: a correct password but a wrong TOTP code
- When: the login attempt is completed
- Then: no session is created, and the error message does not reveal that the password was correct

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: login attempts with correct/wrong password combined with correct/wrong TOTP code
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green, no partial information disclosure
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-022 (TOTP enrollment) and REQ-024 (password enrollment) must have been completed beforehand
- **Downstream effects**: none known
- **Conflicts**: REQ-006 (error messages must not leak password correctness)

## Risks of Non-Fulfillment

- Risk 1: Without TOTP as an option, only a plain password (REQ-011) remains for clients without WebAuthn support, which is weaker – medium severity

## Trade-offs

- vs. usability: the additional input step (TOTP code) increases login effort compared to a plain password or passkey

## Implementation Notes

- Store password hash (e.g., Argon2id) and TOTP secret separately from the business Person object (sensitive credential data)
- Provide rate limiting for TOTP attempts (brute-force protection given only a 6-digit code)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Alternative A4.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
