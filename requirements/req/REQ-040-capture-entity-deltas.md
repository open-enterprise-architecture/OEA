---
id: REQ-040
title: Capture the EntityDeltas of a solution
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - solution
  business_rules:
    - solution:BR-03
    - solution:BR-05
  stakeholders:
    - SH-04
  concept:
    - concept/20-entities/06-core-entity-types.md
    - concept/30-dynamics/11-temporal-model.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-040: Capture the EntityDeltas of a solution

## Statement

The system MUST allow the solution architect to capture, edit, and remove EntityDeltas of the types `new`, `modified`, and `retiring` within a solution; solutions with status=`implemented` MUST NOT be modifiable (BR-03). New entities (`deltaType=new`) MUST be creatable both via the **catalog** (form) and via the **diagram** (canvas); in the diagram, a suggestion list with existing components from the current IT landscape MUST be displayed when entering the name.

## Rationale

EntityDeltas are the core of the architecture vision: they describe precisely which entities arise, change, or go out of service through this initiative. Without this structured delta capture, the solution remains a free-text document without machine-readable scope delimitation – conflict detection, diff view, and later impact analyses would not be possible.

## Context

An `EntityDelta` is a value object (not a business object of its own) and part of the solution. It references either an already existing entity from the IT landscape (`modified`, `retiring`) or describes a new entity that is created for the first time by this solution (`new`). Exactly one delta is allowed per entity and solution (no simultaneous `new` + `retiring` for the same entity).

## Type-Specific Fields

### For type=functional

**Inputs**:

*deltaType=new – catalog path (form)*:
- EntityType (dropdown of available types per MetamodelConfiguration)
- Name (string, mandatory)
- Properties (per EntityType schema)

*deltaType=new – diagram path (canvas)*:
- Place the entity on the canvas (drag from palette or double-click)
- Name input directly on the canvas element; while typing: suggestion list with entities from the current IT landscape (REQ-039), filtered by the typed text (prefix and substring search); selecting from the list marks the entity as `deltaType=modified` or `retiring` instead of `new`
- EntityType selectable via context menu or palette

*deltaType=modified*:
- entityId (reference to an entity from the baseline, REQ-039)
- `changes` map: affected properties with before and after value

*deltaType=retiring*:
- entityId (reference to an entity from the baseline, REQ-039)
- No further content required

**Processing**:
- BR-03: Solution.status == `implemented` → reject all write access to deltas with 409 Conflict
- Uniqueness check: a solution may have at most one delta per entityId; on duplicate → validation error
- `modified`/`retiring` validate that the referenced entityId is present in the baseline (REQ-039)
- `new` needs no reference check against the baseline (entity does not exist yet)
- Persist the delta as part of the solution (no separate DB record; embedded in the solution or closely linked)

**Outputs**:
- Updated delta list of the solution
- Trigger for updating the diff view (REQ-041) and checking for conflicts (REQ-042)

**Error Cases**:
- Solution status=`implemented` → 409 Conflict: "This solution is completed and can no longer be modified"
- Duplicate delta for the same entityId → validation error: "A delta already exists for this entity in this solution"
- Unknown entityId for `modified`/`retiring` → validation error: "Entity not found in the current landscape"
- Missing permission → 403 Forbidden

## Acceptance Criteria

**AC1**:
- Given: Michael is working on a solution in status `draft`; entity `CRM-Legacy` (ApplicationComponent) is in the baseline
- When: he marks `CRM-Legacy` as `retiring`
- Then: `CRM-Legacy` appears in the solution's delta list as `deltaType=retiring`; the diff view (REQ-041) shows it as "out of service"

**AC2**:
- Given: same starting situation as AC1
- When: Michael creates a new entity `Salesforce` of type `ApplicationComponent` with `deltaType=new`
- Then: `Salesforce` appears in the delta list as `deltaType=new`; `Salesforce` is **not** yet in the baseline (it is newly added by this solution)

**AC3**:
- Given: entity `ERP-Core` (ApplicationComponent) is in the baseline
- When: Michael marks `ERP-Core` as `modified` and changes the version from v4 to v5
- Then: the delta contains `changes = {version: {before: "v4", after: "v5"}}`; the diff view shows the change

**AC4**:
- Given: the solution has status=`implemented`
- When: Michael tries to add a new delta
- Then: 409 Conflict with message "This solution is completed and can no longer be modified"

**AC5**:
- Given: `CRM-Legacy` already has a `retiring` delta in this solution
- When: Michael tries to add `CRM-Legacy` again as `modified`
- Then: validation error "A delta already exists for CRM-Legacy in this solution"

**AC6**:
- Given: Michael enters, as a `modified` delta, an entityId that is not in the baseline
- When: he saves
- Then: validation error "Entity not found in the current landscape"

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: create delta new/modified/retiring; BR-03 (implemented read-only); duplicate check; invalid entityId
- [x] Measurement tool: backend test suite
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-038 (the solution must exist, status=`draft`); REQ-039 (baseline as reference for `modified`/`retiring`); MetamodelConfiguration for the EntityType dropdown
- **Downstream effects**: REQ-041 (diff view) reads the deltas; REQ-042 (conflict warning) checks overlaps with other solutions

## Risks of Non-Fulfillment

- Risk 1: Without structured delta capture, the solution is only free text without machine-readable scope delimitation – automation (conflict detection, diff, later impact analyses) is not possible, severe

## Implementation Notes

- `POST/PUT/DELETE /api/v1/solutions/{id}/deltas`
- Whether the EntityDelta is stored as an embedded JSON array in the solution or as a separate table with FK is a solution design decision; both are permissible
- `changes` map for `modified`: `{propertyName: {before: any, after: any}}`; the before value can be prefilled automatically from the baseline (UX improvement, not mandatory for MVP)

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
| 0.2.0 | 2026-06-26 | requirements-engineer | Added two creation paths (catalog + diagram); specified diagram name completion with landscape suggestions |
