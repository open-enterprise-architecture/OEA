---
id: REQ-042
title: Conflict warning for parallel EntityDeltas
type: functional
priority: should
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
    - solution:BR-05
  stakeholders:
    - SH-04
    - SH-03
  concept:
    - concept/30-dynamics/11-temporal-model.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-042: Conflict warning for parallel EntityDeltas

## Statement

When capturing or updating an EntityDelta, the system SHOULD check whether another active solution (status ∉ {`implemented`, `archived`}) already contains an EntityDelta for the same entity; if so, a non-blocking warning with the name and owner of the conflicting solution SHOULD be displayed.

## Rationale

In organizations, several change initiatives typically run in parallel. When two solutions modify or retire the same entity, a substantive conflict arises that must be resolved in the review or go-live process. Without early visibility of these conflicts, they only become visible at the go-live attempt – at a point where resolution is expensive. The warning is deliberately non-blocking: Michael should know, but it is up to him and Kurt to resolve the conflict.

## Context

Basis is solution.md BR-05: "Entities of a solution are visible to other solutions (basis for collision detection in parallel changes to the same entity)."

An "active" conflict exists when another solution – regardless of its concrete status (draft, proposed, approved, in-progress) – has a delta for the same entityId. `implemented` and `archived` solutions are excluded: `implemented` is the realized state (no conflict anymore), `archived` is abandoned.

## Type-Specific Fields

### For type=functional

**Inputs**:

- Solution ID (current solution)
- entityId (of the delta just captured/updated)

**Processing**:

- Query: is there at least one other solution with status ∉ {`implemented`, `archived`} and a delta for the same entityId?
- If so: generate a warning with name and owner (person name, person ID) of the conflicting solution(s)
- If several conflicts: list all
- No blocking: the delta is saved in any case

**Outputs**:

- Warning: "Attention: This entity is also being changed in solution '{Name}' (owner: {Person}). The conflict must be resolved in the review process."
- Display the warning persistently on the solution and on the affected delta (not only once as a toast)

**Error Cases**:

- Conflict check fails (e.g., database error): the delta is saved anyway; the warning is omitted (error log, no user message)

## Acceptance Criteria

**AC1**:

- Given: solution A (draft, owner: Maria) contains a delta for entity `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in another solution B
- Then: a warning appears "This entity is also being changed in solution 'Solution A' (owner: Maria)"; the delta in solution B is saved anyway

**AC2**:

- Given: solution A (implemented) contains a delta for entity `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in solution B
- Then: **no** warning (implemented is not an active conflict)

**AC3**:

- Given: solution A (archived) contains a delta for entity `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in solution B
- Then: **no** warning (archived is not an active conflict)

**AC4**:

- Given: two other solutions (A and B) each have a delta for `CRM-Legacy`
- When: Michael creates a delta for `CRM-Legacy` in solution C
- Then: two warnings appear (one per conflicting solution)

**AC5**:

- Given: a warning was displayed for `CRM-Legacy` in solution B
- When: Michael reloads solution B or opens the delta list
- Then: the warning is still visible (no one-time toast, persistent display on the delta)

**AC6**:

- Given: solution A is set to `implemented` (go-live)
- When: Michael opens solution B with the delta for `CRM-Legacy`
- Then: the warning disappears (conflict resolved by go-live of A)

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: conflict with draft/proposed/approved/in-progress solution; no conflict with implemented/archived; multiple conflicts; warning persisted; warning disappears after go-live
- [x] Measurement tool: backend test suite + E2E test
- [x] Pass criterion: AC1–AC6 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-038 (solutions exist); REQ-040 (EntityDeltas exist that can be compared)
- **Conflicts**: none

## Risks of Non-Fulfillment

- Risk 1: Conflicts between parallel solutions are only discovered at go-live, where resolution is considerably more expensive (rollback risk, governance problem), severe

## Implementation Notes

- Conflict check: after every delta write (server-side); no client-only check
- `SELECT s.id, s.name, p.name as owner FROM entity_deltas ed JOIN solutions s ON ed.solution_id = s.id JOIN persons p ON s.owner_id = p.id WHERE ed.entity_id = :entityId AND s.id != :currentSolutionId AND s.status NOT IN ('implemented', 'archived')`
- Store the warning as a field on the delta object or compute it on the fly when loading the solution (both acceptable; on the fly is more consistent)
- Persistence of the warning: recommendation on the fly (always current), not as a stored flag (would be inconsistent after a status change of the other solution)

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
