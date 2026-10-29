# Proposal

## Why

An administrator cannot change the status of a person: there is no operation to take a person's access away temporarily (`inactive`) or for good (`offboarded`). Former employees therefore keep their local role assignments, and their sessions run until they time out. REQ-164 AC2 requires that a person set to `inactive` or `offboarded` loses every session within 60 seconds. UC-23 A2 requires that offboarding ends all local role assignments in one step, while history and audit stay attributed. This second and last change of S4 adds the person lifecycle on top of the local assignments of `us-144-local-role-assignments`.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-23: Manage roles and role assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (A2 offboarding, E3) |
| User Stories | [US-148: Revoke access on offboarding](../../../requirements/user-stories/US-148-revoke-access-on-offboarding.md); [US-146: Protect the last system admin path](../../../requirements/user-stories/US-146-protect-last-system-admin-path.md) AC5 |
| Requirements | [REQ-164: Session termination and revocation](../../../requirements/req/REQ-164-session-termination-revocation.md) AC2; [REQ-004: Reject unknown or offboarded person](../../../requirements/req/REQ-004-reject-unknown-offboarded-person.md); [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (DM-4, SoD-1, SoD-7, API-9) |
| Sprint / Milestone | S4 / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-148 AC1:** the audit event of the revoked sessions is the existing `session.revoked` (reason `person-status-change`), one per session, not `auth.session-revoked`. There are no refresh tokens or event streams of OEA yet; the sessions are the only credentials to revoke.
- **US-148 AC5:** the missing permission is `person:manage`. The status operation needs it alone, as the business object `person` defines.
- **Business object `person`, reactivation of `offboarded` persons (BR-07, setting `allowReactivationOfOffboarded`):** the setting cannot be changed yet and is off, so `offboarded → invited` is always rejected with 409.
- **Service accounts (business object `person`):** service accounts change status with `service-account:manage` (US-142). This operation is for human persons only and rejects service accounts with 422.

## What Changes

- **New contract operation** `POST /admin/persons/{personId}/status` (`changePersonStatus`, permission `person:manage`): body `{status, reason}`, response the person.
  - Allowed transitions: `active ⇄ inactive`; `invited`, `active` or `inactive` → `offboarded`.
  - Other transitions are rejected with 409 (`invalid-status-transition`). This includes `offboarded → invited` while reactivation is off and `invited → active`, which only the first login performs.
- **Effects:**
  - Leaving `active` or `invited` revokes all sessions of the person at once (`session.revoked`, reason `person-status-change`). Login stays refused for `inactive` and `offboarded` persons (REQ-004, implemented).
  - `offboarded` additionally revokes every granted local assignment, with the acting administrator as `revokedBy`, reason "offboarding" and one `role.revoked` each.
  - `inactive` keeps the assignments: they are effective again after the return to `active`.
  - The change is audited as `person.status-changed`, and the person's history and audit events stay attributed to it.
- **Rules:**
  - A principal cannot change its own status (SoD-1, 403 `self-administration`).
  - Service accounts are rejected with 422.
  - A status change is rejected with 409 `last-admin-path` if the person holds the last usable path to `system-admin` (SoD-7, US-146 AC5), using the check of `us-144-local-role-assignments`.
- **Manual:** persons page (statuses, offboarding), English and German.

## Out of Scope

- **Reactivation of offboarded persons** (`allowReactivationOfOffboarded`, BR-07) and anonymization of offboarded accounts.
- **Status changes of service accounts** (US-142) and **HR master system synchronization**.
- **Further fields of a person** (name, e-mail, end date) and an **admin UI**.

## Capabilities

### New Capabilities

- `identity/person-lifecycle`: status transitions of persons, their effect on sessions and local role assignments, the rules (SoD-1, SoD-7, service accounts) and the audit.

### Modified Capabilities

- `identity/access-administration`: "The last path to system administration is kept" also covers status changes of persons.

## Impact

- **`backend/`:**
  - New `io.oea.app.access.PersonLifecycle`.
  - `AdministrationPaths.Removal` gets a person.
  - `RoleAssignments` revokes all of a person's assignments for offboarding.
  - Controller operation in `io.oea.api.access`.
- **`api/openapi.yaml`:** `changePersonStatus` with schema `PersonStatusChange`; problem types `invalid-status-transition` and `last-admin-path`; `429`.
- **No Flyway migration:** `persons.status`, `status_changed_*` and `sessions.revoked_reason` `person-status-change` exist.
- **Data model:** `docs/data-model.puml` unchanged; `business-objects/person.md` gets a note on the realigned transitions.
- **Docs:**
  - `docs/audit-event-catalog.md` (`person.status-changed`).
  - `manual/administration/identity-providers.md` and its German page.
- **`frontend/`:** no change.
