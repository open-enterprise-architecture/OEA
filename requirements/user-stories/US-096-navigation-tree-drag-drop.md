# US-096: Reorganize the Navigation Tree via Drag & Drop

**ID**: US-096
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to move and rearrange folders and items in the navigation tree via drag & drop, so that I can adjust the tree structure quickly and ergonomically.

## Reference

**Use Case**: [UC-13](../use-cases/UC-13-manage-navigation-tree.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-096](../req/REQ-096-navigation-tree-drag-drop.md)

## Acceptance Criteria

**AC1** (Cycle protection):

- When: I drag folder A under a descendant of A
- Then: the drop target is marked as invalid; an API attempt responds with HTTP 422

**AC2** (Reordering items):

- When: I rearrange items within a folder
- Then: `sortOrder` is updated correctly and the order is persistent

**AC3** (Move item between folders):

- When: I move an item from folder A to folder B
- Then: the item is in B and no longer in A

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
