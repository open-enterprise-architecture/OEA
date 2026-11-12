# US-151: Define a Custom Authorization Role

**ID**: US-151
**Story Points**: 5
**Status**: done
**Sprint**: S10
**Implementation**: OpenSpec change `us-128-override-role-list`, commit ae59e44 (#234) – `createAuthorizationRole` with `role:assign`, SoD-2 and SoD-4 checked, audited as `role.created`; the page SCR-039 with the dialog "New role"

## Story

As Max – Operator, I want to define a custom authorization role with an explicit permission list (possibly empty), so that the organization can name roles such as `iso` or `migration-support` for property visibility and the override role list without changing the built-in roles.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (A5)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-161](../req/REQ-161-visibility-configuration-governance.md), [REQ-128](../req/REQ-128-property-visibility-admin-override.md)

## Acceptance Criteria

**AC1** (Create a role without permissions):

- Given: Max holds `role:assign`
- When: he creates the role `iso` with the name "Information Security Officer" and an empty permission list
- Then: the role exists, is not built-in, grants no permissions, can be used as a read role of a property and in the override role list, and `role.created` is audited

**AC2** (Create a role with permissions):

- Given: Max holds `role:assign`
- When: he creates the role `migration-support` with the permissions `entity:read` and `portal:read`
- Then: a person assigned to `migration-support` holds exactly these permissions

**AC3** (Separation of duties, SoD-2):

- Given: Max holds `role:assign`
- When: he creates a role with `metamodel:write` and `visibility:configure`
- Then: the request is rejected with 422 and no role is stored

**AC4** (Invalid definitions):

- When: the ID is taken (built-in or custom), a permission is unknown, or a role with an admin-class permission is marked assignable to service accounts
- Then: the request is rejected (409 for a taken ID, otherwise 422) and no role is stored

**AC5** (Without permission):

- Given: Kurt holds `metamodel-admin` but not `role:assign`
- When: he tries to create a role via UI or API
- Then: the request is rejected with 403

**AC6** (Roles page):

- Given: Max holds `role:assign`
- When: he opens the page "Authorization roles"
- Then: all built-in and custom roles are listed with their permissions and markers for built-in, privileged and override roles, and he can create a custom role in a dialog

## Technical Notes

- API endpoints: `createAuthorizationRole` (planned → implemented), `listAuthorizationRoles` (implemented)
- Database changes: none; `authorization_roles` and `role_permissions` exist since V3
- Four-eyes approval of a role with an admin-class permission (`202`) follows with US-150; until then such a role is stored at once and audited with high severity
- Changing and deleting custom roles (`updateAuthorizationRole`, `deleteAuthorizationRole`) are not part of this story

## Definition of Done

- [x] Acceptance criteria met
- [x] Tests written (unit, integration, Cucumber)
- [x] Manual updated in English and German
- [x] Entered in the traceability matrix

## Notes

- 2026-11-11: created for S10 change 4 (owner decision: custom roles are created together with the override role list, with API and an own page).
