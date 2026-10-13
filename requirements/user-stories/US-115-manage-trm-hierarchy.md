# US-115: Create and Reorganize the TRM Category Hierarchy

**ID**: US-115
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to create, rename, delete, and reorganize TRM categories via drag & drop, so that I can adapt the TRM to my own organizational structure.

## Reference

**Use Case**: [UC-19](../use-cases/UC-19-configure-trm.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-115](../req/REQ-115-manage-trm-hierarchy.md)

## Acceptance Criteria

**AC1** (Automatic level):
- When: I create a subcategory under "Security Services"
- Then: the `level` field is automatically set to `parent.level + 1`

**AC2** (Delete with child nodes):
- When: I want to delete a category with 3 child nodes
- Then: I receive HTTP 422 "Child nodes must be removed first"

**AC3** (Drag & drop cycle protection):
- When: I drag a category via drag & drop under one of its own descendants
- Then: the drop is visually blocked; an API attempt responds with HTTP 422

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
