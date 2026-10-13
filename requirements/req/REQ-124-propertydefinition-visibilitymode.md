---
id: REQ-124
title: Extend PropertyDefinition with visibilityMode
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-124: Extend PropertyDefinition with visibilityMode

## Statement

The data model of the `PropertyDefinition` (in `MetamodelConfiguration`) MUST be extended by four new fields:

| Field | Type | Default | Constraint |
|---|---|---|---|
| `visibilityMode` | enum | `public` | `[public, role-restricted, connection-scoped]` |
| `readRoles` | Role[] | [] | Mandatory if `visibilityMode = role-restricted`; at least 1 entry |
| `writeRoles` | Role[] | [] | Optional; every entry must also be contained in `readRoles`; empty = nobody may write the value under `role-restricted` except via the override of REQ-128 |
| `scopingConnectionType` | string | null | Mandatory if `visibilityMode = connection-scoped`; must reference a valid `ConnectionType` ID |

**Semantics of the modes**:

- `public`: value is readable for every authenticated user and writable for every user with entity write permission (current behavior, default)
- `role-restricted`: value is only readable for users whose active role is contained in `readRoles` and only writable for users whose active role is contained in `writeRoles`
- `connection-scoped`: value is only readable/writable for users who have a path in the graph to the requested entity via the connection type `scopingConnectionType`

Changing these fields requires the security administration permission `visibility:configure`, not the metamodel administration permission (REQ-157, REQ-161).

## Rationale

Without extending the `PropertyDefinition`, no fine-grained visibility control is possible. The configuration must reside declaratively in the metamodel so that no code change is needed for new fields.

Security review SEC-03 (2026-10-13): one `allowedRoles` list for read and write does not match concept §20.9/§21.8, which require separate read and write lists; a reader of a confidential value must not automatically be allowed to change it. Owner decision Q4: visibility configuration is security administration, separate from metamodel administration.

## Acceptance Criteria

**AC1** (Data model):

- When: a `PropertyDefinition` is created or edited
- Then: `visibilityMode` is storable; default `public` is correct

**AC2** (Validation role-restricted):

- When: `visibilityMode = role-restricted` and `readRoles` is empty, or `writeRoles` contains a role that is not in `readRoles`
- Then: saving is rejected with a validation error

**AC3** (Validation connection-scoped):

- When: `visibilityMode = connection-scoped` and `scopingConnectionType` points to a non-existent ConnectionType ID
- Then: saving is rejected with a validation error

**AC4** (Backward compatibility):

- When: an existing `PropertyDefinition` without `visibilityMode`
- Then: default `public` is applied; existing values are retained

**AC5** (Read/write separation):

- Given: `visibilityMode = role-restricted`, `readRoles = [ISO, auditor]`, `writeRoles = [ISO]`
- When: a user with only the role `auditor` reads and then writes the value
- Then: reading succeeds; writing is rejected with 403

**AC6** (Permission to change):

- Given: a user with metamodel administration permission but without `visibility:configure`
- When: they change `visibilityMode`, `readRoles`, `writeRoles` or `scopingConnectionType`
- Then: the change is rejected with 403

## Dependencies

- **Prerequisites**: REQ-032 (PropertyDefinition already modeled), REQ-036 (ConnectionType defined), REQ-157 (permission model)
- **Consequences**: REQ-125 (configuration form), REQ-126 (enforcement role-restricted), REQ-127 (enforcement connection-scoped), REQ-161 (governance of visibility configuration)

## Notes

The split of `allowedRoles` into `readRoles`/`writeRoles` changes the data model; `docs/data-model.puml`, the metamodel-configuration business object and the API contract (`PropertyVisibilityEntry`, `PropertyDefinitionDetail/Write`) must be aligned (follow-up for business engineer and solution architect).

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-03 / owner decision Q4: `allowedRoles` split into `readRoles`/`writeRoles`; changes require `visibility:configure` (AC5, AC6) |
