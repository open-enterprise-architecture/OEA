# US-145: Reject Self-Administration of Roles

**ID**: US-145
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator responsible for demonstrable access controls, I want the system to reject every attempt of an administrator to change their own access, so that no single person can widen their own permissions and the separation of duties holds in an audit.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (E1)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-175](../req/REQ-175-security-event-logging.md)

## Acceptance Criteria

**AC1** (Self-assignment):

- Given: Max holds `security-admin` (with `role:assign`)
- When: he assigns `metamodel-admin` to himself
- Then: the response is 403 with the message that own access cannot be changed by oneself, no assignment is created, and an audit event `authz.denied` with reason `self-administration` exists

**AC2** (Self-revocation):

- Given: Max holds `security-admin` and `viewer`
- When: he revokes one of his own assignments
- Then: the response is 403 and the assignment remains active

**AC3** (Local system admin account linked to the person):

- Given: the local system admin account is linked to Max's person (`optionallyLinkedTo`)
- When: the local system admin account assigns any role to Max's person
- Then: the response is 403 with reason `self-administration`

**AC4** (Another administrator may perform the change):

- Given: Erika holds `role:assign` and is a different principal than Max
- When: she assigns `metamodel-admin` to Max
- Then: the assignment is stored (subject to US-150 if four-eyes approval is enabled)

## Technical Notes

- Affected components: authorization module (application layer, SoD-1 invariant)
- Affected EntityTypes/Relations: `role_assignments`, `system_admin_account.optionallyLinkedTo`
- API endpoints: role assignment and revocation operations (ADR-032 API-4); error code 403 per API-9
- Database changes: none

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit for the invariant, integration per operation)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-144
- Blocks: –

## Notes

Walking skeleton (S1–S2). SoD-1 also covers changing the permissions of a held custom role, mapping a group contained in one's own token (US-147) and setting one's own account link; these checks are added with the respective stories.
