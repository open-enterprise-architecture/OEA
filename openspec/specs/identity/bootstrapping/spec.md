# identity/bootstrapping Specification

## Purpose

Defines how a freshly installed OEA instance obtains its first administrator: a local system admin account created through a one-time setup token that only operators with access to the server can read.

## Requirements

### Requirement: Local bootstrapping on a fresh instance

When no system admin account exists, the system SHALL offer a local bootstrapping process that requires no external identity provider. A successful bootstrapping SHALL create the local system admin account in the state `bootstrapped` with a password credential and SHALL store the instance slug, which cannot be changed through bootstrapping afterwards.

Trace: US-013 AC1, REQ-013, ADR-001

#### Scenario: Fresh instance offers local bootstrapping

- **WHEN** a freshly installed instance without a system admin account is started and its setup status is requested
- **THEN** the system reports that setup is required and accepts a bootstrapping request without any identity provider being configured

#### Scenario: Bootstrapping creates the system admin account

- **WHEN** the operator submits the valid setup token, a login name, a password of at least 12 characters and an instance slug
- **THEN** the local system admin account exists with status `bootstrapped`, the instance slug is stored, the password is stored only as an argon2id hash, and the setup status reports that no setup is required

#### Scenario: Password outside the length limits

- **WHEN** the operator submits a password shorter than 12 or longer than 128 characters
- **THEN** the request is rejected with a validation problem, and no account is created

### Requirement: Setup token only through a privileged channel

The setup token SHALL be retrievable only through a channel that requires access to the server or container, and SHALL NOT appear in application logs or API responses. Bootstrapping SHALL be refused without a valid, unexpired setup token.

Trace: US-017 AC1, US-017 AC2, REQ-017, REQ-175 AC3

#### Scenario: Token readable only inside the container

- **WHEN** the instance starts without a system admin account
- **THEN** a one-time setup token is written to a file inside the container that only the OEA user can read, the application log states where to read it without containing the token, and no API operation returns it

#### Scenario: Setup without valid token

- **WHEN** a bootstrapping request carries no setup token, a wrong token or an expired token
- **THEN** no password is set, no account is created, and the request is rejected

### Requirement: No repeated or parallel bootstrapping

The system SHALL allow exactly one successful bootstrapping per instance and SHALL reject every further attempt as long as a system admin account exists.

Trace: US-015 AC1, US-015 AC2, REQ-015

#### Scenario: Two simultaneous attempts

- **WHEN** two bootstrapping requests with the valid setup token compete on the same fresh instance
- **THEN** exactly one completes successfully and the other is rejected

#### Scenario: Instance already bootstrapped

- **WHEN** a bootstrapping request is made on an instance with an existing system admin account
- **THEN** it is rejected without changing the existing account, and the setup token is no longer valid

### Requirement: Bootstrapping is audited

Every completed and every rejected bootstrapping attempt SHALL be recorded in the audit trail with timestamp, mode and result, without the setup token or password.

Trace: US-016 AC1, US-016 AC2, REQ-016, REQ-175

#### Scenario: Completed bootstrapping

- **WHEN** a local bootstrapping completes
- **THEN** the audit trail contains an event `system.bootstrapped` with timestamp, mode `local` and outcome `success`, attributed to the new system admin account

#### Scenario: Rejected bootstrapping

- **WHEN** a bootstrapping attempt is rejected
- **THEN** the audit trail contains an event `system.bootstrap-rejected` with timestamp, mode `local`, outcome `denied` and the reason, attributed to an unauthenticated actor
