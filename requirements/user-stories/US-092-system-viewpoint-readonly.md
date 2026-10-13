# US-092: System-Defined Viewpoints Are Read-Only

**ID**: US-092
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want system-defined viewpoints such as ArchiMate 3 or BPMN 2.0 to be non-editable and non-deletable, so that the rendering system stays stable.

## Reference

**Use Case**: [UC-12](../use-cases/UC-12-manage-viewpoints.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-092](../req/REQ-092-system-viewpoint-read-only.md)

## Acceptance Criteria

**AC1** (API write protection):
- When: I send a DELETE request for a system-defined viewpoint
- Then: I receive HTTP 422

**AC2** (UI write protection):
- When: system-defined viewpoints are shown in the list
- Then: edit and delete buttons are not visible

**AC3** (Import protection):
- When: system-defined viewpoints are contained in an import
- Then: they are not overwritten

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
