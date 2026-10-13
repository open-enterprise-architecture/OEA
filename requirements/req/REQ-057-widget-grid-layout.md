---
id: REQ-057
title: Configure widget grid layout (GridPosition)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
  business_rules: []
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-057: Configure widget grid layout (GridPosition)

## Statement

The system MUST store a `GridPosition` for each widget of a dashboard and return it at runtime; it determines where the widget is placed in the dashboard's **12-column grid**. The system MUST reject `col + width > 12` (exceeding the grid width) as a validation error. Vertical rows are unlimited (the dashboard scrolls); widget overlap is not checked server-side (client responsibility).

## Rationale

A uniform grid layout (12 columns, analogous to the Bootstrap/Material grid) enables flexible positioning of widgets without fixed constraints. Server-side width validation prevents obviously faulty layouts; overlap detection is a client task (drag-and-drop editor in the client app).

## Type-Specific Fields

### GridPosition Fields

| Field | Type | Value Range | Description |
|---|---|---|---|
| col | integer | 1–12 | Start column (1-based) |
| row | integer | ≥ 1 | Start row (1-based; unlimited downwards) |
| width | integer | 1–12 | Column width; `col + width` must not exceed 13 |
| height | integer | 1–6 | Height in grid units (1 = smallest unit, ~150px) |

GridPosition is set and validated on widget creation (REQ-052 POST) and widget update (PUT).

### Persistence and Retrieval

- GridPosition is stored as part of the widget configuration
- `GET /api/v1/dashboards/{id}` returns all widgets including their GridPositions
- The frontend reads GridPositions and renders the layout; no layout calculation server-side

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `col + width` MUST NOT exceed 13; on violation → HTTP 422 | onCreate, onUpdate |
| BR-02 | `col`, `row`, `width`, `height` are positive integers; 0 and negative values → 422 | onCreate, onUpdate |
| BR-03 | `height` is limited to a maximum of 6 (corresponds to approx. 900px at a unit size of 150px) | onCreate, onUpdate |
| BR-04 | Widget overlap is not checked server-side; the client is responsible for an overlap-free layout | – |

## Acceptance Criteria

**AC1** (Valid GridPosition):
- When: Create widget with gridPosition={col:4, row:1, width:9, height:3}
- Then: HTTP 201; `col + width = 13` → valid (limit exactly exhausted)

**AC2** (Widget too wide):
- When: Create widget with gridPosition={col:4, row:1, width:10, height:2}
- Then: HTTP 422 "col + width = 14 exceeds the maximum grid width of 12"

**AC3** (Invalid values):
- When: Create widget with gridPosition={col:0, row:1, width:3, height:1}
- Then: HTTP 422 "col must be ≥ 1"

**AC4** (Height limit):
- When: Create widget with height=7
- Then: HTTP 422 "height may be at most 6"

**AC5** (GridPosition in dashboard response):
- Given: Dashboard with 3 widgets with different GridPositions
- When: GET /api/v1/dashboards/{id}
- Then: each widget contains its `gridPosition` object; the client renders the layout from it

**AC6** (Overlap not blocked server-side):
- When: Two widgets are created with overlapping GridPositions
- Then: both widgets are accepted (HTTP 201); overlap is a client problem

## Dependencies

- Part of: REQ-052 (create/configure widget) – GridPosition is a mandatory field of every widget
- Rendering: Client app (Electron, ADR-009) manages the drag-and-drop editor; the web portal renders the grid read-only

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; 12-column grid, width validation, overlap handled client-side |
