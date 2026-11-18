---
id: REQ-095
title: Navigation tree items as soft references
type: functional
priority: must
status: proposed
version: 0.3.0
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
    - solution
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-095: Navigation tree items as soft references

## Statement

The system MUST enable adding and removing repository contents (element entities, catalogs, solutions; diagrams once they exist) as TreeNodeItems to folders. Removing an item MUST delete exclusively the navigation entry (TreeNodeItem); the referenced object MUST NOT be changed or deleted in the process. The system MUST mark orphaned items (target object has since been deleted) with a warning symbol and enable cleanup.

## Rationale

The navigation tree is a pure navigation aid (soft-reference principle). Coupling navigation and data deletion would lead to unintended data loss and make the tree worthless as an independent structuring layer.

## Acceptance Criteria

**AC1** (Item removal without data loss):

- When: a TreeNodeItem is removed
- Then: the TreeNodeItem is deleted; the referenced entity still exists (HTTP 200 on the entity)

**AC2** (Mark orphaned items):

- When: an entity that is referenced as an item in 2 folders is deleted
- Then: both items show a warning symbol; the entity is not restorable by removing the item

## Dependencies

- **Prerequisites**: REQ-094 (folders exist)
- **Downstream effects**: REQ-097 (multiple placement)

## Implementation Notes

- Soft-reference principle per BR-05, BR-06 in `tree-node.md`
- Check orphaned items via background job or on display (lazy vs. eager)
- Cleanup: bulk delete for orphaned items as a separate UI action
- Implemented for catalogs by the OpenSpec change `us-054-catalog-navigation-tree`, commit e44b736 (#197): `reference_id` has no foreign key, and removing an entry (`DELETE /tree-nodes/{nodeId}/items/{itemId}`) never changes the catalog (AC1). Catalogs cannot be deleted yet, so no catalog entry is orphaned; the tree leaves out an entry whose catalog no longer exists. Marking orphaned entries (AC2) follows with entities in the tree
- Realigned by the OpenSpec change `us-095-navigation-tree-items` (owner decisions 2026-11-17): element entities (`itemType` `entity`, `entityId`) and solutions (`itemType` `solution`, `referenceId`) are entries next to catalogs, placed with "Place in navigation" on the catalog browser (SCR-020) and the solution page (SCR-061), at the end of the folder, with an optional display label. A connection, an unknown or a deleted entity is rejected with 422 naming `entityId`, an unknown solution with 422 naming `referenceId`. Entities are only soft-deleted, so `tree_node_items.entity_id` references `entities (id)` (V40): the entry of a deleted entity stays in every folder, marked `deleted` and shown as "Deleted", and loses the marker when the entity is restored (AC2); it is cleaned up by removing it, without a bulk action. Solutions cannot be deleted, so their entries are never marked. Diagram entries follow with the diagrams

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-07 | Solution Architect | Implementation note for catalogs (us-054-catalog-navigation-tree) |
| 0.3.0 | 2026-11-17 | Solution Architect | Realigned with us-095-navigation-tree-items: entities and solutions as entries, the marker of a deleted entity |
