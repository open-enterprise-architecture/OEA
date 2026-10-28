---
id: REQ-199
title: Concurrent editing of diagrams and documents with automatic merge
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-10-28
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-24
  business_objects:
    - diagram
    - diagram-note
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
    - SH-08
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-047
    - ADR-007
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-199: Concurrent editing of diagrams and documents with automatic merge

## Statement

The system SHOULD let several participants edit the same diagram or document at the same time without locks and SHOULD merge their changes automatically, so that all participants end with the same state and no participant's change is lost without notice.

## Rationale

Workshops and parallel modeling are only fluent if nobody has to wait for a lock or resolve "someone else changed this" dialogs (UC-24 steps 5 and 6; owner decision of 2026-10-28: real co-editing).

## Context

Applies to the layout and diagram-only elements of diagrams, to the entities and connections changed through a diagram, and to documents (arc42, UC-09). The merge happens on the server, which also persists the result as versions per author (§21.10). Explicit saving in the canvas (UC-10 step 9) is replaced by continuous saving.

## Type-Specific Fields

### For type=functional

**Trigger**: Two or more participants change the same diagram or document within overlapping time

**Input**: edit operations (add, move, resize, rename, delete shapes and edges; edit text)

**Output / Behavior**: a merged state that is identical for all participants; changes of different attributes of the same element are all kept; concurrent changes of the same attribute end with one deterministic value that every participant sees

**Business rules**: deleting an element that another participant is changing deletes it and informs the other participant; undo affects only the participant's own changes; changes made while offline are merged on reconnect, and changes that cannot be applied are reported and kept as a copy; a version groups the operations of one author on one object and closes after 2 minutes without a further operation of that author, after 15 minutes at the latest, or when the author leaves or disconnects (defaults, configurable by the administrator; ADR-047); every accepted operation is recorded with author, attribute and old and new value

## Acceptance Criteria

**AC1** (Different attributes):

- Given: Franz and Kurt have the same diagram open
- When: Franz renames the ERP system while Kurt moves it
- Then: the ERP system has Franz's name and Kurt's position for both

**AC2** (Same attribute):

- Given: two participants change the name of the same shape at the same moment
- When: both changes reach the server
- Then: both participants see the same final name, and the other name is in the version history

**AC3** (Delete while editing):

- Given: Michael moves a shape that Kurt deletes at the same time
- When: both changes are merged
- Then: the shape is deleted for both, and Michael sees a notice that Kurt deleted it

**AC4** (Offline merge):

- Given: Franz edits a diagram while his connection is lost for 2 minutes and Kurt keeps editing
- When: Franz's connection returns
- Then: the changes of both are merged, and nothing of either is lost

**AC5** (Undo):

- Given: Kurt and Michael edited the same diagram
- When: Kurt undoes his last change
- Then: only Kurt's change is undone; Michael's changes remain

**AC6** (Versions per author):

- Given: three participants edited a diagram for 30 minutes
- When: the change history of the diagram is opened (UC-14)
- Then: it shows versions attributed to exactly one author each, not one version per operation

**AC7** (Version window):

- Given: Kurt moves three shapes within one minute, pauses for 3 minutes and then renames a shape
- When: the change history of the diagram is opened
- Then: it shows two versions by Kurt – the first with the three moves, the second with the rename – and the audit trail shows all four operations

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green; randomized concurrent operation tests converge to identical states in 100 % of runs
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: ADR-047; REQ-203
- **Downstream effects**: UC-10 (continuous saving instead of explicit saving), UC-14 (version granularity), REQ-072 (canvas performance with live updates)

## Risks of Non-Fulfillment

- Lost changes in workshops destroy trust in the tool; teams fall back to whiteboards (high)

## Trade-offs

- CRDT/OT increases implementation effort and the size of the stored state compared with locking; owner decision for real co-editing

## Implementation

- ADR(s): ADR-047, ADR-007, ADR-009
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner request and decisions of 2026-10-28 (UC-24): priority *should* after the MVP; presence and live changes in every view; real co-editing with automatic merge; sticky notes and comment threads in diagrams.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (UC-24) |
| 0.2.0 | 2026-10-28 | requirements-engineer | Version granularity set: one author per version, 2 minutes idle, 15 minutes at most (owner decision, ADR-047) |
