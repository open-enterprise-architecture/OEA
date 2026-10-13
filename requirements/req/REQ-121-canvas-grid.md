---
id: REQ-121
title: Canvas Grid with Orientation Lines
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - viewpoint
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-121: Canvas Grid with Orientation Lines

## Statement

The diagram canvas MUST display a visible grid consisting of **4 thin guide lines** followed by **1 thicker orientation line** (repeat period: 5 grid units). Entities that are placed or resized on the canvas MUST snap to the grid points. The grid MUST be showable and hideable via a toolbar button or the keyboard shortcut `G`; snapping remains active even when the grid is hidden.

## Rationale

A grid with visual orientation (thin/thick rhythm) considerably facilitates the precise alignment of entities. Without snapping, irregular spacing arises, which makes diagrams unreadable and leads to artifacts in automatically generated export formats.

## Acceptance Criteria

**AC1** (Grid display):

- When: the canvas is opened
- Then: grid visible with a pattern of 4 thin + 1 thick line; the thicker line has a clearly higher visual contrast than the thin ones

**AC2** (Entity snapping):

- When: an entity is moved or created on the canvas
- Then: position and size snap to the nearest grid unit; sub-grid positioning is not possible

**AC3** (Show/hide grid):

- When: the user presses `G` or clicks the toolbar button
- Then: grid lines are no longer visible; snapping remains active

## Dependencies

- **Prerequisites**: UC-05 canvas available
- **Consequences**: REQ-122 (orthogonal routing uses grid units), REQ-123 (anchor points snap to the grid)

## Implementation Notes

- Grid unit: 10 px at 100% zoom; scales with the zoom factor
- Thicker line every 5 units = 50 px grid as orientation grid
- Vue Flow: `:snap-to-grid="true"` with `:snap-grid="[10, 10]"`; grid background via CSS `background-size`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
