---
id: REQ-022
title: TOTP secret enrollment (setting up an authenticator app)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
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

# REQ-022: TOTP secret enrollment (setting up an authenticator app)

## Statement

The system SHOULD allow setting up a TOTP secret (RFC 6238) as a local authentication method by generating a secret, displaying it as a QR code (key URI format) and in plain text, accepting a verification code, and persisting the secret in encrypted form only if verification is passed.

## Rationale

TOTP is the most widely supported local two-factor method without dependency on specific hardware or platform APIs. It is the Basic Flow in UC-03 and the fallback option for environments in which WebAuthn (passkey) is not available. Addresses SH-06's requirement for local user management without an external IdP and SH-03's self-hosting scenario.

## Context

Triggered as a required action during the first login (UC-03 Basic Flow): after a successful password check, the system detects that 2FA is enforced (REQ-020) and no TOTP credential exists yet, and directly redirects the user to the setup page. Alternatively, TOTP can be added via the profile settings of an already logged-in person (REQ-026). The secret is used at login by REQ-010 (TOTP login). The secret may be displayed in plain text only once; afterwards it is no longer retrievable.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Triggered enrollment step "TOTP" (after token validation or session authentication)
- Verification code: 6-digit TOTP code, entered by Kurt from his authenticator app

**Processing**:
- System generates a cryptographically random TOTP secret (≥ 160 bits, RFC 4226 compliant)
- System creates a key URI (`otpauth://totp/...`) and renders a QR code from it
- System displays the QR code and the plain-text secret (for manual entry) exactly once
- When Kurt enters the verification code: the system checks it against the generated secret with time tolerance (±1 step of 30 s, RFC 6238)
- On successful verification: the system persists the secret in encrypted form (AES-256 or equivalent) and links it to the Person object

**Outputs**:
- QR code and plain-text secret (once, during enrollment)
- Success message after verification is passed
- On failed verification: error message without any hint of the secret value, nothing persisted

**Error Cases**:
- Wrong or expired verification code (E2 from UC-03) → error message, secret is not persisted, token remains valid; Kurt can try again
- Secret generation failed (technical error) → generic error message, enrollment is not completed

## Acceptance Criteria

**AC1**:
- Given: Kurt has chosen TOTP as the method
- When: the system generates the secret
- Then: a QR code (key URI format) and the plain-text secret are displayed

**AC2**:
- Given: Kurt scans the QR code and enters the current TOTP code
- When: the code is correct (within time tolerance ±1 step)
- Then: the secret is persisted in encrypted form and linked to Kurt's person; login with this TOTP is possible immediately (UC-01 A4)

**AC3**:
- Given: Kurt enters a wrong TOTP code
- When: verification fails
- Then: no secret is persisted, the enrollment token remains valid, Kurt can try again

**AC4**:
- Given: enrollment has been completed
- When: the system writes log entries
- Then: the TOTP secret is contained in logs neither in plain text nor as a hash

**AC5**:
- Given: the TOTP secret is persisted
- When: it is stored in the database
- Then: it is encrypted (not readable in plain text without the decryption key)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: TOTP enrollment with a virtual authenticator (RFC 6238-compliant test implementation); check against DB contents
- [x] Measurement tool: test suite of the enrollment module; DB inspection in the test
- [x] Pass criterion: AC1–AC5 green; login after enrollment (AC2) works via REQ-010
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: UC-03 required action trigger (first login with initial password, REQ-024) or REQ-026 (session authentication for profile settings)
- **Downstream effects**: REQ-010 (TOTP login requires an enrolled secret)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without TOTP enrollment, no 2-factor login for environments without WebAuthn hardware – increased risk from password-only login, medium severity
- Risk 2: TOTP secret in plain text in logs → compromise of the second factor, severe (mitigated by AC4)

## Trade-offs

- vs. REQ-023 (passkey): TOTP is less phishing-resistant than a passkey, but has no platform API dependency – a sensible complement, not a replacement

## Implementation Notes

- Key URI format: `otpauth://totp/{issuer}:{account}?secret={BASE32}&issuer={issuer}&algorithm=SHA1&digits=6&period=30` (standard for authenticator app compatibility)
- Time tolerance: ±1 window (30 s) is the RFC 6238 recommendation; larger tolerance weakens security
- Secret encryption: the symmetric key (AES-256) must be managed separately from the database (e.g., environment variable or KMS); no hard-coding
- Secret and person in a dedicated credential table (not in the Person object itself, cf. UC-01 Implementation Notes)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-03 Basic Flow (steps 3–8) and Exception Flow E2.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft from UC-03 |
