---
id: REQ-096
title: Navigation tree drag & drop with cycle protection
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - tree-node
    - catalog
    - entity
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-096: Navigation tree drag & drop with cycle protection

## Statement

The system MUST support rearranging items (`sortOrder`) and moving folders and items via drag & drop. When moving a folder, the system MUST detect cycles (BR-03: a folder must not be moved into one of its own descendants) and visually mark the drop target as invalid; API attempts with a cycle MUST be rejected with HTTP 422.

## Rationale

Drag & drop is the most ergonomic form of tree reorganization. Without cycle protection, an accidental move would make the tree inconsistent and lead to an infinite traversal.

## Acceptance Criteria

**AC1** (Cycle detection):
- When: folder A is dragged under folder B via drag & drop and B is a descendant of A
- Then: the drop target is visually marked as invalid; an API attempt responds with HTTP 422

**AC2** (Rearranging items):
- When: items within a folder are rearranged via drag & drop
- Then: `sortOrder` is updated correctly; the new order is persistent

**AC3** (Move item between folders):
- When: an item is moved from folder A to folder B via drag & drop
- Then: the item is present in B and no longer in A

## Dependencies

- **Prerequisites**: REQ-094 (folder CRUD), REQ-095 (items)
- **Downstream effects**: none

## Implementation Notes

- Cycle check: ancestor traversal before the move (recursive parent check)
- `sortOrder` as an integer field; on reordering, affected neighbors are updated
- The drag & drop library must be able to mark valid drop targets in real time

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
