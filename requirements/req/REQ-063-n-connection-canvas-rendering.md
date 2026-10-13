---
id: REQ-063
title: n-Connection canvas rendering (3-dot indicator + connection panel)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-010
    - ADR-007
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-063: n-Connection canvas rendering (3-dot indicator + connection panel)

## Statement

In the canvas editor of the client app (Electron, ADR-009), the system MUST display, for each connection entity that is referenced as `sourceEntityId` in at least one further connection (n-connection), a **circular 3-dot indicator** (`•••`) on the connection line; double-clicking this indicator MUST open a **connection panel** that lists all n-connections of this connection and is filterable by connection type at the top edge.

## Rationale

A connection that is itself the source of further connections would be invisibly complex without a visual cue — the user would not notice that additional information hangs on a line. The 3-dot indicator signals: "This connection has more". The connection panel with type filters makes it possible to view different kinds of n-connections (carries-data, security-control, compliance-requirement) separately. Reference: Obsidian (graph view / backlink panel).

## Context

Affected by this REQ:
- **Client app** (Electron, ADR-009): full canvas editor (React Flow, ADR-007); the 3-dot indicator and connection panel are implemented here
- **Web portal**: read-only; also shows the indicator (but no edit mode in the panel)

The indicator is precalculated server-side when a diagram is loaded: for each connection in the diagram, the system checks whether it occurs as `sourceEntityId` in an n-connection (`SELECT * FROM architecture_entities WHERE source_entity_id = ?`). The result flows into the diagram response as `hasNConnections: bool`.

## Type-Specific Fields

### 3-Dot Indicator

| Property | Value |
|---|---|
| Shape | Circle (radius ≈ 10px); subtle color (gray or the viewpoint's secondary color) |
| Content | `•••` (three dots); alternatively an icon |
| Position | Centered on the connection line; slightly offset if there are several indicators on one line |
| Visibility | Always visible when `hasNConnections=true`; does not disappear on zoom |
| Interaction | Single click: highlight indicator; double click: open connection panel |

### Connection Panel

| Element | Description |
|---|---|
| Header | Name of the connection (e.g., "ERP → DWH") + EntityType label |
| Filter bar | Horizontal chip bar at the top; one chip per existing n-connection type (e.g., `carries-data (2)`, `security-control (1)`); chip is a toggle (active/inactive); multiple selection possible |
| List | Below the filter: all n-connections of this type (filtered); per entry: ID, name, target entity (name + EntityType), properties (collapsed/expanded) |
| Empty state | "No connections of this type" when a filter is active and there are no hits |
| Edit | Button "Create new n-connection" (opens creation dialog; client app only); read-only in the web portal |

### Filter Behavior

- When opening the panel: all chips activated → all n-connection types visible
- Click on a single chip: only this type remains active (exclusive toggle); clicking again: back to "all active"
- Multiple chips can be active simultaneously (Shift-click or corresponding UX pattern TBD)

## Acceptance Criteria

**AC1** (Indicator appears):
- Given: DataFlow id=5 is the `sourceEntityId` of carries-data id=103
- When: Lukas opens a diagram that contains DataFlow id=5
- Then: The 3-dot circle indicator appears on the line of DataFlow id=5

**AC2** (No indicator without n-connection):
- Given: DataFlow id=7 is the sourceEntityId in no n-connection
- When: Diagram with DataFlow id=7 opened
- Then: No indicator on this line

**AC3** (Double click opens panel):
- When: Lukas double-clicks the 3-dot indicator on DataFlow id=5
- Then: Connection panel opens; header shows "ERP → DWH (data-flow)"; filter bar shows chip `carries-data (2)`

**AC4** (Filter chip switches view):
- Given: Panel with chips `carries-data (2)` and `security-control (1)` (both active)
- When: Lukas clicks `security-control`
- Then: Only `security-control` connections visible; `carries-data` entries hidden

**AC5** (Web portal read-only):
- When: CIO opens a diagram with an n-connection indicator in the web portal
- Then: Indicator visible; double click opens the panel read-only; "Create new n-connection" button is missing

**AC6** (Multiple indicators on one line):
- Given: DataFlow id=5 has 3 carries-data and 2 security-control connections
- When: Diagram loaded
- Then: A single indicator (not 5 separate ones); panel shows both types in the filter bar; total count visible (e.g., `•••  5`)

## Verification Method

- [x] Method: manual (UI test in the client app)
- [x] Test setup: Diagram with DataFlow with and without n-connections; panel interaction
- [x] Pass criterion: AC1–AC6 green on manual verification; AC1/AC2 also via automated test (hasNConnections flag)
- [ ] Integrated in CI: AC1/AC2 automated; AC3–AC6 E2E test (Playwright)

## Dependencies

- **Prerequisites**: REQ-061 (carries-data connections must exist); ADR-007 (React Flow); ADR-009 (Electron)
- **Downstream effects**: no direct ones

## Implementation Notes

- `hasNConnections` flag in the diagram load response (backend calculates on load)
- React Flow custom edge: when `hasNConnections=true`, a custom EdgeMarker (circle with `•••`) is rendered along with it
- Panel as React side panel / popover; loads n-connection data via `GET /api/v1/entities/{id}/n-connections`
- Filter state: local in the frontend (no server call when filtering)

## Implementation

- ADR(s): [ADR-010](../../adrs/ADR-010-n-connection-data-lineage.md) (proposed), [ADR-007](../../adrs/ADR-007-canvas-library.md), [ADR-009](../../adrs/ADR-009-client-app-framework.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft; 3-dot indicator + panel with type filter; Obsidian as reference |
