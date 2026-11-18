# US-092: System-Defined Viewpoints Are Read-Only

**ID**: US-092
**Story Points**: 2
**Status**: done
**Sprint**: S15
**Implementation**: OpenSpec change `us-091-viewpoint-notations` – system-defined viewpoints are installed by metamodel packages (the starter package carries two, V41); update and delete answer 422 `read-only-viewpoint` (AC1); SCR-031 shows them in their own group without "Edit" and "Delete" (AC2); another package or an organization viewpoint with the same ID or name is a conflict (409), and only a re-import of the same package changes them (AC3)

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
