# US-067: Open the Connection Panel via Double-Click and Filter by Type

**ID**: US-067
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Data Architect, I want to open a panel by double-clicking the 3-dot indicator of a connection that lists all linked n-connections – and be able to filter them by connection type – so that I immediately see which data objects a data flow carries and which security controls are applied to it.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02); Kurt – Lead EA (SH-03)
**Requirements**: [REQ-063](../req/REQ-063-n-connection-canvas-rendering.md)

## Acceptance Criteria

**AC1** (panel opens):

- When: Lukas double-clicks the 3-dot indicator of DataFlow id=5
- Then: Connection panel opens; header shows "ERP → DWH (data-flow)"; list of all n-connections visible

**AC2** (filter chips in panel):

- Given: DataFlow id=5 has carries-data (×2) and security-control (×1)
- When: Panel opened
- Then: Filter bar shows chips `carries-data (2)` and `security-control (1)`; all initially active

**AC3** (chip toggle filters list):

- When: Lukas clicks chip `security-control`
- Then: Only security-control entries visible; carries-data hidden

**AC4** (multiple chips active simultaneously):

- When: Lukas activates carries-data and security-control
- Then: Both types visible; overall list of all n-connections of both types

**AC5** (target entity visible):

- Given: carries-data id=103 connects DataFlow id=5 with DataObject "Customer Master" (id=42)
- When: Panel filtered by carries-data
- Then: Entry shows: "carries-data #103 → Customer Master (data-object, ID 42)"

**AC6** (web portal read-only):

- When: CIO opens panel in the web portal
- Then: Panel shows entries; no "Create new n-connection" button; no edit icons

## Technical Notes

- Panel loads via `GET /api/v1/entities/{id}/n-connections` (filter state local in the frontend)
- Reference UI pattern: Obsidian backlink panel / hover preview

## Definition of Done

- [ ] AC1–AC6 met
- [ ] Tests: panel opening, filter chips, toggle, target entity, web portal read-only
- [ ] E2E test (Playwright) for double-click → panel → filter interaction
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
