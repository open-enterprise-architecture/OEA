# US-122: Choose Connection Routing Mode (Curved / Straight / Orthogonal)

**ID**: US-122
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to choose per connection whether it runs curved, straight, or orthogonal (right-angled on the grid), so that I can support different diagram conventions and deliberately control clarity.

## Reference

**Use Case**: [UC-05](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-122](../req/REQ-122-connection-routing-modes.md)

## Acceptance Criteria

**AC1** (Curved):

- When: routing mode = Curved
- Then: connection as a Bézier curve; no grid binding

**AC2** (Orthogonal):

- When: routing mode = Orthogonal
- Then: exclusively 90° segments; bend points on grid points

**AC3** (Mode switch):

- When: routing mode of an existing connection is changed
- Then: immediate re-rendering; existing anchor points are preserved

**AC4** (Viewpoint default):

- When: a new connection is dragged
- Then: default routing from viewpoint configuration; fallback = Orthogonal

## Definition of Done

- [ ] AC1–AC4 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
