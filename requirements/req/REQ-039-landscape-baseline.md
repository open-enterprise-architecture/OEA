---
id: REQ-039
title: Show the current IT landscape as the solution baseline
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
    - architecture
  business_rules: []
  stakeholders:
    - SH-04
  concept:
    - concept/30-dynamics/11-temporal-model.md
    - concept/30-dynamics/12-domain-views.md
    - concept/20-entities/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-039: Show the current IT landscape as the solution baseline

## Statement

Within a solution, the system MUST show the current state of the IT landscape as a searchable and filterable baseline; in **project mode** this state MUST be computed from the aggregation of all entities of solutions with status=`implemented`, in **plateau mode** from the entities of the solution's `fromPlateau`.

## Rationale

The solution architect can only meaningfully capture change deltas if they can see what already exists. Without this baseline, they would have to know all entities from memory or external documents – reliable scope management is then not possible. The baseline is at the same time the foundation of the diff view (REQ-041) and the conflict warning (REQ-042).

## Context

The baseline is context-bound: it appears only within a concrete solution and serves exclusively as a reference point for delta capture. It is not persisted separately but computed on demand.

**Computation rules (project mode)**: For each `entityId`, the state information most recently set in an `implemented` solution is used. If several `implemented` solutions have `modified` the same entity, the most recently implemented version applies (sorted by the solution's go-live timestamp). `retiring` deltas in `implemented` solutions remove the entity from the baseline.

**Computation rules (plateau mode)**: Entities are read directly from the linked `fromPlateau` (including all inherited entities, if the plateau itself inherits from a previous plateau).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Solution ID (context; the solution the architect is working in)
- Optional: search term (free text, name of the entity)
- Optional: filter by EntityType (dropdown of available types)

**Processing**:

- Determine the mode: `solution.fromPlateauId == null` → project mode; otherwise plateau mode
- Project mode: aggregate all EntityDeltas from solutions with status=`implemented`; determine the end result per entityId (latest-wins for `modified`; `retiring` removes the entity)
- Plateau mode: load the entities of the `fromPlateau` (including plateau inheritance)
- Restrict the result by search term and EntityType filter (client- or server-side)
- Special case empty baseline: output a notice; disable the `modified` and `retiring` options in delta capture

**Outputs**:

- Ordered, paginated entity list: EntityType, name, relevant properties (short view)
- If applicable, notice "No implemented entities available – you can only create new entities"

**Error Cases**:

- Solution ID unknown or no permission → 403/404

## Acceptance Criteria

**AC1**:

- Given: two implemented solutions exist; solution A has entity `CRM-Legacy` (new), solution B has entity `ERP-Core` (new)
- When: Michael opens a new solution (project mode) and loads the baseline
- Then: `CRM-Legacy` and `ERP-Core` are shown in the list; no other entities

**AC2**:

- Given: solution A (implemented) has entity `CRM-Legacy` (modified: name changed to "CRM-Legacy-v2"); solution B (implemented, more recent) has modified the same entity again
- When: the baseline is computed
- Then: it shows the state from solution B (the newer implementation wins)

**AC3**:

- Given: solution A (implemented) has marked entity `CRM-Legacy` as retiring
- When: the baseline is computed
- Then: `CRM-Legacy` is **not** in the baseline (retiring from an implemented solution removes the entity)

**AC4**:

- Given: plateau P0 (baseline) with entities `ERP-Core` and `CRM-Legacy`; solution with `fromPlateauId=P0` (plateau mode)
- When: Michael loads the baseline
- Then: exactly the entities of P0 are shown (including inherited entities, if any)

**AC5**:

- Given: no implemented solutions present (empty landscape, project mode)
- When: Michael loads the baseline
- Then: the notice "No implemented entities available" appears; the `modified` and `retiring` options are disabled

**AC6**:

- Given: baseline with 50 entities of various types
- When: Michael filters by `ApplicationComponent`
- Then: only entities of type `ApplicationComponent` are shown

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: aggregation from several implemented solutions (including overlaps, retiring); plateau mode variant; empty landscape; filter
- [x] Measurement tool: backend test suite (aggregation logic as unit test); E2E test for UI display
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-038 (the solution must exist); solutions with status=`implemented` (project mode) or plateau (plateau mode) as data source
- **Downstream effects**: REQ-040 (capture EntityDeltas) uses the baseline as a reference point; REQ-041 (diff view) builds on it; REQ-042 (conflict warning) also checks against this data basis

## Risks of Non-Fulfillment

- Risk 1: Without a baseline, Michael has to know the current state manually – scope errors (forgotten entities, wrongly assumed states) are likely, severe
- Risk 2: Wrong aggregation logic (e.g., retired entities remain visible) creates an incorrect basis for deltas and the diff view

## Trade-offs

- Aggregation on the fly vs. materialized view: on the fly is correct but possibly slow with many solutions. Acceptable for MVP; in case of performance problems → materialized view or cache (then a REQ addition is needed)

## Implementation Notes

- `GET /api/v1/solutions/{id}/landscape` (computes the baseline on the fly)
- Pagination recommended: cursor-based, since entity lists can potentially become large
- Aggregation query: `JOIN entity_deltas ON solution.status='implemented' GROUP BY entity_id ORDER BY solution.implemented_at DESC` – treat `retiring` deltas as tombstones

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
