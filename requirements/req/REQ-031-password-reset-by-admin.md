---
id: REQ-031
title: Password reset by administrator
type: functional
priority: must
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

# REQ-031: Password reset by administrator

## Statement

The system MUST allow an administrator to reset the local password of any person; in doing so, the previous password credential MUST be revoked immediately, a new password credential MUST be set (compliant with REQ-028) and displayed to the administrator once in plain text, and the required action "Change password at next login" MUST be set automatically for the affected person, blocking all application access until it has been performed.

## Rationale

Password reset is a core requirement of any local user management: persons forget passwords, and accounts must be locked and reinitialized after security incidents. The operator (SH-06) must be able to perform this process without external infrastructure (no self-service reset via email). The enforced required action "Change password" ensures that the admin-set password serves only as a one-time bridging code and the person immediately chooses their own password – analogous to the pattern for the initial password (REQ-024), but for existing accounts.

## Context

Concerns exclusively persons with an active local password credential (`LocalCredential.type=password`, `status=active`). Does not apply to the system admin account (UC-02), whose password is managed via a separate mechanism (REQ-013/REQ-014). OIDC persons have no local password credential; this process is not applicable to them. The reset is an admin action with an audit obligation.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Target person (person ID or username)
- New password: entered manually or via generator (REQ-027)

**Processing** (atomar / transaktional):
1. Authorization check: the executing user must have the admin role
2. The target person must exist and have an active `LocalCredential.type=password`
3. Validate the new password against REQ-028 policies
4. Hash the new password (argon2id preferred; bcrypt as fallback)
5. Set the person's previous active password credential to `status=revoked`, `revokedReason=admin_action`, `revokedAt=now`
6. Create a new `LocalCredential (type=password, status=active)` and link it to the person
7. Set the required action `password_change` for the person (in the Person object or a separate required actions table)
8. Write an audit log entry: admin person ID, target person ID, timestamp, action `password_reset`
9. Return the new plain-text password once

**Outputs**:
- Plain-text password: one-time display in the admin UI or CLI stdout, not retrievable afterwards
- Confirmation: "Password reset. The person must set a new password at next login."
- On error: error message; no partial state change (transaction rollback)

**Error Cases**:
- Person has no active password credential → error message ("Person has no local password; set the initial password via REQ-024")
- New password does not meet REQ-028 policies → error message; no credential change
- Executing user has no admin permission → 403 Forbidden; audit log entry for the unauthorized access attempt
- System admin account as target → 400 Bad Request; this mechanism does not apply to the system admin account

## Acceptance Criteria

**AC1**:
- Given: a person has an active password credential and can currently log in
- When: an administrator resets the password
- Then: the old password credential is `revoked`; a new one is `active`; the new plain-text password appears once

**AC2**:
- Given: the password has been reset
- When: the person logs in with the new password
- Then: the required action `password_change` is detected; the person gets no application access until the password has been changed

**AC3**:
- Given: the password has been reset
- When: the person tries to log in with the old password
- Then: the login fails (old credential is revoked)

**AC4**:
- Given: an administrator resets a password
- When: the audit log is evaluated
- Then: it contains admin person ID, target person ID, timestamp, action `password_reset`; no plain-text password in the log

**AC5**:
- Given: the process fails after step 4 (e.g., DB error)
- When: the error occurs
- Then: neither is the old credential revoked nor a new one created (complete rollback)

**AC6**:
- Given: the system admin account is to be reset
- When: the admin makes the request
- Then: it is rejected with an error; no credential changed

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: admin resets password; login with old password (AC3); login with new password + required action check (AC2); audit log check (AC4); rollback test (AC5)
- [x] Measurement tool: test suite of the admin module and auth module; DB inspection in the test
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**:
  - REQ-024 (setting a password, same security requirements apply)
  - REQ-027 (password generator, recommended for the new password)
  - REQ-028 (password policies; the new password must be compliant)
  - LocalCredential BO (BR-03: max. 1 active password credential; the reset process maintains this)
- **Downstream effects**: After the reset, the person must change their password at next login (required action `password_change`); this required action flow is the subject of a future UC (password change at login)
- **Conflicts**: REQ-013/REQ-014 (the system admin account has its own mechanism; this REQ explicitly does not apply to it)

## Risks of Non-Fulfillment

- Risk 1: Without a password reset, the administrator has to delete and recreate a person to restore access – high effort, data loss (role assignments etc.), severe
- Risk 2: Without an enforced required action, the admin-set password remains valid permanently – the operator knows the user's password, data protection and security risk, medium severity

## Trade-offs

- The required action is mandatory, not optional: the admin cannot switch off the enforced password change. Trade-off: less flexibility, but a clear security guarantee (the operator never permanently knows the user's password).
- Transactional atomicity: revoking the old credential + creating the new one + setting the required action must happen in one DB transaction. Prevents inconsistent states (AC5), requires careful implementation.

## Implementation Notes

- Same security requirements as REQ-024: plain text only in working memory; discard from memory immediately after returning; never in logs
- Admin UI: one-time modal/popup with the new password; "Copy password" button; then closed and not retrievable again
- CLI: `oea user reset-password --user <id>` → outputs the new password to stdout; with `--generate` for an automatically generated password
- The required action `password_change` uses the same mechanism as the 2FA required action check (UC-03): after the password check read the required actions list; for `password_change` → redirect to the password change page
- API endpoint: `POST /admin/persons/{id}/reset-password` (admin-only, RBAC-protected)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from operator requirement (SH-06): manual password reset for users. The enforced password change after the reset is an explicit security decision: the operator should issue passwords only as a temporary access code and never permanently know a user's password. This pattern is standard (Active Directory "must change password at next logon", Keycloak "update password" required action).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
