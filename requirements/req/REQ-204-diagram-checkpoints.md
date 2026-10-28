---
id: REQ-204
title: Named checkpoints of diagrams
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-10-28
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-24
    - UC-14
    - UC-15
  business_objects:
    - diagram
  business_rules:
    - BR-DG-06
  stakeholders:
    - SH-03
    - SH-04
    - SH-01
  concept:
    - concept/70-platform/21-api-architecture.md
    - concept/30-dynamics/11-temporal-model.md
  adrs:
    - ADR-047
supersedes: []
superseded_by: []
---

# REQ-204: Named checkpoints of diagrams

## Statement

The system SHOULD let a participant who may edit a diagram name its current state as a checkpoint; checkpoints SHOULD be highlighted in the change history of the diagram and SHOULD be comparable with other states and restorable as a whole.

## Rationale

Continuous co-editing removes the explicit save in the canvas (UC-10 step 9). A named checkpoint gives teams back the ability to record an agreed state – for example the result of a workshop – and to return to it (UC-24; owner decision of 2026-10-28, ADR-047).

## Context

A checkpoint is a `DiagramCheckpoint` of the [Diagram](../../business-objects/diagram.md) and refers to a position in the diagram's operation log, not to one author's version. Versions themselves are formed automatically per author and time window (REQ-199); checkpoints are the only states a person names deliberately.

## Type-Specific Fields

### For type=functional

**Trigger**: A participant names the current state, renames a checkpoint, compares it or restores it

**Input**: name (1–200 characters, unique within the diagram, case-insensitive)

**Output / Behavior**: the checkpoint in the change history of the diagram, highlighted; on restore, the whole diagram is set to the state of the checkpoint as a new version of the restoring person, which all participants see live

**Business rules**: BR-DG-06; creating, renaming and restoring a checkpoint is recorded in the audit trail; restoring requires edit permission on the diagram

## Acceptance Criteria

**AC1** (Name a state):

- Given: Kurt may edit the diagram "CRM migration – target state"
- When: he names the current state "Workshop result 2026-10-28"
- Then: the change history shows the checkpoint highlighted with his name and the time

**AC2** (Restore):

- Given: after the checkpoint, Michael and Franz moved and renamed shapes
- When: Kurt restores the checkpoint
- Then: the diagram shows the state of the checkpoint for all participants, and the history shows the restore as a new version by Kurt; the later versions of Michael and Franz remain in the history

**AC3** (Compare):

- Given: a diagram has the checkpoint "Workshop result 2026-10-28" and later changes
- When: a participant compares the checkpoint with the current state
- Then: added, removed, moved and renamed shapes are marked

**AC4** (Read-only participant):

- Given: Anna may only read the diagram
- When: she opens the change history
- Then: she sees the checkpoints and can compare them, but cannot name or restore one

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: two or more browser sessions of different persons against the same instance (Playwright with several browser contexts)
- [x] Measuring tool: Playwright, backend integration tests
- [x] Pass criterion: all acceptance criteria green
- [ ] Integrated in CI: planned with the implementing change

## Dependencies

- **Prerequisites**: BO diagram; REQ-199 (operation log and versions); ADR-047
- **Downstream effects**: UC-14 (history view shows checkpoints), UC-15 (restore of diagrams)

## Risks of Non-Fulfillment

- Without an explicit save, teams cannot mark an agreed state and fall back to exports or screenshots (medium)

## Trade-offs

- Restoring is only offered for the whole diagram, not for the changes of one author, because selectively reverting one author would conflict with later changes of others (ADR-047)

## Implementation

- ADR(s): ADR-047
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Owner decision of 2026-10-28: version granularity with automatic versions per author (2 minutes idle, 15 minutes at most) and named checkpoints.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-28 | requirements-engineer | Initial draft (owner decision on version granularity) |
