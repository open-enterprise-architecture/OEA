# US-123: Set and Move Anchor Points on Connections

**ID**: US-123
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to set anchor points on connection lines that always snap to the grid, so that I can route connections precisely around other elements without losing alignment.

## Reference

**Use Case**: [UC-05](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-123](../req/REQ-123-connection-anchor-points.md)

## Acceptance Criteria

**AC1** (Set anchor point):

- When: click on a selected connection line
- Then: new anchor point on the nearest grid point; visible as a handle

**AC2** (Snapping always active):

- When: an anchor point is moved via drag & drop
- Then: snaps to the grid; sub-grid positioning not possible (applies to all routing modes)

**AC3** (Remove anchor point):

- When: double-click on anchor point or context menu "Remove"
- Then: anchor point removed; connection adjusts its path

**AC4** (Curved + anchor point):

- When: mode = Curved and anchor point set
- Then: Bézier curve runs through the anchor point; curve handles visible on both sides for curve control

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
