---
id: REQ-035
title: Metamodel lock mode (import-only)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/40-extensibility/14-extensibility.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-035: Metamodel lock mode (import-only)

## Statement

The operator MUST be able to put the `MetamodelConfiguration` of an instance into the `import-only` mode; in this mode, create, update, and delete operations on EntityTypeDefinitions, Stereotypes, and ConstraintRules via the GUI API MUST NOT be executed – regardless of the permission of the requesting person; changes are possible exclusively via the import path (REQ-033).

## Rationale

Organizations with strict governance requirements or GitOps processes want to manage the metamodel as versioned code in a repository. A metamodel edited manually via the GUI would in this case be an undesirable source of drift outside the controlled change process. The lock mode ensures that the YAML file remains the single source of truth and that all metamodel changes go through the same review process as code. Addresses SH-06's requirement for controlled, traceable infrastructure changes.

## Context

The `editMode` is an operator setting (not changeable by architects) in the `MetamodelConfiguration`. The import path (REQ-033) remains fully active in lock mode – it is the only way to make changes. In lock mode, the GUI continues to show all EntityTypes (read-only), but create/edit/delete buttons are disabled or hidden. The `editMode` of the lock mode itself (switching between `gui-and-import` and `import-only`) can be changed exclusively via the operator configuration (admin UI or config file), not via the same GUI that the lock mode blocks.

## Type-Specific Fields

### For type=functional

**Inputs** (operator configuration):

| Field | Type | Default | Description |
|---|---|---|---|
| `editMode` | enum | `gui-and-import` | `gui-and-import` = GUI and import allowed; `import-only` = only import allowed |

**Processing with `editMode=import-only`**:

- Incoming API requests to `POST/PUT/DELETE /admin/metamodel/entity-types`, `.../stereotypes`, `.../constraint-rules` → 403 Forbidden with message: "Metamodel editing via GUI is disabled (import-only mode). Changes only possible via import."
- Import endpoint (`POST /admin/metamodel/import`) remains fully active
- GUI: create/edit/delete actions for EntityTypes, Stereotypes, and Constraints are hidden or disabled; notice text "Metamodel in lock mode – changes only via import" appears
- Read operations (GET) remain possible without restriction
- `editMode` switch: exclusively via `PUT /admin/config/metamodel-edit-mode` (operator endpoint, separate RBAC level)

**Error Cases**:

- Attempt to make a GUI change in lock mode → 403 with a clear message (not 404 or 500)
- Attempt to change `editMode` via the regular metamodel API → 400 Bad Request

## Acceptance Criteria

**AC1**:

- Given: `editMode=import-only`
- When: Kurt tries to create a new EntityType via the GUI (REQ-032)
- Then: the request is rejected with 403 and a note pointing to the import path; the MetamodelConfiguration remains unchanged

**AC2**:

- Given: `editMode=import-only`
- When: Kurt uploads and confirms a YAML file via import (REQ-033)
- Then: the import is processed normally; new EntityTypes are in the configuration

**AC3**:

- Given: `editMode=import-only`
- When: Kurt opens the MetamodelConfiguration overview
- Then: he sees all EntityTypes (read-only); create/edit/delete buttons are missing or disabled; a notice text explains the lock mode

**AC4**:

- Given: `editMode=gui-and-import` (default)
- When: no explicit configuration has been made
- Then: GUI and import editing are both active (no difference to REQ-032/REQ-033 behavior)

**AC5**:

- Given: the operator changes `editMode` from `import-only` to `gui-and-import`
- When: Kurt subsequently creates an EntityType via the GUI
- Then: this is possible again; no restart needed (configuration is evaluated at runtime)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: set `editMode`; attempt GUI operations (expect 403); import operation (expect success); switch to `gui-and-import` (expect GUI operations possible again)
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-032 (GUI API that is blocked); REQ-033 (import path that remains active)
- **Downstream effects**: UC-04 A5 (collaborative editing): with `import-only` all manual changes are prevented
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without lock mode, architects can manually overwrite the metamodel managed via CI/CD – drift from the versioned YAML, medium to severe in GitOps setups

## Trade-offs

- `import-only` raises the barrier for quick fixes (no quick GUI fix possible anymore) – a deliberate governance trade-off; the operator decides whether the gain in control justifies the reduced agility.

## Implementation Notes

- Middleware/guard: on every writing metamodel API request read `editMode` from `MetamodelConfiguration`; with `import-only` → 403; no pass-through to the service layer
- `editMode` switch: dedicated admin endpoint with its own RBAC check (operator role, not metamodel editor role)
- Frontend: `editMode` as a global UI state variable; components render conditionally based on this flag

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
