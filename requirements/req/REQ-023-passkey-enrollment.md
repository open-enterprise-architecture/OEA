---
id: REQ-023
title: Passkey enrollment (WebAuthn credential registration)
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

# REQ-023: Passkey enrollment (WebAuthn credential registration)

## Statement

The system SHOULD allow registering a passkey (WebAuthn Level 2 / FIDO2) as a local authentication method by generating a registration challenge, verifying the authenticator response, and persistently linking the public key of the registered credential to the Person object.

## Rationale

Passkey is the recommended standard for local authentication according to concept §21.8 (passwordless, phishing-resistant). UC-03 Alternative A1. Without passkey enrollment, UC-01 Alternative A3 (passkey login) is not usable. Passkeys are the most secure local option for SH-03 and SH-06 in the self-hosting scenario without an external IdP.

## Context

Triggered as a required action during the first login (UC-03 Alternative A1): after a successful password check, the system detects that 2FA is enforced (REQ-020) and no passkey exists yet. Alternatively, a passkey can be added via the profile settings of an already logged-in person (REQ-026). The registered public key is used at login by REQ-009 (passkey login). The private key never leaves the authenticator.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Triggered enrollment step "Passkey"
- Authenticator response: `PublicKeyCredential` (contains attestation object, client data JSON, credential ID)

**Processing**:

- System generates `PublicKeyCredentialCreationOptions` (contains challenge, RP ID, user handle, list of algorithms)
- Client passes the options to the authenticator (platform or roaming); the authenticator creates a new key pair
- Authenticator returns `PublicKeyCredential`; the system verifies:
  - Signature over client data JSON
  - RP ID and origin correct
  - Challenge match (replay protection)
  - Attestation (format: `none` as default; other formats optional)
- On successful verification: the system persists public key, credential ID, and signature counter, linked to the person; multiple credentials per person are supported

**Outputs**:

- Success message with a note on successful registration
- On error: generic error message, no credential is stored

**Error Cases**:

- User cancels the authenticator confirmation (E3 from UC-03) → no credential persisted, token remains valid, error message
- RP ID/origin mismatch → technical error, no credential persisted
- Authenticator not compatible → generic error message; Kurt can choose an alternative method

## Acceptance Criteria

**AC1**:

- Given: Kurt has chosen passkey as the method and has a compatible authenticator
- When: he completes the authenticator confirmation
- Then: the public key is stored persistently and login via passkey (UC-01 A3) is possible immediately

**AC2**:

- Given: Kurt cancels the authenticator confirmation
- When: the cancellation occurs
- Then: no credential is stored, the enrollment token remains valid

**AC3**:

- Given: a person already has a registered passkey
- When: they register another passkey (e.g., second device)
- Then: both credentials are stored and individually usable for login

**AC4**:

- Given: a registration response with a wrong origin or RP ID
- When: the system processes it
- Then: it is rejected, no credential persisted

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: virtual WebAuthn authenticator implementation (e.g., the test utilities of the Java WebAuthn server library used, or a browser stack with virtual authenticator)
- [x] Measurement tool: test suite of the enrollment module
- [x] Pass criterion: AC1–AC4 green; login after enrollment (AC1) works via REQ-009
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: UC-03 required action trigger (first login with initial password, REQ-024) or REQ-026 (session authentication for profile settings)
- **Downstream effects**: REQ-009 (passkey login requires a registered public key)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without passkey enrollment, only password/TOTP as local options – lower security (susceptibility to phishing), medium severity
- Risk 2: A faulty origin/RP ID check allows cross-origin credential registration, severe (mitigated by AC4)

## Trade-offs

- vs. implementation effort: WebAuthn registration is more complex than TOTP/password; worthwhile because of significantly higher security

## Implementation Notes

- Realized by OpenSpec change `us-022-passkeys` without enrollment tokens: registration in the running session with re-authentication, discoverable credential, user verification and attestation `none`; challenge, origin, relying party ID and signature are verified before the passkey is stored (AC4); several passkeys per person (AC3); a cancellation stores nothing (AC2).
- Attestation: `none` as default (no attestation CA operation needed, better data protection); `direct` or `indirect` configurable if needed
- Algorithms: support at least ES256 (P-256) and RS256 (RSA-2048); order in `PublicKeyCredentialParameters` prefers ES256
- Signature counter: store and check on every login attempt (replay protection, UC-01 A3)
- User handle: opaque, not personally identifiable (e.g., UUID of the person), must not be the email address (FIDO2 spec)
- Credential table separate from the Person object; columns: `credential_id`, `public_key`, `sign_count`, `aaguid`, `person_id`, `created_at`, `last_used_at`

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-022-passkeys`
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-03 Alternative A1 and Exception Flow E3. That multiple passkeys per person (AC3) must be supported follows from the open question in UC-03; stated here as a requirement because a lost device would otherwise lead to a complete lockout.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft from UC-03 |
| 0.2.0 | 2026-10-30 | requirements-engineer | Implementation note (change `us-022-passkeys`) |
