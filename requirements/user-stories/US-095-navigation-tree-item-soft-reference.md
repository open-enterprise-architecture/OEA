# US-095: Navigation Tree Item as Soft Reference

**ID**: US-095
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to attach entities, diagrams, and catalogs as items to folders and remove them again without modifying the referenced object, so that the navigation tree remains an independent structuring layer.

## Reference

**Use Case**: [UC-13](../use-cases/UC-13-manage-navigation-tree.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-095](../req/REQ-095-navigation-tree-item-soft-reference.md)

## Acceptance Criteria

**AC1** (Remove item without data loss):

- When: I remove a TreeNodeItem
- Then: the item is deleted; the referenced entity still exists

**AC2** (Mark orphaned items):

- When: an entity is deleted that is referenced as an item in 2 folders
- Then: both items show a warning symbol

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
