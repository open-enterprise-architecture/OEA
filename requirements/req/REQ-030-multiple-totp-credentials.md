---
id: REQ-030
title: Multiple active TOTP credentials per person
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
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-030: Multiple active TOTP credentials per person

## Statement

The system MUST allow a person to hold several active TOTP credentials (`LocalCredential.type=totp`) at the same time; at login (REQ-010) a valid TOTP code from any of the person's active TOTP credentials MUST be accepted; each TOTP credential MUST carry a user-assignable label so that the person can tell their credentials apart.

## Rationale

A person may want to use several devices (smartphone, tablet, hardware token) as TOTP authenticators. Especially for persons with administrative roles, a backup authenticator for recovery scenarios makes sense. The previous restriction to one active TOTP per person (lifted by this requirement) does not reflect actual practice: replicating the same secret onto several devices (which means secret sharing) is worse than separate registrations with their own secret per device. This behavior already corresponds to the passkey model in the same BO (multiple passkeys per person are already allowed; BR-02 was more restrictive for TOTP).

## Context

Applies to `LocalCredential.type=totp`. Password credentials (`type=password`) remain limited to at most one per person (BR-03 in `local-credential.md`). The requirement lifts the previous BR-02 ("at most one active TOTP per person") and replaces it with: "multiple active TOTP credentials per person permitted; each with its own secret and label". A new TOTP NO LONGER automatically invalidates existing ones (previous behavior removed). The person manages their TOTP credentials themselves (deletion via profile settings, separate UC).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Label (user-assigned, optional; max. 255 characters, e.g., "iPhone – Google Authenticator", "YubiKey TOTP")
- TOTP enrollment flow as defined in REQ-022 and UC-03

**Processing during enrollment**:

- A new TOTP credential is created and linked to the person; existing TOTP credentials remain active
- No implicit revocation of the previous TOTP; only the user can explicitly delete credentials

**Processing during login (REQ-010)**:

- System loads all active TOTP credentials of the person
- Verifies the entered code against each credential sequentially (order: most recently used first, for performance)
- As soon as a credential verifies the code: login successful; update `lastUsedAt` of this credential
- No "which credential" field needed for the user at login – the code is the only input

**Error Cases**:

- Label empty → permitted; the system assigns a default label (e.g., "TOTP authenticator 2026-06-25")
- Label longer than 255 characters → validation error at enrollment
- No active TOTP credential → login behavior per REQ-010 (no second factor available)

## Acceptance Criteria

**AC1**:

- Given: a person has two active TOTP credentials (different secrets, different devices)
- When: a valid TOTP code from the second credential is entered
- Then: the login is successful; `lastUsedAt` of the second credential is updated

**AC2**:

- Given: a person has an existing active TOTP credential
- When: they set up another TOTP credential via UC-03 A3
- Then: they have two active TOTP credentials afterwards; the first one is not revoked

**AC3**:

- Given: two active TOTP credentials
- When: the person opens their security settings
- Then: both credentials are shown with their label and `createdAt`/`lastUsedAt`

**AC4**:

- Given: a person sets up a TOTP without a label
- When: the credential is created
- Then: it receives a sensible default label (e.g., "TOTP authenticator" + date)

**AC5**:

- Given: two active TOTP credentials
- When: an invalid code (not attributable to any of the credentials) is entered
- Then: the login fails; no information about which credential was not matched (no leak)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: create a person with 2 TOTP credentials; test login with a code from each credential; enrollment test (no auto-revoke); default label test
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-022 (TOTP enrollment), REQ-010 (login flow with TOTP)
- **Downstream effects**: `local-credential.md` BR-02 must be updated (restriction lifted); the `label` attribute is added to the TOTP-type-specific attributes
- **Conflicts**: previously BR-02 in `local-credential.md` – is lifted by this REQ

## Risks of Non-Fulfillment

- Risk 1: Without multiple TOTP credentials, persons are forced to copy a secret to several devices (secret sharing) – worse than separate credentials; moderate severity
- Risk 2: Without a backup TOTP, users risk lockout on device loss – higher support load for operators

## Trade-offs

- Multiple TOTP credentials minimally increase the attack surface (each credential is a potential point of attack). Mitigated by: separate secret per credential (no secret sharing), `lastUsedAt` tracking, audit log on enrollment/use.
- Performance overhead at login: sequential verification of all TOTP credentials. Negligible at reasonable values (max. 5–10 credentials); most-recently-used-first ordering further mitigates the overhead.

## Implementation Notes

- DB query at login: `SELECT * FROM local_credentials WHERE person_id = ? AND type = 'totp' AND status = 'active' ORDER BY last_used_at DESC NULLS LAST`
- Verification loop stops at the first match
- Label input: free-text field in the enrollment UI ("What would you like to call this device?"); optionally with suggestions (iOS, Android, hardware token)
- No UI select "which credential at login" – the TOTP code itself is the distinction; multi-credential lookup is transparent on the server side

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from operator requirement (SH-06): "store multiple 2nd factors of the same type". Particularly useful for instances with several administrators: each admin can set up their own backup TOTP authenticator without affecting other credentials. Lifts BR-02 in `local-credential.md`.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft; lifts BR-02 in local-credential.md |
