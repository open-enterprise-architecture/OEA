---
id: REQ-041
title: Diff view of current state vs. target state of a solution
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
    - architecture
  business_rules: []
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

# REQ-041: Diff view of current state vs. target state of a solution

## Statement

Within a solution, the system SHOULD provide a diff view that compares the current state of the affected entities (baseline from REQ-039) with the target state after applying all EntityDeltas (REQ-040) and visually highlights the differences; the diff view SHOULD be updated after every delta change and MUST NOT be persisted separately.

## Rationale

Without a diff view, the solution architect has to piece together the effects of their delta captures in their head – error-prone with more than five to ten entities. The diff view makes the scope immediately verifiable: Michael sees at a glance whether he has captured all relevant entities and whether the target state matches his expectations. It is also the centerpiece of later review workflows (Kurt sees in the diff view what he is to approve).

## Context

The diff view is a computation function (read-only, not persisted): it combines the baseline (REQ-039) with the current deltas (REQ-040) and shows the result. The `should` status reflects that an MVP variant (a plain delta list without side-by-side comparison) could be sufficient for the start; however, the full diff view is needed for a usable review workflow.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Solution ID (context)

**Processing**:

1. Load the baseline (REQ-039): all entities of the current state
2. Load the EntityDeltas of the solution (REQ-040)
3. Consider only affected entities (= entities with at least one delta in this solution)
4. For each affected entity:
   - `deltaType=new`: no entry in the baseline (entity does not exist yet); target state = new entity with all properties
   - `deltaType=modified`: baseline state left/top, target state (with changed properties) right/bottom
   - `deltaType=retiring`: baseline state left/top; target state shows the entity as "out of service"

**Outputs (display)**:

| Delta type | Left side (current state) | Right side (target state) | Visual marking |
|---|---|---|---|
| `new` | – (empty) | New entity with properties | Highlighted in green |
| `modified` | Entity with old property values | Entity with changed property values | Changed properties marked yellow/blue |
| `retiring` | Entity in current state | Entity struck through / grayed out | Marked red |

The diff view shows **only affected entities** (no complete landscape dump).

**Error Cases**:
- Solution ID unknown → 404
- No deltas present → diff view is empty (with the note "No changes captured yet")

## Acceptance Criteria

**AC1**:
- Given: the solution has a `new` delta for `Salesforce (ApplicationComponent)` and a `retiring` delta for `CRM-Legacy`
- When: Michael opens the diff view
- Then: exactly two entries appear; `Salesforce` marked green (no entry on the left), `CRM-Legacy` red/struck through on the right

**AC2**:
- Given: the solution has a `modified` delta for `ERP-Core` (version: v4 → v5)
- When: Michael opens the diff view
- Then: `ERP-Core` appears once; the left side shows `version=v4`, the right side `version=v5`; only the changed property is highlighted

**AC3**:
- Given: Michael adds another delta (after opening the diff view)
- When: he saves the delta
- Then: the diff view updates without a full reload and shows the new entity

**AC4**:
- Given: the solution has no deltas
- When: Michael opens the diff view
- Then: the note "No changes captured yet" appears; no error

**AC5**:
- Given: the solution has 20 deltas of various types
- When: Michael opens the diff view
- Then: all 20 deltas are displayed; entities without a delta are **not** visible (no landscape dump)

## Verification Method

- [x] Method: test (automated) + exploration (UI-Test)
- [x] Test setup: diff view with new/modified/retiring; empty delta list; live update after adding a delta
- [x] Measurement tool: E2E test suite; snapshot tests for visual highlighting
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-038 (solution), REQ-039 (baseline), REQ-040 (EntityDeltas)
- **Downstream effects**: The diff view is the basis for future review/approval workflows (UC-06 or a separate UC); there Kurt sees exactly this view before approving

## Risks of Non-Fulfillment

- Risk 1: Without a diff view, Michael cannot verify the scope; Kurt cannot perform a meaningful review – the review workflow would be non-functional, severe

## Trade-offs

- Live update vs. manual refresh: live update increases complexity (WebSocket or polling), but is important for the interactive capture workflow. For MVP: re-render after every delta save (no streaming needed).

## Implementation Notes

- `GET /api/v1/solutions/{id}/diff` (computed on the fly; not persisted)
- Frontend: split panel or before/after layout; highlight changes at property level in the `modified` case (diff of the `changes` map)
- Efficiency: the diff computation is O(n) in the number of deltas – fast even with large landscapes, since only affected entities are computed

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft from UC-05 |
