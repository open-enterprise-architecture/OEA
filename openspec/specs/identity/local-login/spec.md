# identity/local-login Specification

## Purpose

Defines the login of the local system admin account: password check and its protection against guessing, the setup phase without second factor, and the second factor that is required afterwards and how it is enrolled.

## Requirements

### Requirement: Password login of the system admin account

The local system admin account SHALL log in with its login name and password and SHALL then hold the permissions of `system-admin`; a wrong login name and a wrong password SHALL produce the same response. Successful and failed logins SHALL be audited; outdated hash parameters SHALL be upgraded at a successful login.

Trace: US-013 AC2, REQ-169, REQ-175

#### Scenario: Login after bootstrapping

- **WHEN** the operator logs in with the login name and password chosen during the bootstrapping
- **THEN** a session with the permissions of `system-admin` is created, the account changes from `bootstrapped` to `active`, and an audit event `auth.login` exists

#### Scenario: Unknown login name and wrong password

- **WHEN** a login is attempted once with an unknown login name and once with a wrong password
- **THEN** both are rejected with the same status, problem type and body, and each produces an audit event `auth.login-failed`

### Requirement: Throttling of local logins

Local logins SHALL be throttled per login name, including unknown names, with exponential backoff after 5 failed attempts within 15 minutes and a temporary lock of at most 15 minutes, and per source address after 100 failed attempts within 10 minutes; no lock SHALL be permanent.

Trace: REQ-168 AC1, REQ-168 AC3, REQ-168 AC4

#### Scenario: Repeated wrong passwords

- **WHEN** a sixth attempt follows five failed attempts for one login name within 15 minutes
- **THEN** it is rejected for at least the backoff period with the same response for existing and unknown names, and after the lock period the correct credentials succeed again

#### Scenario: Many names from one address

- **WHEN** 100 failed attempts from one source address across different login names occur within 10 minutes
- **THEN** further attempts from that address are throttled and an audit event `auth.throttled` exists

### Requirement: Setup phase without second factor

While the instance is in its setup phase, the system admin SHALL log in with login name and password only. After the setup phase has ended, every login of the system admin SHALL require a valid TOTP code in addition; five wrong codes SHALL invalidate the login transaction.

Trace: REQ-171 AC1, REQ-168 AC2, ADR-042

#### Scenario: Login during the setup phase

- **WHEN** the system admin logs in with login name and password while the setup phase lasts
- **THEN** the login succeeds and the session is not second-factor-verified

#### Scenario: Login after the setup phase

- **WHEN** the system admin logs in after the setup phase has ended, first without a code and then with a valid TOTP code
- **THEN** the first attempt asks for the second factor without creating a session, and the second creates a second-factor-verified session

#### Scenario: Wrong codes in one login transaction

- **WHEN** five wrong TOTP codes are entered in one login transaction
- **THEN** the transaction is invalidated and the password step must be repeated

### Requirement: TOTP enrollment

The system admin SHALL enroll a TOTP credential in the running session; enrollment SHALL require an authentication within the last 5 minutes, SHALL show the secret only once, and SHALL complete only after a valid code has been entered, which also marks the session as second-factor-verified. A code SHALL never be accepted twice for the same credential.

Trace: REQ-171, REQ-172 AC1, REQ-172 AC3, REQ-170 AC1

#### Scenario: Enrollment and confirmation

- **WHEN** the system admin starts the enrollment within 5 minutes after login and confirms it with a valid code
- **THEN** the TOTP credential is active, the session is second-factor-verified, and an audit event `auth.factor-added` exists

#### Scenario: Enrollment in an old session

- **WHEN** the system admin starts the enrollment in a session authenticated more than 5 minutes ago
- **THEN** it is rejected with problem type `reauthentication-required`

#### Scenario: Code replay

- **WHEN** a TOTP code that was just accepted is submitted again within its validity window
- **THEN** it is rejected
