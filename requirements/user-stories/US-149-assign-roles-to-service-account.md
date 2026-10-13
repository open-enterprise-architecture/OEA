# US-149: Assign Roles to a Service Account

**ID**: US-149
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator, I want to assign only non-privileged, explicitly assignable roles to a service account, so that an integration such as the CMDB importer can write entities but a leaked client secret can never be used for administration.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (A4, E2)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-176](../req/REQ-176-machine-credential-lifecycle.md), [REQ-155](../req/REQ-155-service-account-management.md), [REQ-157](../req/REQ-157-permission-model.md)

## Acceptance Criteria

**AC1** (Only assignable roles offered):

- Given: a service account `cmdb-importer` exists
- When: Max opens its role assignment dialog
- Then: only roles flagged `assignableToServiceAccounts` are offered (built-in: `viewer`, `integration-writer`, `auditor`)

**AC2** (Assignment effective):

- Given: Max assigns `integration-writer` to `cmdb-importer`
- When: the service account obtains a client credentials token and calls an entity write operation
- Then: the operation is authorized (`entity:write`); the token carries only the `client_id`, and the permissions are resolved server-side; an audit event `role.assigned` exists

**AC3** (Privileged role rejected):

- Given: a service account
- When: Max assigns `system-admin` or any role containing an admin-class permission via UI or API
- Then: the request is rejected with 422 naming the restriction, and an audit event `authz.denied` exists (SoD-4)

**AC4** (Custom role flag validated):

- Given: a custom role containing `role:assign`
- When: Max tries to flag it `assignableToServiceAccounts`
- Then: the request is rejected with 422

**AC5** (No administrative access):

- Given: `cmdb-importer` holds `integration-writer`
- When: it calls any operation requiring an admin-class permission
- Then: the response is 403

## Technical Notes

- Affected components: authorization module, service account management
- Affected EntityTypes/Relations: `persons` (`person_type=service-account`), `role_assignments`, authorization roles (`assignable_to_service_accounts`)
- API endpoints: role assignment operations for service accounts; `ServiceAccountWrite.roles` restricted (ADR-032 API-5)
- Database changes: none beyond US-143/US-144

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (integration with client credentials flow)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-142, US-144
- Blocks: US-141 (batch import needs a service account with `integration-writer`)

## Notes

Service accounts cannot be targets of IdP group mappings (US-147 AC8).
