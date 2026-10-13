# US-091: Keep Viewpoint Notation Mapping Consistent

**ID**: US-091
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to be able to select only notation elements that match the chosen notation in the viewpoint editor, so that no inconsistent diagram mappings arise.

## Reference

**Use Case**: [UC-12](../use-cases/UC-12-manage-viewpoints.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-091](../req/REQ-091-viewpoint-notation-mapping.md)

## Acceptance Criteria

**AC1** (Dropdown filtered by notation):

- When: I select `notation=archimate3`
- Then: the dropdown shows only `archimate3:*` elements

**AC2** (API validation):

- When: an API call saves a `bpmn:Task` element in an `archimate3` viewpoint
- Then: I receive HTTP 422

**AC3** (Dropdown reset on change):

- When: I change the notation in the form
- Then: the dropdown updates immediately; incompatible elements are reset

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
