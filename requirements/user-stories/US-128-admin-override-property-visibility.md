# US-128: Grant an Explicit, Audited Override for Restricted Properties

**ID**: US-128
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Max – Operator acting as security administrator, I want to add a dedicated role to the property-visibility override list for an exceptional situation (error analysis, migration), so that its holders can read restricted properties of the entities they can already read – with an audit log entry for every override access – while nobody, not even administrators, reads restricted properties by default.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-128](../req/REQ-128-property-visibility-admin-override.md), [REQ-161](../req/REQ-161-visibility-configuration-governance.md)

## Acceptance Criteria

**AC1** (Override effective once configured):

- Given: Max (holding `security-admin`) has added the role `migration-support` to the override role list
- When: a person holding `migration-support` and `viewer` retrieves an entity with restricted properties
- Then: all values are returned; `visibilityMode` has no effect

**AC2** (Audit log):

- Given: a person holding an override role
- When: they read a restricted property through the override
- Then: the audit log contains an entry `property-override-access` with user ID, entity ID, property name and timestamp

**AC3** (Override list changeable by security administration only):

- Given: Kurt holds `metamodel-admin` but not `visibility:configure`
- When: he tries to change the override role list via UI or API
- Then: the request is rejected with 403 and the list is unchanged; a change by Max takes effect within 60 s and writes `visibility.override-changed` with old and new list

**AC4** (No implicit override):

- Given: a user holding the role `EA-Manager`, which is not in the override list
- When: they read an entity with restricted properties
- Then: restrictions apply normally; no automatic override

**AC5** (Empty by default):

- Given: a fresh instance with the default configuration
- When: a person holding `system-admin`, `security-admin` and `viewer` but none of the property's `readRoles` reads an entity with a `role-restricted` property
- Then: the property is redacted, and the override role list is empty

## Definition of Done

- [ ] AC1–AC5 fulfilled
- [ ] Audit log entry structurally validated
- [ ] Code review; linter green; entered in trace matrix

## Notes

- 2026-10-13: aligned with ADR-032 and owner decisions Q4 (override list changed only with `visibility:configure`, not in the metamodel editor) and Q14 (override list empty by default; previously `system-admin` was the implicit override role).
