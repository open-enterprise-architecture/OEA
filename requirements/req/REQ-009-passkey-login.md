---
id: REQ-009
title: Passkey login (local authentication without an external IdP)
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-24
last_updated: 2026-10-30
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

# REQ-009: Passkey login (local authentication without an external IdP)

## Statement

The system SHOULD allow persons to log in using a passkey (WebAuthn/FIDO2) when the OEA instance is operated without an external OIDC identity provider. For passkeys registered as a discoverable credential (resident key), login SHOULD be possible without prior entry of a username or password.

## Rationale

Concept §21.8 provides local authentication as an alternative to OIDC, with passkey as the recommended standard (passwordless, phishing-resistant). UC-01, Alternative A3. Addresses SH-06's anti-pattern boundary (no custom user management without a sensible alternative to classic passwords) and SH-03's self-hosting reality without a dedicated IdP team.

## Context

Assumes that the person has previously registered a passkey (enrollment is a separate use case, not part of UC-01).

## Type-Specific Fields

### For type=functional

**Inputs**:

- WebAuthn assertion (signed challenge-response) from the client authenticator, for discoverable credentials without prior username entry

**Processing**:

- System generates a WebAuthn challenge (for discoverable credentials without prior identity indication, the so-called usernameless flow)
- Client signs the challenge with the private key of the registered passkey
- System determines the person via the user handle contained in the assertion (discoverable credential) or via the previously entered identity (non-resident credential, fallback)
- System verifies the signature against the person's stored public key

**Outputs**:

- On successful verification: continue with person/role lookup (see REQ-001, step 6 of the Basic Flow)

**Error Cases**:

- No registered passkeys for the given identity → generic error message (see REQ-006), no indication of non-existence
- Signature verification failed → rejection, audit log entry (see REQ-005)

## Acceptance Criteria

**AC1**:

- Given: a person with a previously registered passkey
- When: they log in with this passkey
- Then: they receive a session according to their active role assignments

**AC2**:

- Given: a failed passkey verification
- When: the login attempt is completed
- Then: no session is created and the error message is indistinguishable from other error cases (e.g., E1, E2)

**AC3**:

- Given: a person with a passkey registered as a discoverable credential
- When: they log in
- Then: no prior entry of a username or password is required

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: WebAuthn test client with a registered virtual authenticator
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-023 (passkey enrollment must have been completed beforehand)
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a passkey option, only password-based login remains for self-hosting without an IdP – higher risk (phishing, weak passwords), medium severity

## Trade-offs

- vs. implementation effort: WebAuthn is more complex to implement than a simple password check, but offers significantly higher security

## Implementation Notes

- Realized by OpenSpec change `us-022-passkeys` with WebAuthn4J (ADR-048): `POST /auth/passkey/options` and `/auth/passkey/login` for discoverable passkeys (AC3); every failure answers like a wrong password and counts for the throttling per source address (AC2); the session is second-factor-verified with user verification (owner decision 2026-10-30).
- Public keys per passkey should be stored separately from the business Person object (sensitive credential data, see UC-01 Implementation Notes)
- Multiple passkeys per person should be supported (e.g., multiple devices)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-022-passkeys`
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, Alternative A3.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
| 0.2.0 | 2026-10-30 | requirements-engineer | Implementation note (change `us-022-passkeys`) |
