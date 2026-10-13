# US-093: Viewpoint Deletion with Diagram Warning

**ID**: US-093
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see a warning about how many diagrams are affected when deleting a viewpoint, so that I do not unknowingly create orphaned diagram references.

## Reference

**Use Case**: [UC-12](../use-cases/UC-12-manage-viewpoints.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-093](../req/REQ-093-viewpoint-delete-warning.md)

## Acceptance Criteria

**AC1** (Warning for a used viewpoint):

- When: I delete a viewpoint that is used by 5 diagrams
- Then: I see the warning "5 diagrams will be affected"; after confirmation it is deleted

**AC2** (No warning for an unused viewpoint):

- When: I delete a viewpoint that is used by no diagram
- Then: a direct confirmation dialog appears without warning text

## Definition of Done

- [ ] AC1–AC2 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
