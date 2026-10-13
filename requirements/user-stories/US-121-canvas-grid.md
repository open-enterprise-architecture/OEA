# US-121: Canvas Grid with Guide Lines

**ID**: US-121
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see a grid with 4 thin and 1 thick guide line on the diagram canvas that snaps entities when placing them, so that my diagrams are precisely aligned and visually consistent.

## Reference

**Use Case**: [UC-05](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-121](../req/REQ-121-canvas-grid.md)

## Acceptance Criteria

**AC1** (Grid visible):
- When: the canvas is opened
- Then: grid with pattern 4 thin + 1 thick line visible; thick line clearly distinguishable

**AC2** (Snapping):
- When: an entity is moved on the canvas
- Then: the position snaps to the nearest grid point; free positioning not possible

**AC3** (Show/hide):
- When: `G` pressed or toolbar button clicked
- Then: grid lines hidden; snapping remains active

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
