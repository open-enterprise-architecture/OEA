---
id: REQ-122
title: Connection Routing Modes (Curved, Straight, Orthogonal)
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
    - entity
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-122: Connection Routing Modes (Curved, Straight, Orthogonal)

## Statement

The system MUST offer three routing modes for every connection on the canvas, selectable individually per connection:

| Mode | Path | Grid relation |
|---|---|---|
| **Curved** | Bézier curve; soft swing around anchor points | does **not** follow the grid |
| **Straight** | Direct line from the source to the target anchor point | does not necessarily follow the grid |
| **Orthogonal** | Exclusively 90° angles (right-angled routing) | follows the **grid** |

The routing mode MUST be changeable via the context menu of the connection (right-click) or a toolbar when a connection is selected. The default routing mode for new connections MUST be configurable in the viewpoint.

## Rationale

Different diagram types require different connection routing: ArchiMate diagrams prefer orthogonal connections; informal sketches benefit from Bézier curves; simple relationship diagrams get by with straight lines. Without these three modes, users must fall back on external diagramming tools.

## Acceptance Criteria

**AC1** (Curved mode):

- When: routing mode = Curved
- Then: the connection is rendered as a Bézier curve; the curve path is controllable via Bézier handles (via anchor points, REQ-123); the line follows no grid

**AC2** (Orthogonal mode):

- When: routing mode = Orthogonal
- Then: the connection makes exclusively 90° bends; all segments run horizontally or vertically; bend points lie on grid points (REQ-121)

**AC3** (Mode switch):

- When: the user changes the routing mode of an existing connection
- Then: the connection is re-rendered immediately; existing anchor points (REQ-123) are retained and applied to the new routing (rounded to the grid if applicable when switching to Orthogonal)

**AC4** (Viewpoint default):

- When: a new connection is dragged onto the canvas
- Then: the routing mode corresponds to the default configured in the viewpoint; without configuration = Orthogonal

## Dependencies

- **Prerequisites**: REQ-121 (grid for orthogonal mode), REQ-123 (anchor points)
- **Consequences**: –

## Implementation Notes

- Vue Flow: `edgeType='smoothstep'` (Orthogonal), `'straight'` (Straight), `'default'` (Curved/Bezier)
- Orthogonal routing: segments snap to the `snapGrid` unit; bend points always lie on grid points
- Mode switch: when switching Curved → Orthogonal, anchor points are rounded to the nearest grid point

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
