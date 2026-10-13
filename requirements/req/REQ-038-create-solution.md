---
id: REQ-038
title: Create a solution and describe the architecture vision
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
    - person
    - role
  business_rules:
    - solution:BR-01
    - solution:BR-02
  stakeholders:
    - SH-04
    - SH-03
  concept:
    - concept/30-dynamics/11-temporal-model.md
    - concept/30-dynamics/12-domain-views.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-038: Create a solution and describe the architecture vision

## Statement

The system MUST allow persons with solution architect permission to create a solution with name, architecture vision (free text: goal and scope of the initiative) and – in plateau mode – the plateau references (`fromPlateauId`, `toPlateauId`); the system MUST enforce the business rules BR-01 and BR-02 of the solution.

## Rationale

Before entity changes can be captured, the initiative needs a container with a clear identity. Name and architecture vision text establish the context: whoever reads this UC or REQ later must understand why the initiative exists and what it is supposed to achieve. Without this entry step, the solution architect would jump straight into entity editing without having documented the scope.

## Context

In **project mode** (SME), `fromPlateauId` and `toPlateauId` are both null – the solution architect needs no plateau reference. In **plateau mode** (enterprise), they select a baseline plateau (`fromPlateauId`) and a target plateau (`toPlateauId`); validation ensures consistency (BR-01/BR-02).

An existing solution in status `draft` can be edited further at any time (steps 4-5 in the Basic Flow do not apply, since the solution already exists).

## Type-Specific Fields

### For type=functional

**Inputs**:

- `name` (string, mandatory): readable initiative name, max. 255 characters; unique within the instance
- `description` (string, optional): architecture vision free text (goal, scope, rationale), max. 2000 characters
- `fromPlateauId` (reference, optional): plateau mode only; dropdown of plateaus with status=`baseline`
- `toPlateauId` (reference, optional): plateau mode only; dropdown of plateaus with status=`target` or `transition`

**Processing**:

- Check the name for uniqueness (within the instance, across all non-archived solutions)
- Check BR-01: either both plateau IDs set or both null; mixed state → validation error
- Check BR-02 (plateau mode only): `fromPlateauId ≠ toPlateauId`; `toPlateauId.status` ∈ {`target`, `transition`}
- Persist the solution with status=`draft`, `ownerId=currentUser`, `createdAt=now()`

**Outputs**:

- Persisted solution with all fields; display in the solution overview
- System navigates to the solution detail view (starting point for REQ-039/REQ-040)

**Error Cases**:

- Name already taken → validation error: "Name '{X}' is already taken"
- BR-01 violation (one plateau ID set, the other null) → validation error: "Both plateau references must be set or both empty"
- BR-02 violation → validation error with the specific cause
- Missing permission → 403 Forbidden

## Acceptance Criteria

**AC1**:

- Given: Michael has solution architect permission; no plateau mode
- When: he enters the name "ERP Extension Logistics" and a description text and saves
- Then: a solution exists with status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; visible in the solution overview

**AC2**:

- Given: instance in plateau mode; plateau P0 (baseline) and P1 (target) exist
- When: Michael sets `fromPlateauId=P0` and `toPlateauId=P1` and saves
- Then: solution created with both plateau references; BR-02 is satisfied

**AC3**:

- Given: Michael enters `fromPlateauId=P0` and leaves `toPlateauId` empty
- When: he saves
- Then: validation error "Both plateau references must be set or both empty"; no object is created (BR-01)

**AC4**:

- Given: a solution with the name "ERP Extension Logistics" already exists
- When: Michael wants to create a second solution with the same name
- Then: validation error "Name 'ERP Extension Logistics' is already taken"

**AC5**:

- Given: a person without solution architect permission
- When: they try to create a solution
- Then: 403 Forbidden; no object is created

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: create solution (project mode, plateau mode, error case BR-01, BR-02, name collision, missing permission)
- [x] Measurement tool: backend test suite (integration tests)
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: Role model (solution architect permission); UC-01 (logged-in person); in plateau mode: at least one baseline plateau (UC-06)
- **Downstream effects**: REQ-039 (show baseline), REQ-040 (capture EntityDeltas), REQ-041 (diff view) require an existing solution

## Risks of Non-Fulfillment

- Risk 1: Without solution creation there is no container for EntityDeltas – the entire UC-05 workflow is blocked, critical
- Risk 2: Missing BR-01/BR-02 validation produces inconsistent plateau references that corrupt later diff calculations

## Implementation Notes

- `POST /api/v1/solutions` (RBAC: solution architect role)
- Render the `fromPlateauId`/`toPlateauId` fields only if the instance is configured in plateau mode (instance-level setting, TBD)
- Name uniqueness check: case-insensitive, trimmed

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
