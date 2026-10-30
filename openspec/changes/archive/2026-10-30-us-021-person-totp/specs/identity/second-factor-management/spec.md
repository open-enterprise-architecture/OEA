# Spec Delta

## Purpose

Defines how principals see and remove their own second factors, and how an administrator resets the second factors of a person who lost every device.

## ADDED Requirements

### Requirement: Overview of the own second factors

A principal with a local password SHALL list its active second factors with type, label, `createdAt` and `lastUsedAt`, never with a secret.

Trace: US-029 AC3, REQ-030 AC3, US-025 AC3, REQ-026 AC3

#### Scenario: Two authenticators

- **WHEN** a person with two active TOTP credentials lists the own second factors
- **THEN** the answer contains both with their labels and times, and no secret

### Requirement: Removing an own second factor

A principal SHALL remove one of its active second factors after an authentication within the last 5 minutes. The credential SHALL be revoked, the other sessions of the principal SHALL end, and the removal SHALL be audited as `auth.factor-removed`. The last TOTP credential of the system admin account SHALL NOT be removed after the setup phase (409).

Trace: owner decision 2026-10-30, REQ-172, ADR-042, BR-10 of local-credential

#### Scenario: Lost phone

- **WHEN** a person with two authenticators removes the one of the lost phone within 5 minutes after logging in
- **THEN** that credential is revoked, its codes are rejected at the next login, and an audit event `auth.factor-removed` exists

#### Scenario: Last factor of the system admin account

- **WHEN** the system admin account removes its only TOTP credential after the setup phase
- **THEN** the request is rejected with 409 and the credential stays active

### Requirement: Factor reset by an administrator

A principal with `person:manage` SHALL reset the second factors of a person: every active second factor SHALL be revoked with reason `factor_reset`, all sessions of the person SHALL end, and the reset SHALL be one transaction audited as `person.second-factors-reset`. The next password login SHALL offer a new enrollment.

Trace: REQ-173 AC5, REQ-164, owner decision 2026-10-30

#### Scenario: Every device lost

- **WHEN** an administrator resets the second factors of a person with one TOTP credential and an active session
- **THEN** the credential is revoked, the session is rejected with 401, the next login succeeds with the password alone and offers the enrollment, and an audit event `person.second-factors-reset` exists

### Requirement: Rules of a factor reset

A factor reset SHALL be rejected with 403 and reason `self-administration` for the caller's own person, with 409 for a person without an active second factor, with 422 for a service account, and with 403 audited as `authz.denied` if the target holds an admin-class permission through a local role assignment and the caller is not the system admin account.

Trace: REQ-173 AC3, ADR-032 SoD-1

#### Scenario: Reset of an administrator by another administrator

- **WHEN** a person holding `security-admin` resets the second factors of a person who holds `metamodel-admin` locally
- **THEN** the request is rejected with 403, the credentials stay active, and an audit event `authz.denied` exists
