# Design

## Context

See proposal.md – Why. Relevant current state:

- **`persons`:**
  - `status` is `invited | active | inactive | offboarded`.
  - `status_changed_at`, `status_changed_by_person_id` and `status_changed_by_admin_id` exist.
  - `Persons` creates persons (`invited`) and binds identities. The first OIDC login sets `invited → active` and writes `person.activated`.
- **Sessions and login:**
  - `Sessions.authenticate` accepts sessions of persons in status `invited` or `active` only, so a status change already blocks requests without waiting for a revocation.
  - `sessions.revoked_reason` allows `person-status-change`.
  - The OIDC login refuses persons that are neither `invited` nor `active` (REQ-004).
- **Assignments and SoD-7:**
  - `RoleAssignments.revoke` revokes one assignment and the person's sessions.
  - `AdministrationPaths.requireRemainingPath(Removal)` checks SoD-7 under an advisory lock. `Removal` names the local account, a mapping or an assignment.
- **Contract:** no operation changes a person's status. `Person.status` is in the `Person` schema.

## Goals / Non-Goals

**Goals:**

- One operation for the status transitions of human persons with immediate effect.
- Offboarding as one step: status, sessions and local assignments in one transaction.

**Non-Goals:**

- Reactivation of offboarded persons, service accounts, anonymization, HR synchronization.

## Architecture Constraints

- **ADR-028:** `PersonLifecycle` lives in `io.oea.app.access` and its controller operation in `io.oea.api.access`.
- **ADR-032:**
  - SoD-1 is answered with 403 and reason `self-administration` and audited through `PermissionDenials`.
  - SoD-7 is answered with 409 `last-admin-path` (API-9).
  - Assignments are never deleted (DM-4).
- **ADR-013:** the operation is added to `api/openapi.yaml` first, with `x-oea-authorization` `person:manage` and `429`.
- **ADR-033:** `person.status-changed` is added to the audit event catalog. `session.revoked` and `role.revoked` are reused.

## Decisions

### D1: A dedicated status operation

`POST /admin/persons/{personId}/status` takes `PersonStatusChange` (`status`: `active | inactive | offboarded`, `reason`: optional, max 500) and returns `Person`.

- **Alternative – `PATCH /admin/persons/{personId}`:** this mixes a security-relevant transition with editable master data and its validation. A transition operation is easier to authorize, audit and check, as with `revoke` of assignments.

### D2: Transition table

The allowed transitions are:

| From \ To | `active` | `inactive` | `offboarded` |
|---|---|---|---|
| `invited` | – (first login only) | – | ✓ |
| `active` | – (no change) | ✓ | ✓ |
| `inactive` | ✓ | – (no change) | ✓ |
| `offboarded` | – | – | – (no change) |

- Every cell marked "–" is answered with 409 `invalid-status-transition` (exception `InvalidStatusTransitionException`). This includes the unchanged status, so that a repeated request does not write a second event.
- `offboarded → invited` is not offered while reactivation is off.
- `invited → inactive` has no use: an invited person has no access yet.

### D3: One transaction for the effects

`PersonLifecycle.change` performs these steps in one transaction:

1. **Checks:** person exists and is human (422 for a service account), SoD-1, transition allowed.
2. **SoD-7:** if the new status is not `active` and the person holds a usable `system-admin` path, `AdministrationPaths.requireRemainingPath(Removal.person(personId))`. `Removal` gets a `personId` that excludes all assignments of that person from the count.
3. **Status:** update `status`, `status_changed_at`, `status_changed_by_*`.
4. **Sessions:** revoke all open sessions of the person with reason `person-status-change` and write one `session.revoked` each, as `Sessions` does for ended sessions.
5. **Offboarding:** for `offboarded`, revoke every granted assignment through a new `RoleAssignments.revokeAllForOffboarding(principal, personId)`. It applies the same update as `revoke`, with reason "offboarding" and one `role.revoked` each; the SoD-7 check of step 2 already covered the person, and the sessions are revoked once in step 4.
6. **Audit and cache:** write `person.status-changed` with `from`, `to`, `reason`, `sessionsRevoked` and `assignmentsRevoked`, and call `evictAll()`.

### D4: Status check of persons

`Sessions.authenticate` already rejects requests of persons who are not `invited` or `active`, and `PermissionResolver` counts local assignments only for `active` persons. The revocation of step 4 makes the end visible in the session records and audit trail; correctness does not depend on it.

## Data Model

No change to `docs/data-model.puml`; the columns exist. `business-objects/person.md` gets a note:

- the transitions are realized through `changePersonStatus`;
- `offboarded → invited` is rejected while reactivation is off;
- service accounts change status with US-142.

## API

Delta to `api/openapi.yaml`:

- **New `POST /admin/persons/{personId}/status`** (`changePersonStatus`, tag Access Control):
  - `x-oea-authorization` `person:manage`;
  - request `PersonStatusChange`;
  - `200` `Person`;
  - `400`, `401`, `403` (also `self-administration`), `404`;
  - `409` with problem types `invalid-status-transition` and `last-admin-path`;
  - `413`, `422` (service account), `429`.
- **New schema `PersonStatusChange`.**

## Risks / Trade-offs

- **Offboarding a person with many assignments writes many audit events in one transaction.** → The number of local assignments per person is small (built-in roles only); each event is one insert.
- **Status changes of service accounts stay impossible until US-142.** → No service account can be created through the API yet either.
