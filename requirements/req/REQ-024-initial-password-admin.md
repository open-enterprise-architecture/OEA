---
id: REQ-024
title: Set initial password by administrator
type: functional
priority: must
status: proposed
version: 0.4.0
created: 2026-06-25
last_updated: 2026-10-29
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-024: Set initial password by administrator

## Statement

The system MUST allow an administrator to set an initial password when creating a person for local authentication; this password MUST be persisted as an argon2id hash (parameters per REQ-169) immediately after being set, MUST be displayed to the administrator in plain text exactly once and not be retrievable afterwards, MUST expire after a configurable period (default 72 hours, REQ-173) and MUST only allow reaching the mandatory password change.

## Rationale

Local authentication without an external IdP needs a starting point: before a person can log in, they must have credentials. The initial password bridges this gap – it is the starting point for the first login (UC-01 A5), after which UC-03 (required action) sets up the 2nd factor. The administrator sets the password once, passes it on to the person (any channel), and the person changes it at first login (mandatory required action `password_change`, see Context). Addresses SH-06's requirement for local user management without email infrastructure.

## Context

The initial password is a temporary credential: it suffices to perform UC-01 A5 (password login) and to trigger UC-03 (required action). No enrollment token or invitation link is needed.

Security review SEC-12 (2026-10-13): an initial password without expiry and without mandatory change can be used by whoever obtains it first, who then completes just-in-time enrollment with their own second factor. Since version 0.3.0 the password change at first login is mandatory (required action `password_change`, same mechanism as REQ-031) and the initial password expires (REQ-173). Hashing follows REQ-169 (argon2id only; bcrypt is no longer permitted because it truncates at 72 bytes).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Generated or manually entered initial password by the administrator (admin UI or CLI)

**Processing**:

- System checks the password against configurable minimum requirements (minimum length; optional complexity rules)
- System creates an argon2id hash (REQ-169)
- Hash is persisted as a `LocalCredential` with `type=password` and linked to the person, marked as temporary with an expiry timestamp (default now + 72 h)
- System sets the required action `password_change` for the person
- Plain-text password is displayed once in the admin UI / CLI output, afterwards not retrievable
- Optional: the system can automatically generate a cryptographically strong password (recommended)

**Outputs**:

- Plain-text password: one-time display in the admin UI or CLI stdout
- Confirmation: "Initial password set. Please pass it on to the person securely."
- On error: error message with the unmet requirement

**Error Cases**:

- Password does not meet minimum requirements → error message with the specific requirements, no hash stored
- Person does not exist or already has an active password credential → error message; existing credentials unchanged

## Acceptance Criteria

**AC1**:

- Given: an administrator creates a person and sets an initial password
- When: the request is processed successfully
- Then: the password hash is persisted as `LocalCredential (type=password)`; the plain text is displayed exactly once

**AC2**:

- Given: the initial password has been set and displayed
- When: the administrator reopens the admin UI or calls the API again
- Then: the plain-text password is no longer retrievable (only the hash in the DB)

**AC3**:

- Given: the initial password is set
- When: the person logs in with this password (UC-01 A5)
- Then: the login succeeds and UC-03 (required action) is triggered if 2FA is enforced

**AC4**:

- Given: the system processes the password request
- When: server logs are written
- Then: the plain-text password is not contained in the logs

**AC5**:

- Given: the system automatically generates a password
- When: it is generated
- Then: it has at least 16 characters and sufficient entropy (upper/lower case/digits/special characters)

**AC6**:

- Given: an initial password set 73 hours ago and never used (default expiry 72 h)
- When: the person tries to log in with it
- Then: the login fails with the generic error of REQ-006 and the administrator must issue a new initial password

**AC7**:

- Given: a person logs in successfully with the initial password
- When: the login completes
- Then: no application access is possible until the person has set a new password (required action `password_change`)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: set password, check hash format, attempt plain-text retrieval, log scan
- [x] Measurement tool: test suite of the admin module; DB inspection in the test
- [x] Pass criterion: AC1–AC7 green; login with the set password (AC3) works via REQ-011
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: The person must be created in the system (no separate UC so far); REQ-028 (password policies must be configured so that validation takes effect)
- **Downstream effects**: REQ-011 (password login); UC-03 required action (requires an initial password)
- **Recommended**: REQ-027 (password generator automatically creates a policy-compliant password)
- **Related**: REQ-169 (password storage), REQ-173 (temporary password lifecycle)
- **Follow-up requirement**: REQ-031 (password reset for existing users – same pattern, but with revocation of the old credential and forced password change)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without an initial password, no first login for local users – complete blocking of onboarding, severe
- Risk 2: Plain text in logs → credentials leak, severe (mitigated by AC4)

## Trade-offs

- No email sending by OEA: the administrator is responsible for secure handover of the initial password (in person, messenger, own mail system). OEA remains infrastructure-independent.

## Implementation Notes

- Realized by OpenSpec change `us-011-local-password`: the initial password is set by `POST /admin/persons/{personId}/password` on an existing person (not inside `createPerson`), stored with origin `initial` and an expiry (`oea.auth.person-password.temporary-password-validity`, default 72 hours); the answer carries only the expiry.
- Prefer automatically generated passwords (admin does not have to invent one); cryptographically secure (CSPRNG)
- Hash immediately when setting: plain text only in working memory, discard after hashing; never in request logs
- Admin UI: display the password in a one-time popup or a secured page (not in the regular page content, which could be cached)
- CLI alternative: `oea user create --name "Kurt" --generate-password` → outputs the password to stdout; admin copies it

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: OpenSpec change `us-011-local-password`
- Tests: –
- Verification status: not-verified

## Notes

v0.2.0: Completely rewritten. v0.1.0 (password enrollment by the user themselves via enrollment token) was rejected because the token approach (REQ-021 `rejected`) was replaced by just-in-time enrollment in the login flow. The initial password is now set by the administrator and displayed once – like the setup token in UC-02, but for regular persons.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft: password enrollment by user via enrollment token |
| 0.2.0 | 2026-06-25 | requirements-engineer | Complete revision: admin sets initial password; token approach rejected |
| 0.3.0 | 2026-10-13 | requirements-engineer | Security review SEC-12/SEC-09: expiry (default 72 h), mandatory password change (AC6, AC7); argon2id only per REQ-169 |
| 0.4.0 | 2026-10-29 | requirements-engineer | Implementation note (change `us-011-local-password`) |
