---
id: REQ-096
title: Navigation tree drag & drop with cycle protection
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-17
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
- Implemented by the OpenSpec change `us-095-navigation-tree-items` (owner decisions 2026-11-17): `moveTreeNode` (`PUT /tree-nodes/{nodeId}/position`, new parent and position) and `moveTreeNodeItem` (`PUT /tree-nodes/{nodeId}/items/{itemId}/position`, target folder and position), with `viewpoint:manage` and `entity:read`. Positions are 0-based (0–10000), count the entries the caller sees and are clamped to the end; the siblings keep a dense `sortOrder`, and new folders and entries go to the end. Rejected with 422: a folder under itself or a descendant, an unknown parent, a name taken among the new siblings and a subtree ending more than 10 levels below the root (`parentId`); an unknown target folder, a target folder that already holds the entry's target and a full folder of 200 entries (`nodeId`); the root cannot move. Moves are serialized under one lock, so concurrent moves cannot form a cycle. Native drag and drop in the sidebar (SCR-055) marks invalid drop targets; "Move up", "Move down" and "Move to…" cover keyboard and touch. Audited as `tree-node.moved` and `tree-node.item-moved`; a move to the same place writes nothing

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Solution Architect | Implementation note (us-095-navigation-tree-items) |
