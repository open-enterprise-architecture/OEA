# US-030: Reset a User's Password as Administrator

**ID**: US-030
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an administrator, I want to reset a person's local password and hand over a new one-time password to them, so that they can log in again after a forgotten password or a security incident, without me having permanent knowledge of their password.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-031: Password Reset by Administrator](../req/REQ-031-password-reset-by-admin.md)

## Acceptance Criteria

**AC1**:

- Given: Max opens the user management and selects a person
- When: he chooses "Reset password" and enters (or generates) a new password
- Then: the old password becomes invalid; the new password appears once; Max can pass it on to the person

**AC2**:

- Given: the password has been reset
- When: the person logs in with the new password
- Then: they are immediately prompted to choose their own password; they have no app access until this is done

**AC3**:

- Given: the password has been reset
- When: the person tries to log in with the old password
- Then: the login fails

**AC4**:

- Given: Max performs a password reset
- Then: an audit log entry appears with Max's ID, the person ID and the timestamp; no password plaintext in the log

## Technical Notes

- Affected components: admin user management UI, auth module (required action mechanism), audit log
- API endpoints: `POST /admin/persons/{id}/reset-password` (admin-only, RBAC-protected)
- Database changes: no schema change; existing `local_credentials` and required actions mechanism are used
- Transactionality: revoke old credential + create new + set required action must be atomic (AC5 in REQ-031)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (reset flow, login with old password, required action check, audit log, rollback on error)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-023 (set initial password – same UI elements and security patterns); US-027 (password policies must be active for validating the new password)
- Recommended beforehand: US-026 (password generator for the new one-time password)
- Blocks: future US for "change password on required action" (login flow after reset)

## Notes

The forced password change after the reset is not configurable – it is always active. Max only sets a one-time password, not a permanent one. The pattern corresponds to Active Directory "User must change password at next logon" and the Keycloak "update password" required action. The required action `password_change` uses the same mechanism as the 2FA required action in UC-03.
