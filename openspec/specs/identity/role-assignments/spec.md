# identity/role-assignments Specification

## Purpose

Defines how administrators assign authorization roles to individual persons locally: validity, effect on permissions and sessions, the separation-of-duties rules of an assignment, expiry, revocation and the preview of a role.

## Requirements

### Requirement: Local assignment of a role

A principal with `role:assign` SHALL be able to assign a role to a person with an optional `validFrom`, `validTo` and reason. The assignment SHALL be stored with source `local`, the granting principal and time, and SHALL be audited as `role.assigned`.

Trace: US-144 AC1, REQ-157, REQ-175, ADR-032 DM-4

#### Scenario: Assignment stored and audited

- **WHEN** an administrator assigns `enterprise-architect` to an active person with `validTo` at the end of her contract and the reason "TICKET-123"
- **THEN** the assignment is stored with source `local`, the administrator as `grantedBy`, `grantedAt`, `validFrom`, `validTo` and the reason, and an audit event `role.assigned` with actor, target person and role exists

#### Scenario: Invalid validity

- **WHEN** an administrator assigns a role with a `validTo` that is not after `validFrom`, or an unknown role
- **THEN** the request is rejected with 422 and nothing is stored

#### Scenario: Missing permission

- **WHEN** a principal holding only `metamodel-admin` calls the assignment or the revocation operation
- **THEN** the response is 403 and an audit event `authz.denied` exists

### Requirement: Effective local roles

A local assignment SHALL grant its role while it is granted, `validFrom` ≤ now < `validTo`, and the person is `active`. A privileged role SHALL be effective only in a second-factor-verified session. Changes SHALL apply to existing sessions within 60 seconds. An assignment to an `invited` person SHALL be stored and SHALL become effective once the person is `active`.

Trace: US-144 AC1, US-144 AC5, REQ-003 AC2, REQ-164 AC3, ADR-032 SoD-8

#### Scenario: Assignment effective within 60 seconds

- **WHEN** a person without any role receives `enterprise-architect` and calls an operation requiring `entity:write` 60 seconds later
- **THEN** the call is authorized and the current principal lists `enterprise-architect` with source `local`

#### Scenario: Pre-provisioning

- **WHEN** an administrator assigns `viewer` to a person in status `invited` and the person logs in for the first time
- **THEN** the assignment was stored before the login and the person holds `viewer` after it

### Requirement: Expiry by validTo

An assignment whose `validTo` has passed SHALL no longer grant its role within 60 seconds, without a status change, and SHALL be audited once as `role.assignment-expired`.

Trace: US-144 AC3, ADR-032 DM-4

#### Scenario: Expired assignment

- **WHEN** a person holds `enterprise-architect` with `validTo` = T and calls an operation requiring `entity:write` after T + 60 seconds
- **THEN** the response is 403 and exactly one audit event `role.assignment-expired` for the assignment exists

### Requirement: Revocation of an assignment

A principal with `role:assign` SHALL be able to revoke a granted assignment. The assignment SHALL keep its history with `revokedAt` and `revokedBy` and SHALL NOT be deleted. All sessions of the person SHALL be revoked, and the revocation SHALL be audited as `role.revoked`. Revoking an assignment that is already revoked or expired SHALL be rejected with 409.

Trace: US-144 AC4, REQ-164 AC3, REQ-175

#### Scenario: Role revoked

- **WHEN** a person holds `enterprise-architect` and `viewer` and has an active session, and an administrator revokes `enterprise-architect`
- **THEN** the assignment shows `revokedAt` and `revokedBy`, the session is rejected with 401, the person's next login holds only `viewer`, and an audit event `role.revoked` exists

### Requirement: No self-administration of assignments

A principal SHALL NOT assign or revoke roles of itself; such a request SHALL be rejected with 403 and reason `self-administration` and audited as `authz.denied`.

Trace: ADR-032 SoD-1, US-144

#### Scenario: Own assignment

- **WHEN** a person holding `role:assign` assigns a role to herself
- **THEN** the request is rejected with 403 and reason `self-administration`, and an audit event `authz.denied` exists

### Requirement: Roles of service accounts

A service account SHALL receive only roles marked `assignableToServiceAccounts`; other assignments SHALL be rejected with 422.

Trace: ADR-032 SoD-4

#### Scenario: Privileged role for a service account

- **WHEN** an administrator assigns `system-admin` to a service account
- **THEN** the request is rejected with 422 and nothing is stored

### Requirement: Combined metamodel and visibility administration is flagged

An assignment that gives a person `metamodel:write` together with `visibility:configure` or `visibility-scope:assign` SHALL be rejected with 409 and problem type `sod-conflict` unless the request acknowledges the conflict; an acknowledged assignment SHALL be stored and flagged with `sodConflict`.

Trace: ADR-032 SoD-3

#### Scenario: Conflict acknowledged

- **WHEN** a person holding `metamodel-admin` is assigned `security-admin`, first without and then with `acknowledgeSodConflict`
- **THEN** the first request is rejected with 409 and problem type `sod-conflict`, and the second stores the assignment with `sodConflict` true

### Requirement: Role preview

A principal with `role:assign` SHALL be able to read a role with its permissions and whether it is privileged, before assigning it.

Trace: US-144 AC2, REQ-171

#### Scenario: Preview of a privileged role

- **WHEN** an administrator reads the role `metamodel-admin`
- **THEN** the response lists `metamodel:write` and marks the role as privileged
