---
id: REQ-039
title: Show the current IT landscape as the solution baseline
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-12
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

Within a solution, the system MUST show the current state of the IT landscape as a searchable and filterable baseline; in **project mode** this state MUST be the current catalog, that is the entities of element metatypes that are not deleted, in **plateau mode** from the entities of the solution's `fromPlateau`.

## Rationale

The solution architect can only meaningfully capture change deltas if they can see what already exists. Without this baseline, they would have to know all entities from memory or external documents – reliable scope management is then not possible. The baseline is at the same time the foundation of the diff view (REQ-041) and the conflict warning (REQ-042).

## Context

The baseline is context-bound: it appears only within a concrete solution and serves exclusively as a reference point for delta capture. It is not persisted separately but computed on demand.

**Computation rules (project mode)**: the baseline is the current catalog: every entity of an element metatype that is not deleted, in its current version. The go-live of a solution (S14) applies its deltas to the catalog, so implemented changes appear in the baseline without an aggregation over solutions (owner decision 2026-11-12).

**Computation rules (plateau mode)**: Entities are read directly from the linked `fromPlateau` (including all inherited entities, if the plateau itself inherits from a previous plateau).

## Type-Specific Fields

### For type=functional

**Inputs**:

- Solution ID (context; the solution the architect is working in)
- Optional: search term (free text, name of the entity)
- Optional: filter by EntityType (dropdown of available types)

**Processing**:

- Determine the mode: `solution.fromPlateauId == null` → project mode; otherwise plateau mode
- Project mode: read the entities of element metatypes that are not deleted from the catalog
- Plateau mode: load the entities of the `fromPlateau` (including plateau inheritance)
- Restrict the result by search term (part of the name, case-insensitive, at most 255 characters) and metatype filter, server-side; order by name case-insensitively, then by ID
- Special case empty baseline: output a notice; disable the `modified` and `retiring` options in delta capture

**Outputs**:

- Ordered, paginated entity list: metatype, name and version, with the total and a cursor to the next page
- If applicable, notice "No entities in the current landscape – you can only create new entities"

**Error Cases**:

- Solution ID unknown or no permission → 403/404

## Acceptance Criteria

**AC1**:

- Given: the catalog holds the applications `CRM-Legacy` and `ERP-Core` and a data flow between them
- When: Michael opens a new solution (project mode) and loads the baseline
- Then: `CRM-Legacy` and `ERP-Core` are shown in the list; no other entities

**AC2**:

- Given: `CRM-Legacy` was renamed to "CRM-Legacy-v2" in the catalog
- When: the baseline is loaded
- Then: it shows "CRM-Legacy-v2" in its current version

**AC3**:

- Given: `CRM-Legacy` is deleted in the catalog
- When: the baseline is loaded
- Then: `CRM-Legacy` is **not** in the baseline

**AC4**:

- Given: plateau P0 (baseline) with entities `ERP-Core` and `CRM-Legacy`; solution with `fromPlateauId=P0` (plateau mode)
- When: Michael loads the baseline
- Then: exactly the entities of P0 are shown (including inherited entities, if any)

**AC5**:

- Given: the catalog holds no entity (empty landscape, project mode)
- When: Michael loads the baseline
- Then: the notice "No entities in the current landscape – you can only create new entities" appears; the `modified` and `retiring` options are disabled

**AC6**:

- Given: baseline with 50 entities of various types
- When: Michael filters by `ApplicationComponent`
- Then: only entities of type `ApplicationComponent` are shown

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: catalog with element and connection entities, deleted and renamed entities; plateau mode variant (S14); empty landscape; search and filter
- [x] Measurement tool: backend integration and Cucumber tests; frontend component tests and screen stories for the UI display
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-038 (the solution must exist); the catalog (project mode) or the plateau (plateau mode) as data source
- **Downstream effects**: REQ-040 (capture EntityDeltas) uses the baseline as a reference point; REQ-041 (diff view) builds on it; REQ-042 (conflict warning) also checks against this data basis

## Risks of Non-Fulfillment

- Risk 1: Without a baseline, Michael has to know the current state manually – scope errors (forgotten entities, wrongly assumed states) are likely, severe
- Risk 2: A wrong baseline (e.g., deleted entities remain visible) creates an incorrect basis for deltas and the diff view

## Trade-offs

- Aggregation on the fly vs. materialized view: on the fly is correct but possibly slow with many solutions. Acceptable for MVP; in case of performance problems → materialized view or cache (then a REQ addition is needed)

## Implementation Notes

- `GET /api/v1/solutions/{id}/landscape` (`getSolutionLandscape`, `entity:read`), read on demand
- Paging: `page`, `pageSize` (1–200, default 50) and an opaque cursor, as for the catalog query (REQ-071)
- Realigned by the OpenSpec change `us-039-solution-baseline` (owner decision 2026-11-12): the baseline in project mode is the current catalog, not the aggregate of the deltas of implemented solutions; AC1–AC3 and AC5 follow from it. Connections are not listed; the baseline shows no property values
- AC4 (plateau mode) follows with the plateaus in S14

## Implementation

- ADR(s): –
- Implemented by: OpenSpec change `us-039-solution-baseline`, commit 624c836 (#241) (project mode: AC1–AC3, AC5, AC6; plateau mode AC4 in S14)
- Tests: `SolutionLandscapeTests`, `SolutionLandscapeLatencyIT`, `features/solution/solution-baseline.feature`, `SolutionPage.spec.ts`
- Verification status: partially verified (project mode)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
| 0.2.0 | 2026-11-12 | Change us-039-solution-baseline | Project mode implemented; the baseline is the current catalog; plateau mode deferred to S14 |
