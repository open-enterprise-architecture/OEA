# Spec Delta

## Purpose

Defines passkeys (WebAuthn) of persons with a local password: the registration in the running session, the passwordless login, the passkey as second step after the password, and the relying party the passkeys are bound to.

## ADDED Requirements

### Requirement: Passkey registration

A person with a local password SHALL register a passkey in the running session after an authentication within the last 5 minutes. The passkey SHALL be discoverable and verify the user; challenge, origin, relying party ID and signature SHALL be verified before it is stored. The confirmation SHALL mark the session second-factor-verified and be audited as `auth.factor-added`.

Trace: US-022 AC1, US-022 AC4, US-025 AC2, REQ-023, REQ-026 AC2, REQ-172, ADR-048

#### Scenario: Passkey registered

- **WHEN** a person starts a passkey registration with the label "Laptop" and confirms it with the response of an authenticator that verified the user
- **THEN** the passkey is active with the label "Laptop", the session is second-factor-verified, and an audit event `auth.factor-added` with type `passkey` exists

#### Scenario: Wrong origin

- **WHEN** the registration response was created for another origin or relying party ID
- **THEN** the request is rejected, no passkey is stored, and an audit event `auth.factor-enrollment-failed` with reason `verification-failed` exists

#### Scenario: Cancelled in the browser

- **WHEN** the person cancels the registration in the browser
- **THEN** no passkey is stored, and the pending registration expires after 10 minutes

### Requirement: Several passkeys

A person SHALL hold several active passkeys, each with a label; each SHALL be usable for the login on its own. Without a label a passkey SHALL get "Passkey" and the date of the registration.

Trace: US-022 AC3, US-025 AC2, REQ-023 AC3

#### Scenario: Second device

- **WHEN** a person with one passkey registers a second one and logs in with each of them
- **THEN** both logins succeed

### Requirement: Passwordless login with a passkey

A person in status `invited` or `active` SHALL log in with a passkey alone, without entering a login name or password, while password login of persons is possible. A passkey login SHALL create a second-factor-verified session, activate an invited person and keep the required password change of a temporary or expired password.

Trace: US-009 AC1, US-009 AC3, REQ-009, ADR-032 SoD-8, owner decision 2026-10-30

#### Scenario: Login with a passkey

- **WHEN** a person with a passkey and a locally assigned privileged role logs in with the passkey
- **THEN** a second-factor-verified session holding the privileged role is created, and an audit event `auth.login` with method `passkey` exists

### Requirement: Failed passkey logins

A failed passkey login – unknown credential, wrong signature, missing user verification, signature counter going backwards, person not allowed to log in – SHALL create no session, SHALL give the same answer as a wrong password, SHALL be audited as `auth.login-failed`, and SHALL count for the throttling per source address.

Trace: US-009 AC2, REQ-009 AC2, REQ-168 AC3, REQ-006

#### Scenario: Unknown passkey

- **WHEN** a login is attempted with a passkey that is not registered at the instance
- **THEN** the answer is the same as for a wrong password, and an audit event `auth.login-failed` exists

### Requirement: Passkey as second step after the password

After the password step of a person with an active second factor, the answer SHALL name the methods of the person (`totp`, `passkey`). A passkey of that person SHALL complete the login transaction like a valid code, with method `password-passkey`; five failed attempts SHALL invalidate the transaction.

Trace: owner decision 2026-10-30, REQ-010, REQ-031 AC8

#### Scenario: Password and passkey

- **WHEN** a person with a passkey and no TOTP credential logs in with the password and then confirms with the passkey
- **THEN** the password step names the method `passkey`, and the confirmation creates a second-factor-verified session

### Requirement: Relying party

Passkeys SHALL be bound to the relying party ID and origin of the public URL of the instance (`oea.web.public-url`), unless the operator sets them with `oea.auth.passkey.rp-id` and `oea.auth.passkey.origins`. Without a public URL or these settings, registrations and passkey logins SHALL be refused with 409.

Trace: US-022 AC4, REQ-023 AC4, ADR-048

#### Scenario: Instance without public URL

- **WHEN** a person starts a passkey registration on an instance without public URL and passkey settings
- **THEN** the request is rejected with 409, and no registration is started
