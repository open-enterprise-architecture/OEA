# US-130: Nest DocumentItems Hierarchically

**ID**: US-130
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Michael – Solution Architect, I want to nest chapters within a DocumentCollection (e.g. "§5 Building Block View" → "§5.1 Whitebox Overall System"), so that I can structure complex documentation naturally without forcing everything into a flat list.

## Reference

**Use Case**: [UC-09](../use-cases/UC-09-document-solution-architecture-arc42.md)
**Persona**: Michael – Solution Architect (SH-04)
**Requirements**: [REQ-133](../req/REQ-133-document-item-nesting.md)

## Acceptance Criteria

**AC1** (Create subchapter):

- When: Michael selects "Create subchapter" on an item
- Then: new item appears indented below it in the navigation

**AC2** (Drag & drop):

- When: Michael drags an item under another via drag & drop
- Then: `parentId` and `sortOrder` are updated; structure immediately visible in navigation

**AC3** (Depth limit):

- When: Michael tries to create a subchapter at level 6
- Then: note "Maximum depth reached"; action not possible

**AC4** (Cascade delete warning):

- When: Michael deletes an item with subchapters
- Then: warning with the number of affected subchapters; confirmation required

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present (incl. cycle protection test)
- [ ] Code review; linter green; entered in trace matrix
