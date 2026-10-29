# US-144: Assign and Revoke a Built-in Role for a Person

**ID**: US-144
**Story Points**: 5
**Status**: done
**Sprint**: S4
**Implementation**: OpenSpec change `us-144-local-role-assignments` – `listRoleAssignments`, `createRoleAssignment`, `revokeRoleAssignment`, `getAuthorizationRole`; table `role_assignments`

## Story

As Max – Operator acting as security administrator, I want to assign a built-in authorization role to a person, optionally time-boxed, and to revoke it again, so that every person has exactly the access their task requires and the change is effective and audited within a minute.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (basic flow, A3)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-003](../req/REQ-003-access-without-role-assignment.md), [REQ-164](../req/REQ-164-session-termination-revocation.md), [REQ-175](../req/REQ-175-security-event-logging.md)

## Acceptance Criteria

**AC1** (Assignment effective within 60 s):

- Given: Max holds `role:assign`, and an active person Anna has no authorization role (she sees the "no role assigned" notice, REQ-003)
- When: Max assigns `enterprise-architect` to Anna with `validTo` = end of her contract and reason "TICKET-123"
- Then: the assignment is stored with `source=local`, `grantedBy=Max`, `grantedAt`, `validFrom`, `validTo` and reason; Anna's next request after at most 60 s is authorized for an operation requiring `entity:write`; an audit event `role.assigned` with actor, target, role, source, `validFrom` and `validTo` exists

**AC2** (Preview before confirmation):

- Given: Max has selected `metamodel-admin` for Anna
- When: the assignment dialog shows the preview
- Then: it lists the permissions Anna will receive and marks the role as privileged (MFA required, REQ-171)

**AC3** (Expiry by `validTo`):

- Given: Anna holds `enterprise-architect` with `validTo` = T
- When: she calls an `entity:write` operation after T + 60 s
- Then: the response is 403, and an audit event `role.assignment-expired` exists

**AC4** (Revocation):

- Given: Anna holds `enterprise-architect` and `viewer` and has an active session
- When: Max revokes `enterprise-architect`
- Then: the assignment keeps its history with `revokedAt` and `revokedBy` (no hard delete), all of Anna's sessions are revoked within 60 s (REQ-164), her next login has only `viewer`, and an audit event `role.revoked` exists

**AC5** (Pre-provisioning):

- Given: a person with status `invited`
- When: Max assigns `viewer`
- Then: the assignment is stored and becomes effective once the person is `active` (first login)

**AC6** (Missing permission):

- Given: a principal holding only `metamodel-admin`
- When: it calls the assignment or revocation operation
- Then: the response is 403 with a generic problem body and an audit event `authz.denied` exists

## Technical Notes

- Affected components: authorization module, authorization cache (max. age 60 s, invalidated on change), session store, admin UI (access administration)
- Affected EntityTypes/Relations: `persons`, authorization roles, `role_assignments` (ADR-032 DM-4)
- API endpoints: `GET/POST /admin/persons/{personId}/role-assignments`, `POST /admin/persons/{personId}/role-assignments/{assignmentId}/revoke` (ADR-032 API-4)
- Database changes: `role_assignments` replaces `person_roles` (DM-4)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration; controlled clock for AC1 and AC3)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-143, US-003
- Blocks: US-145, US-146, US-148, US-150

## Notes

AC2 realigned by OpenSpec change `us-144-local-role-assignments`: there is no admin UI; the preview is `GET /admin/roles/{roleId}` (`getAuthorizationRole`) with the permissions of the role and its `privileged` flag. Four-eyes approval of privileged assignments (US-150) is not part of this change.

Walking skeleton (S1–S2): the bootstrap system admin account assigns built-in roles. The access review (UC-23 A8), custom roles (A5) and the SoD-3 flag (A6) are covered by later stories.
