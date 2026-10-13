# US-143: Seed the Permission Catalog and Built-in Roles

**ID**: US-143
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator acting as security administrator, I want every instance to start with the fixed permission catalog and the built-in authorization roles and to be able to retrieve the permission matrix, so that I can see which role grants which permission before I assign anything and prove the configuration in an audit.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (preconditions; walking skeleton subset)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-158](../req/REQ-158-deny-by-default-authorization.md)

## Acceptance Criteria

**AC1** (Catalog seeded):

- Given: a freshly installed instance
- When: a principal with `role:assign` calls `GET /admin/permissions`
- Then: the response lists exactly the 20 permissions of ADR-032 §3, each with its identifier and the flag `adminClass`; `metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage` and `settings:manage` are flagged admin-class

**AC2** (Built-in roles and matrix):

- Given: a freshly installed instance
- When: a principal with `role:assign` calls `GET /admin/roles`
- Then: the nine built-in roles `portal-reader`, `viewer`, `analyst`, `enterprise-architect`, `integration-writer`, `auditor`, `metamodel-admin`, `security-admin`, `system-admin` are returned with `isBuiltin=true`, their permission sets, `privileged` and `assignableToServiceAccounts` exactly as in the ADR-032 §4 matrix

**AC3** (Built-in roles immutable):

- Given: the built-in role `viewer`
- When: a principal with `role:assign` tries to change its permission set or delete it
- Then: the request is rejected with 422 and the role is unchanged (SoD-5)

**AC4** (No role without permission):

- Given: a principal holding only `metamodel-admin`
- When: it calls `GET /admin/roles` or `GET /admin/permissions`
- Then: the response is 403 with a generic problem body

## Technical Notes

- Affected components: authorization module (backend), Flyway seed data
- Affected EntityTypes/Relations: `permissions` (seeded, read-only), authorization roles, `role_permissions` (ADR-032 DM-2, DM-3)
- API endpoints: `GET /admin/permissions`, `GET /admin/roles`, `GET /admin/roles/{roleId}` (ADR-032 API-4; contract first)
- Database changes: seeded permission catalog and built-in roles; business attributes removed from the authorization role table (DM-3)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration); matrix test compares seed data with ADR-032 §4
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-013 (bootstrapping), data model update by the business engineer (ADR-032 DM-1 to DM-3), contract update (ADR-032 API-4)
- Blocks: US-144, US-145, US-146, US-147, US-149

## Notes

Walking skeleton (S1–S2) per UC-23 Implementation Notes. Custom roles (UC-23 A5) are not part of this story.
