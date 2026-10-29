# Spec Delta

## Purpose

Defines the local password login of persons on instances where the operator allows it: the initial password set by an administrator, the login, the required change of a temporary password, and failed logins that reveal nothing about accounts.

## ADDED Requirements

### Requirement: Password login of persons is switched on explicitly

Password login of persons SHALL be off on a newly installed instance and SHALL be possible only after the operator switches it on. While it is off, a password login of a person SHALL fail like a wrong password.

Trace: US-011 AC2, REQ-011

#### Scenario: Fresh instance

- **WHEN** a person with a stored password logs in on an instance where the operator has not switched on password login of persons
- **THEN** the login fails with the generic invalid-credentials answer

### Requirement: Initial password set by an administrator

A principal with `person:manage` SHALL be able to set an initial password for a human person in status `invited` or `active` who has no password. The password SHALL be stored only as an argon2id hash with the origin `initial` and an expiry (default 72 hours), SHALL never be returned or logged, and the change SHALL be audited as `person.password-set`.

Trace: US-023 AC1, US-023 AC2, US-023 AC4, REQ-024, REQ-173 AC1, REQ-169

#### Scenario: Initial password set

- **WHEN** an administrator sets an initial password for an invited person
- **THEN** the response carries the expiry but not the password, the database holds only an argon2id hash with origin `initial`, an audit event `person.password-set` exists, and no log line contains the password

#### Scenario: Password already set

- **WHEN** an administrator sets an initial password for a person who already has a password
- **THEN** the request is rejected with 409 and problem type `password-already-set`

### Requirement: Rules for setting an initial password

Setting an initial password SHALL be rejected with 409 if password login of persons is off or the person already has a password, with 403 and reason `self-administration` for the caller's own person, and with 422 for a service account or a password outside 12 to 128 characters.

Trace: US-023, REQ-024, ADR-032 SoD-1, REQ-169

#### Scenario: Own person and service account

- **WHEN** an administrator sets an initial password for her own person, and for a service account
- **THEN** the first request is rejected with 403 and reason `self-administration`, the second with 422

### Requirement: Password login of persons

When password login of persons is on, a person in status `invited` or `active` SHALL log in with the e-mail address of the person record and a valid, unexpired password. The first login SHALL set an invited person to `active`. The session SHALL NOT be second-factor-verified, so privileged roles stay ineffective.

Trace: US-011 AC1, US-011 AC3, US-023 AC3, REQ-011, REQ-168, REQ-007

#### Scenario: Login with a self-chosen password

- **WHEN** an active person holding `viewer` logs in with her e-mail address and her password
- **THEN** a session is created that holds `viewer`, and an audit event `auth.login` with method `password` and no second factor exists

#### Scenario: Throttled like the system admin account

- **WHEN** a person's login fails repeatedly with a wrong password
- **THEN** further attempts are throttled with the same answer as for the system admin account, and each failure is audited as `auth.login-failed`

#### Scenario: Expired initial password

- **WHEN** a person logs in with an initial password whose expiry has passed
- **THEN** the login fails with the generic invalid-credentials answer

### Requirement: A temporary password must be changed first

A login with a temporary password SHALL create a session that allows only reading the current principal, logging out and changing the password. Every other request SHALL be answered with 403 and problem type `password-change-required` until the password is changed.

Trace: REQ-173 AC2, US-023 AC3, REQ-024

#### Scenario: First login with the initial password

- **WHEN** an invited person logs in with her initial password and calls an operation that needs a permission
- **THEN** the login result names the required action `change-password`, the person is `active`, and the call is rejected with 403 and problem type `password-change-required`

### Requirement: Changing the own password

A person with a local password SHALL be able to change it with the current password and a new one of 12 to 128 characters that differs from it. The new password SHALL be stored with origin `self-set` and no expiry, SHALL lift the restriction of the session, and SHALL be audited as `auth.password-changed`.

Trace: REQ-173 AC2, REQ-024, REQ-169

#### Scenario: Password changed

- **WHEN** the person changes the password with the initial password as current password and a new password
- **THEN** further requests of the session are processed, a new login with the initial password fails, a login with the new password succeeds, and an audit event `auth.password-changed` exists

### Requirement: Failed logins reveal nothing

Every failed password login – unknown name, wrong password, expired temporary password, person not active, password login of persons off – SHALL give the same answer and SHALL run one password hash verification, so that the response times do not reveal whether an account exists.

Trace: US-006 AC1, US-006 AC2, REQ-006

#### Scenario: Unknown and existing accounts

- **WHEN** one login fails with an unknown e-mail address and one with the wrong password of an existing person
- **THEN** both answers are identical, and each ran exactly one password hash verification
