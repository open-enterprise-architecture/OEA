# US-128: System Admin Sees All Properties Despite Restriction

**ID**: US-128
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator and system administrator, I want to be able to read all properties of an entity when needed (error analysis, migration), even if they are set to `role-restricted` or `connection-scoped`, so that I can ensure system integrity — with an audit log entry for every override access.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-128](../req/REQ-128-property-visibility-admin-override.md)

## Acceptance Criteria

**AC1** (Override effective):

- When: a system admin retrieves an entity with restricted properties
- Then: all values are returned; `visibilityMode` has no effect

**AC2** (Audit log):

- When: a system admin reads a restricted property
- Then: audit log contains an entry `property-override-access` with user ID, entity ID, property name, and timestamp

**AC3** (Override roles configurable):

- When: Kurt adjusts the override role list in the metamodel editor
- Then: change takes effect immediately; new roles have override access; removed roles lose it

**AC4** (No implicit override):

- When: a user has the `EA-Manager` role, but it is not in the override list
- Then: restrictions apply normally; no automatic override

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Audit log entry structurally validated
- [ ] Code review; linter green; entered in trace matrix
