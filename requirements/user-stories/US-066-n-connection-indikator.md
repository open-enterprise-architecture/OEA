# US-066: See the n-Connection Indicator in the Canvas

**ID**: US-066
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Data Architect, I want to see at a glance which connections in the diagram carry additional n-connections – so that I know on which arrows I can retrieve further information (e.g. transported data objects) via double-click.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02); also all other canvas users (SH-03, SH-04)
**Requirements**: [REQ-063](../req/REQ-063-n-connection-canvas-rendering.md)

## Acceptance Criteria

**AC1** (indicator appears for n-connection):

- Given: DataFlow id=5 is sourceEntityId of carries-data id=103
- When: Lukas opens a diagram with DataFlow id=5
- Then: Circle with `•••` appears on the connection line; still visible when zooming out

**AC2** (no indicator without n-connection):

- Given: DataFlow id=7 has no n-connections
- When: Diagram with DataFlow id=7
- Then: No indicator on this line

**AC3** (total count displayed):

- Given: DataFlow id=5 has 5 n-connections (3 carries-data + 2 security-control)
- When: Diagram loaded
- Then: A single indicator with number "5" or `•••`; not 5 separate circles

**AC4** (web portal also shows indicator):

- When: CIO opens a diagram in the web portal with n-connection indicator
- Then: Indicator visible (read-only; no create button)

## Technical Notes

- `hasNConnections: bool` and `nConnectionCount: int` from the backend on diagram load
- Vue Flow custom edge with optional circle marker

## Definition of Done

- [ ] AC1–AC4 met
- [ ] Tests: indicator visibility, absence, counting; E2E test in the canvas
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
