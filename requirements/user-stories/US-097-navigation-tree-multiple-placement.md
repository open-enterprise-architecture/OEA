# US-097: Place the Same Object in Multiple Folders

**ID**: US-097
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to reference the same repository object in multiple folders simultaneously, so that I can represent cross-connections (e.g. domain and initiative) without data duplication.

## Reference

**Use Case**: [UC-13](../use-cases/UC-13-manage-navigation-tree.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-097](../req/REQ-097-navigation-tree-multiple-placement.md)

## Acceptance Criteria

**AC1** (Multiple placement):
- When: I attach "SAP ERP" to folder "Finance" and "Cloud-Migration"
- Then: two independent TreeNodeItems exist; the entity itself is unchanged

**AC2** (Independent removal):
- When: I remove the item from "Finance"
- Then: the entry in "Cloud-Migration" remains valid

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
