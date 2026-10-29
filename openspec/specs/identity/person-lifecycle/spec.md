# identity/person-lifecycle Specification

## Purpose

Defines how administrators change the status of a person – temporarily without access, back to active, or offboarded – and what each status change does to the person's sessions and local role assignments.

## Requirements

### Requirement: Status transitions of a person

A principal with `person:manage` SHALL be able to change the status of a human person along `active → inactive`, `inactive → active` and `invited`, `active` or `inactive` → `offboarded`, with an optional reason. Every other transition SHALL be rejected with 409 and problem type `invalid-status-transition`. The change SHALL be stored with the acting principal and time and audited as `person.status-changed`.

Trace: US-148 AC5, REQ-004, REQ-175, business object person

#### Scenario: Person set to inactive and back

- **WHEN** an administrator sets an active person to `inactive` and later back to `active`
- **THEN** both changes are stored with the administrator and time, and two audit events `person.status-changed` with the old and new status exist

#### Scenario: Transition not allowed

- **WHEN** an administrator sets an `offboarded` person to `invited` or `active`, or an `invited` person to `active`
- **THEN** the request is rejected with 409 and problem type `invalid-status-transition`, and the status is unchanged

#### Scenario: Missing permission

- **WHEN** a principal holding `role:assign` but not `person:manage` sets a person to `offboarded`
- **THEN** the response is 403 and the status is unchanged

### Requirement: Sessions end when a person leaves active

Setting a person to `inactive` or `offboarded` SHALL revoke all sessions of the person at once, each audited as `session.revoked` with reason `person-status-change`, so that every further request with them is rejected with 401.

Trace: US-148 AC1, REQ-164 AC2, REQ-004

#### Scenario: Sessions revoked on offboarding

- **WHEN** a person with an active session is set to `offboarded`
- **THEN** the next request with that session is rejected with 401, and an audit event `session.revoked` with reason `person-status-change` exists

### Requirement: Offboarding ends local role assignments

Setting a person to `offboarded` SHALL revoke every granted local role assignment of the person, with the acting principal as `revokedBy` and the reason "offboarding", each audited as `role.revoked`. Setting a person to `inactive` SHALL keep the assignments, which apply again once the person is `active`. The person's history and audit events SHALL stay attributed to the person.

Trace: US-148 AC2, US-148 AC3, US-148 AC4, UC-23 A2

#### Scenario: Assignments revoked on offboarding

- **WHEN** a person holding `enterprise-architect` locally and `viewer` through an IdP group mapping is set to `offboarded`
- **THEN** the local assignment shows `revokedAt` and the administrator as `revokedBy`, an audit event `role.revoked` exists for it, and a login through the IdP creates no session

#### Scenario: Assignments kept while inactive

- **WHEN** a person holding `enterprise-architect` locally is set to `inactive` and then back to `active`
- **THEN** the assignment was not revoked and the person holds `enterprise-architect` in a new session

#### Scenario: History retained

- **WHEN** an offboarded person is read and the audit trail is queried for the person
- **THEN** the person still exists with status `offboarded`, and the earlier audit events of the person are still attributed to it

### Requirement: Rules of a status change

A principal SHALL NOT change its own status (403 with reason `self-administration`, audited as `authz.denied`). A service account SHALL be rejected with 422.

Trace: ADR-032 SoD-1, business object person

#### Scenario: Own status

- **WHEN** a person holding `person:manage` sets herself to `inactive`
- **THEN** the request is rejected with 403 and reason `self-administration`, and the status is unchanged

#### Scenario: Service account

- **WHEN** an administrator changes the status of a service account with this operation
- **THEN** the request is rejected with 422
