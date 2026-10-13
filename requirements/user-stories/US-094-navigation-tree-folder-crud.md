# US-094: Create, Rename, and Delete Navigation Tree Folders

**ID**: US-094
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create, rename, and delete folders in the navigation tree, so that I can structure and navigate the EA repository sensibly.

## Reference

**Use Case**: [UC-13](../use-cases/UC-13-manage-navigation-tree.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-094](../req/REQ-094-navigation-tree-folder-crud.md)

## Acceptance Criteria

**AC1** (Create folder):
- When: I create a new folder under a parent
- Then: I receive HTTP 201 and the folder is visible in the tree

**AC2** (Reject duplicate name):
- When: I create a folder with an already existing name under the same parent
- Then: I receive HTTP 422 "Name already exists"

**AC3** (Recursive deletion):
- When: I delete a folder with child folders and items
- Then: all child folders and items are removed; referenced entities remain untouched

**AC4** (Root node protection):
- When: I try to delete the root node
- Then: I receive HTTP 422 "Root node cannot be deleted"

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
