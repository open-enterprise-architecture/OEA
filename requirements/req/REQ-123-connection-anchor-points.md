---
id: REQ-123
title: Anchor Points on Connections
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

# REQ-123: Anchor Points on Connections

## Statement

The system MUST allow setting, moving, and removing **anchor points** (waypoints) on connection lines. Anchor points control the path of the connection between the source and target entity. Anchor points MUST **always snap to the grid** (independent of the routing mode of the connection). Anchor points are set by clicking on the connection line, moved by drag & drop, and removed by double-click or context menu.

## Rationale

Without anchor points, connections cannot be routed precisely around entities. Snapping to the grid ensures that a visually consistent, aligned diagram results even with mixed routing modes (Curved, Straight).

## Acceptance Criteria

**AC1** (Set anchor point):

- When: the user clicks on a selected connection
- Then: a new anchor point is set on the nearest grid point and is visible as a handle

**AC2** (Anchor point always on grid):

- When: an anchor point is moved via drag & drop (any routing mode)
- Then: the anchor point snaps to the nearest grid point; sub-grid positioning is not possible

**AC3** (Remove anchor point):

- When: the user double-clicks an anchor point or selects "Remove anchor point" in the context menu
- Then: the anchor point is removed; the connection adjusts its path automatically

**AC4** (Mode behavior):

- When: connection mode = Curved and anchor point set
- Then: the Bézier curve runs through the anchor point; curve handles appear on both sides of the anchor point for curve control
- When: connection mode = Orthogonal and anchor point set
- Then: segments run at right angles to the anchor point; the anchor point lies on a grid intersection

## Dependencies

- **Prerequisites**: REQ-121 (grid), REQ-122 (routing modes)
- **Consequences**: –

## Implementation Notes

- Anchor points are stored in the diagram object per connection as `waypoints: [{x, y}]`; coordinates are always multiples of the grid unit
- Curved: interpret waypoints as Bézier control points; the Curved edge in Vue Flow natively supports `waypoints` as intermediate points
- Orthogonal: waypoints define bend points; the router automatically calculates right-angled segments between waypoints

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
