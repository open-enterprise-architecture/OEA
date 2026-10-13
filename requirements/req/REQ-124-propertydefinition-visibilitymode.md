---
id: REQ-124
title: Extend PropertyDefinition with visibilityMode
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

The data model of the `PropertyDefinition` (in `MetamodelConfiguration`) MUST be extended by three new fields:

| Field | Type | Default | Constraint |
|---|---|---|---|
| `visibilityMode` | enum | `public` | `[public, role-restricted, connection-scoped]` |
| `allowedRoles` | Role[] | [] | Mandatory if `visibilityMode = role-restricted`; at least 1 entry |
| `scopingConnectionType` | string | null | Mandatory if `visibilityMode = connection-scoped`; must reference a valid `ConnectionType` ID |

**Semantics of the modes**:
- `public`: value is readable and writable for every authenticated user (current behavior, default)
- `role-restricted`: value is only readable/writable for users whose active role is contained in `allowedRoles`
- `connection-scoped`: value is only readable/writable for users who have a path in the graph to the requested entity via the connection type `scopingConnectionType`

## Rationale

Without extending the `PropertyDefinition`, no fine-grained visibility control is possible. The configuration must reside declaratively in the metamodel so that no code change is needed for new fields.

## Acceptance Criteria

**AC1** (Data model):
- When: a `PropertyDefinition` is created or edited
- Then: `visibilityMode` is storable; default `public` is correct

**AC2** (Validation role-restricted):
- When: `visibilityMode = role-restricted` and `allowedRoles` is empty
- Then: saving is rejected with a validation error

**AC3** (Validation connection-scoped):
- When: `visibilityMode = connection-scoped` and `scopingConnectionType` points to a non-existent ConnectionType ID
- Then: saving is rejected with a validation error

**AC4** (Backward compatibility):
- When: an existing `PropertyDefinition` without `visibilityMode`
- Then: default `public` is applied; existing values are retained

## Dependencies

- **Prerequisites**: REQ-032 (PropertyDefinition already modeled), REQ-036 (ConnectionType defined)
- **Consequences**: REQ-125 (configuration form), REQ-126 (enforcement role-restricted), REQ-127 (enforcement connection-scoped)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
