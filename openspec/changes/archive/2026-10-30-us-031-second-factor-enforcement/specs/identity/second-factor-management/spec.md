# Spec Delta

## MODIFIED Requirements

### Requirement: Removing an own second factor

A principal SHALL remove one of its active second factors after an authentication within the last 5 minutes; the other sessions SHALL end, and the removal SHALL be audited as `auth.factor-removed`. The last second factor SHALL NOT be removed (409) by the system admin account after the setup phase, nor by a person who is not exempt while the enforcement is on.

Trace: owner decision 2026-10-30, REQ-172, ADR-042, REQ-020, BR-10 of local-credential

#### Scenario: Lost phone

- **WHEN** a person with two authenticators removes the one of the lost phone within 5 minutes after logging in
- **THEN** that credential is revoked, its codes are rejected at the next login, and an audit event `auth.factor-removed` exists

#### Scenario: Last factor of the system admin account

- **WHEN** the system admin account removes its only TOTP credential after the setup phase
- **THEN** the request is rejected with 409 and the credential stays active

#### Scenario: Last factor under enforcement

- **WHEN** the enforcement is on and a person who is not exempt removes the only second factor
- **THEN** the request is rejected with 409 and the second factor stays active
