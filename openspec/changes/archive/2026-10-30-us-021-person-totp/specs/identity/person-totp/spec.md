# Spec Delta

## Purpose

Defines the TOTP second factor of persons with a local password: the enrollment in the running session, several authenticators per person, the login with password and TOTP code, the enrollment offer after the password login, and the audit of enrollments.

## ADDED Requirements

### Requirement: TOTP enrollment by persons

A person with a local password SHALL enroll a TOTP credential in the running session as the system admin account does: an authentication within the last 5 minutes, the secret shown once and stored encrypted, and completion only after a valid code. The confirmation SHALL mark the session as second-factor-verified and SHALL be audited as `auth.factor-added`; neither secret nor code SHALL appear in logs or audit events.

Trace: US-021 AC1 to AC4, US-025 AC1, REQ-022, REQ-026 AC1, REQ-172, REQ-188

#### Scenario: Enrollment by a person

- **WHEN** a person logged in with a password starts the TOTP enrollment and confirms it with a valid code
- **THEN** the credential is active, the session is second-factor-verified, an audit event `auth.factor-added` exists, and no log line contains the secret

#### Scenario: Wrong code during the enrollment

- **WHEN** the person confirms the enrollment with a wrong code
- **THEN** the request is rejected, the enrollment stays pending and can be confirmed with a valid code, and an audit event `auth.factor-enrollment-failed` with reason `invalid-code` exists

### Requirement: Several authenticators

A person SHALL hold several active TOTP credentials, each with a label; without a label the credential SHALL get "Authenticator app" and the date of the enrollment. A new enrollment SHALL NOT revoke an existing credential, and a valid code of any active credential SHALL be accepted.

Trace: US-029 AC1 to AC4, REQ-030 AC1, REQ-030 AC2, REQ-030 AC4

#### Scenario: Second authenticator

- **WHEN** a person with one active TOTP credential enrolls a second one with the label "Backup phone" and later logs in with a code of the second one
- **THEN** both credentials are active, the login succeeds, and `lastUsedAt` of the second credential is set

### Requirement: TOTP login of persons

After the password step of a person with an active TOTP credential, no session SHALL be created; the answer SHALL ask for the second factor with a login transaction. A valid code SHALL create a second-factor-verified session; a wrong code SHALL not reveal that the password was correct, five wrong codes SHALL invalidate the transaction, and an accepted code SHALL never be accepted again.

Trace: US-010 AC1, US-010 AC2, REQ-010, REQ-030 AC5, REQ-170, REQ-168 AC2, ADR-032 SoD-8

#### Scenario: Login with password and code

- **WHEN** a person with an active TOTP credential and a locally assigned privileged role logs in with password and a valid code
- **THEN** a second-factor-verified session is created that holds the privileged role, and an audit event `auth.login` with method `password-totp` exists

#### Scenario: Wrong code

- **WHEN** the person enters a wrong code after a correct password
- **THEN** no session is created, and the answer is the same as for a wrong code after any password step

### Requirement: Enrollment offer after the password login

After a password login of a person without an active second factor, the web portal SHALL offer the TOTP enrollment; after a required password change, the offer SHALL follow the change. The person SHALL be able to skip the offer and enroll later in the profile.

Trace: US-021, US-025, owner decision 2026-10-30

#### Scenario: First login

- **WHEN** an invited person logs in with the initial password and changes it
- **THEN** the web portal shows the TOTP enrollment with a "Skip" button, and skipping it opens the start page

### Requirement: Audit of enrollments

Every confirmed TOTP enrollment SHALL be audited as `auth.factor-added` and every failed confirmation as `auth.factor-enrollment-failed` with the type `totp`, the owner and a generic reason (`invalid-code` or `enrollment-expired`). No audit event SHALL contain a secret, a code or a hash.

Trace: US-024 AC1, US-024 AC2, US-024 AC4, REQ-025 AC1 to AC3

#### Scenario: Expired enrollment

- **WHEN** a person confirms an enrollment more than 10 minutes after starting it
- **THEN** the request is rejected with 404, and an audit event `auth.factor-enrollment-failed` with reason `enrollment-expired` exists
