# identity/password-reset Specification

## Purpose

Defines how an administrator resets the local password of a person who forgot it or after a security incident, and which resets are not allowed.

## Requirements

### Requirement: Password reset by an administrator

A principal with `person:manage` SHALL reset the local password of a person who has one. The active password SHALL be replaced by a generated temporary password with origin `admin-reset` and an expiry, returned once. All sessions SHALL end, the next login SHALL require the password change after the second factor of the person, and the reset SHALL be one transaction audited as `person.password-reset` without the password.

Trace: US-030, REQ-031 AC1 to AC5, REQ-031 AC7, REQ-031 AC8, REQ-173 AC2

#### Scenario: Password reset

- **WHEN** an administrator resets the password of a person with two active sessions
- **THEN** the answer contains the new password once, both sessions are rejected with 401, a login with the old password fails, a login with the new password requires the password change, and an audit event `person.password-reset` exists without the password

#### Scenario: Second factor stays required

- **WHEN** an administrator resets the password of a person with an active TOTP credential, and the person logs in with the new password
- **THEN** the TOTP step is required first, and only after a valid code the session requires the password change

### Requirement: Rules of a reset

A reset SHALL be rejected with 403 and reason `self-administration` for the caller's own person, with 409 if the person has no password or password login of persons is off, and with 403 audited as `authz.denied` if the target holds an admin-class permission through a local role assignment and the caller is not the system admin account.

Trace: REQ-031 AC6, REQ-031 AC9, REQ-173 AC3, ADR-032 SoD-1

#### Scenario: Reset of an administrator by another administrator

- **WHEN** a person holding `security-admin` resets the password of a person who holds `metamodel-admin` locally
- **THEN** the request is rejected with 403, no credential is changed, and an audit event `authz.denied` exists

#### Scenario: Reset of an administrator by the system admin account

- **WHEN** the system admin account resets the password of the same person
- **THEN** the reset succeeds
