---
id: REQ-037
title: Solution-specific metamodel extension
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
    - solution
    - plateau
  business_rules: []
  stakeholders:
    - SH-03
    - SH-02
    - SH-04
    - SH-06
  concept:
    - concept/40-extensibility/14-extensibility.md
    - concept/40-extensibility/15-schema-evolution.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-037: Solution-specific metamodel extension

## Statement

The system MUST allow creating, for a [Solution](../../business-objects/solution.md), its own `MetamodelConfiguration` with `scope=solution` that inherits the instance default metamodel and extends it with additional EntityTypes, stereotypes, and constraint rules; the solution extension MUST have its own `editMode`, configured independently of the `editMode` of the instance configuration; solution-specific types MUST NOT override or remove instance types.

## Rationale

The instance default metamodel should be stable and controlled for production plateaus (lock mode, REQ-035). At the same time, a solution architect must be able to try out new types relevant for a specific initiative before they are introduced as standard. Without solution-level scoping, experiments would either have to lift the locked default metamodel or take place on a separate instance. The two-level approach (instance locked, solution editable) allows both at once: baseline stability and freedom to innovate in target plateaus.

## Context

A [Solution](../../business-objects/solution.md) describes the delta between a baseline [plateau](../../business-objects/plateau.md) and a target plateau. The effective metamodel of a solution = instance types (inherited, read-only in this context) ∪ solution-specific types (editable, provided `editMode=gui-and-import`). Solution-specific types are visible exclusively within this solution; they do not appear in the instance default metamodel or in other solutions. Once a tried type has proven itself, it can be adopted into the instance default metamodel via import (separate governance step, not in this REQ).

## Type-Specific Fields

### For type=functional

**Inputs**:
- Solution ID (reference to an existing [Solution](../../business-objects/solution.md))
- `editMode` for the extension configuration: `gui-and-import` | `import-only`
- EntityTypeDefinitions, Stereotypes, ConstraintRules (as described in REQ-032/REQ-033, but only for the solution scope)

**Processing**:

*Creating a solution extension configuration*:
1. Check: the solution exists and does not yet have a `metamodelExtensionId` (or replaces an existing one)
2. Create a new `MetamodelConfiguration` with `scope=solution`, `solutionId`, `parentId` → instance configuration, `editMode` (freely selectable)
3. Set Solution.`metamodelExtensionId`

*Compute the effective metamodel (on entity creation within a solution)*:
1. Load the instance configuration (built-in + instance custom types)
2. Load the solution extension configuration (if present)
3. Form the union: all instance types + solution-specific types
4. On name collision (solution type has the same name as an instance type) → error on creation (BR-09: no overriding)

*Validation of new types in the solution extension*:
- Name uniqueness against built-in types + instance custom types + already existing solution types (BR-09)
- Base type (`extends`) must be contained in the effective type list (instance or solution)

**Outputs**:
- Solution extension configuration created/updated
- Effective type list for the solution (union of instance + solution)

**Error Cases**:
- Solution type has the same name as an instance type → error "Type 'X' is already defined in the instance metamodel; solution types cannot override instance types"
- Solution extension configuration has no valid `parentId` → error
- `editMode=import-only` of the instance has no influence on the solution extension → it remains editable according to its own `editMode`

## Acceptance Criteria

**AC1**:
- Given: instance metamodel with `editMode=import-only`; solution "Cloud-Experiment" without an extension configuration
- When: Kurt creates a new extension configuration with `editMode=gui-and-import` for "Cloud-Experiment" and defines `CloudService` in it
- Then: `CloudService` is available in the effective metamodel of "Cloud-Experiment"; the instance default metamodel remains unchanged and locked; `CloudService` does not appear in other solutions

**AC2**:
- Given: solution "Cloud-Experiment" with `CloudService` (solution type) and instance type `ApplicationComponent`
- When: an architect creates a new entity within "Cloud-Experiment"
- Then: both types are available for selection: `ApplicationComponent` (from the instance) and `CloudService` (solution-specific)

**AC3**:
- Given: solution extension with `editMode=gui-and-import`
- When: Kurt adds a new type via the GUI
- Then: this is possible even though the instance metamodel is `import-only` (independent `editMode` levels)

**AC4**:
- Given: a solution extension configuration is to define a type `ApplicationComponent` (already present as an instance type)
- When: Kurt saves
- Then: the error "Type 'ApplicationComponent' is already defined in the instance metamodel" appears; no entry created

**AC5**:
- Given: a solution has no extension configuration (`metamodelExtensionId=null`)
- When: an architect creates an entity within this solution
- Then: only instance types are available; no error

**AC6**:
- Given: solution extension with `editMode=import-only`
- When: Kurt updates the solution extension via a YAML file (REQ-033)
- Then: the import is possible; GUI editing of this extension is blocked

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: instance `import-only` + solution `gui-and-import`; create types; check effective metamodel; name collision; solution without extension
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-032 (GUI configuration, applicable to solution scope); REQ-033 (import, applicable to solution scope); REQ-035 (lock mode is the motivation for solution-level editability); Solution BO must exist
- **Downstream effects**: The entity creation UC (future) must respect the solution scope and compute the effective metamodel; the type selection dropdown must display instance and solution types distinguishably
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without solution-level scoping, new types can only be tried out by temporarily disabling the instance lock mode – contradicts the governance goal of REQ-035
- Risk 2: Without this mechanism, metamodel innovation is only possible via code deployment – high barrier

## Trade-offs

- Solution types cannot override instance types (BR-09): prevents a solution from corrupting the shared model; but reduces the freedom to customize per solution. Deliberate governance decision.
- Effective metamodel as a union: easy to compute, but with many solutions can lead to an unwieldy type list. Mitigation: the UI clearly shows the origin of each type (instance vs. solution).

## Implementation Notes

- DB: the `metamodel_configurations` table gets `scope`, `parent_id`, `solution_id` columns; the instance configuration has `scope=instance`, `parent_id=null`
- Effective metamodel API: `GET /solutions/{id}/effective-metamodel` → returns the union; cached, invalidated on change of either level
- UI: the type selection dropdown is grouped by "Standard (instance)" and "Solution-specific (solution name)"; solution-specific types highlighted with a badge/color
- Import for solution scope: `POST /solutions/{id}/metamodel/import` (analogous to instance import, but scope=solution)

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
