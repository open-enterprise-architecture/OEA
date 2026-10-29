# Proposal

## Why

Today a person receives roles only from IdP group mappings. An administrator cannot grant a single person a role, limit it in time, or take it away again (UC-23 basic flow, A3). The offboarding of the next change (US-148) has to end local assignments, and REQ-164 AC3 needs revoked roles to stop applying within 60 seconds. The SoD-7 rule "administration cannot be lost" is checked only when the local system admin account is deactivated. Revoking an assignment or deleting an IdP group mapping can still remove the last path to `system-admin` (gap noted in `us-019-admin-safeguards`). This first change of S4 adds local role assignments and guards every revocation that SoD-7 covers.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-23: Manage roles and role assignments](../../../requirements/use-cases/UC-23-manage-roles-and-role-assignments.md) (basic flow, A3, E3) |
| User Stories | [US-144: Assign and revoke a local role](../../../requirements/user-stories/US-144-assign-and-revoke-local-role.md); [US-146: Protect the last system admin path](../../../requirements/user-stories/US-146-protect-last-system-admin-path.md) |
| Requirements | [REQ-157: Permission model](../../../requirements/req/REQ-157-permission-model.md); [REQ-003: Access without role assignment](../../../requirements/req/REQ-003-access-without-role-assignment.md); [REQ-164: Session termination and revocation](../../../requirements/req/REQ-164-session-termination-revocation.md) AC3; [REQ-175: Security event logging](../../../requirements/req/REQ-175-security-event-logging.md) |
| ADRs | [ADR-032: Authorization model](../../../adrs/ADR-032-authorization-model.md) (§7, DM-4, SoD-1, SoD-3, SoD-4, SoD-7, SoD-8, API-4, API-9) |
| Sprint / Milestone | S4 / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md), re-scoped 2026-10-29) |

**Realigned sections** (non-binding details made concrete by this change):

- **US-144 AC2 (preview):** there is no admin UI. The preview is the planned operation `getAuthorizationRole`, which returns the permissions of a role and whether it is privileged. A client calls it before the assignment.
- **US-146 AC2 (setting `validTo` on the last holder):** the contract has no operation that changes an existing assignment. `validTo` is set only at creation. An assignment with a `validTo` never counts as a usable path to `system-admin`, so its expiry cannot remove the last path.
- **US-146 AC3:** the local system admin account now also counts a person's open-ended assignment of `system-admin` as another path. This extends `us-019-admin-safeguards` design D3, which counted only IdP group mappings.
- **US-146 AC5 (person status change):** this follows with the person lifecycle of US-148 (next S4 change), which uses the same check.
- **Four-eyes approval (US-150, `privilegedAssignmentApproval`):** this is not part of S4. The setting cannot be enabled yet; assignments are created directly. The `202` response of the contract stays unused.

## What Changes

- **Local role assignments (US-144):**
  - `GET` and `POST /admin/persons/{personId}/role-assignments` and `POST …/{assignmentId}/revoke` (`role:assign`).
  - An assignment has `validFrom`, an optional `validTo` and a reason.
  - It is effective while it is granted and within its validity, and the person is `active`; privileged roles also need a second factor (SoD-8).
  - The role applies to the person's sessions within 60 seconds.
  - Revocation keeps the history (`revokedAt`, `revokedBy`) and revokes all sessions of the person.
  - The audit events are `role.assigned`, `role.revoked` and `role.assignment-expired`. An expired assignment is recorded once by a scheduled job.
- **Rules on assignment:**
  - A principal cannot assign or revoke its own roles (SoD-1, `403`). The case of a person linked to the local system admin account follows once that link exists in the schema.
  - Service accounts get only roles marked `assignableToServiceAccounts` (SoD-4, `422`).
  - Combining `metamodel:write` with visibility permissions needs `acknowledgeSodConflict` (SoD-3, otherwise `409`), and the assignment is flagged (`sodConflict`).
  - An unknown role or `validTo` ≤ `validFrom` gives `422`.
- **Role preview (US-144 AC2):** the planned operation `GET /admin/roles/{roleId}` (`getAuthorizationRole`) is implemented.
- **Last path to `system-admin` (US-146):**
  - One check guards the revocation of an assignment, the deactivation of the local system admin account, and the deletion, deactivation or retargeting of an IdP group mapping.
  - It rejects a change with `409` `last-admin-path` if no usable path would remain.
  - Usable paths are: the active local account, a matched mapping (as before), and an active person's open-ended, effective assignment of `system-admin`.
  - Concurrent changes are serialized, so two parallel revocations cannot both pass.
- **Manual:** roles and permissions page, local assignments, English and German.

## Out of Scope

- **Person status changes and offboarding** (US-148, US-146 AC5): next S4 change.
- **Four-eyes approval** (US-150, `privilegedAssignmentApproval`).
- **Changing an existing assignment** (no contract operation), custom roles, admin UI.
- **Self-administration rules beyond assignments** (US-145: role permission changes, further SoD-1 cases).

## Capabilities

### New Capabilities

- `identity/role-assignments`: local assignment of authorization roles to persons, covering validity, effect on permissions and sessions, the SoD rules of assignment, expiry, revocation and the role preview.

### Modified Capabilities

- `identity/access-administration`: "The last path to system administration is kept" covers revocations of assignments and changes of IdP group mappings, and counts open-ended person assignments as a path.

## Impact

- **`backend/`:**
  - New `io.oea.app.access.RoleAssignments` (service), an expiry job, and `AdministrationPaths` extended (persons, advisory lock).
  - `PermissionResolver`: local grants.
  - `IdpGroupMappings` and `LocalAdminAccount`: SoD-7 check.
  - Controller operations in `io.oea.api.access` and `io.oea.api.admin` (`getAuthorizationRole`).
- **Flyway `V9__role_assignments.sql`:** table `role_assignments` as in `docs/data-model.puml` (DM-4), plus `expiry_recorded_at`.
- **`api/openapi.yaml`:**
  - The three assignment operations and `getAuthorizationRole` become implemented, with `429`.
  - Problem types `sod-conflict` and `last-admin-path` on the assignment operations.
  - `409` on the mapping update and delete.
- **Data model:** `docs/data-model.puml` (`expiry_recorded_at`), `business-objects/role-assignment.md`.
- **Docs:**
  - `docs/audit-event-catalog.md`.
  - `manual/administration/roles-and-permissions.md` and its German page.
- **`frontend/`:** no change.
