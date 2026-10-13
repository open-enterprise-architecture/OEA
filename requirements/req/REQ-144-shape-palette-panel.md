---
id: REQ-144
title: Collapsible Shape Palette Panel in Diagram Editor
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-05
    - UC-12
  business_objects:
    - diagram
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-01
    - SH-03
    - SH-04
    - SH-07
  concept: []
  adrs:
    - ADR-007
    - ADR-008
    - ADR-020
supersedes: []
superseded_by: []
---

# REQ-144: Collapsible Shape Palette Panel in Diagram Editor

## Statement

The system MUST offer a shape palette in the diagram editor, positioned as a standalone panel between the explorer and the diagram canvas. The panel MUST be completely showable and hideable via a toggle button (◀ / ▶). The palette MUST display the available metamodel entities as draggable shape templates, filtered by the currently active viewpoint (REQ-090 viewpoint definition). Entity types that are not allowed for the active viewpoint MUST be grayed out or hidden.

## Rationale

Architects model diagrams by dragging and dropping shapes from a palette. Filtering by viewpoint ensures that only semantically allowed elements can be inserted into a diagram — a core principle of ArchiMate viewpoints (cf. concept §8 viewpoint mechanism). The panel is collapsible to save space on small screens.

## Context

The shape palette is the primary input tool for manual diagram creation:

| Panel | Position | Content |
|---|---|---|
| Explorer | far left | navigation in the model tree |
| Shape palette (this REQ) | between explorer and canvas | draggable shape templates, filtered by viewpoint |
| Canvas | center | diagram drawing area |
| Properties | right | properties of the selected element |

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Description |
|---|---|---|
| activeViewpoint | Viewpoint \| null | current viewpoint; null = all types allowed |
| collapseState | boolean | true = panel collapsed (only toggle button visible) |

**Processing**:

1. When opening the diagram editor: palette shown by default
2. Active viewpoint → only allowed metamodel types are displayed
3. No viewpoint → display all configured metamodel types
4. Toggle button (◀/▶) switches between expanded and collapsed
5. Collapsed state: panel reduced to a 28px wide strip with a toggle icon
6. The state is persisted per user in the user settings

**Outputs**:

- The palette shows shape tiles per metamodel type (abbreviation badge + type name + mini preview)
- Drag indicator activates as soon as the user grabs a tile
- On drop onto the canvas: trigger the REQ-145 entity lookup dialog

## Acceptance Criteria

**AC1** (Palette shows only allowed types):
- Given: viewpoint "Application Layer View" allows AC, AS, AI, AF, AP
- When: the diagram editor is opened with this viewpoint
- Then: the palette shows only the 5 allowed types; technology types are grayed out/hidden

**AC2** (No viewpoint = all types):
- Given: diagram editor without an active viewpoint (free-form)
- Then: all configured metamodel types appear in the palette

**AC3** (Collapse/expand):
- When: the user clicks the toggle button (◀)
- Then: the panel collapses to a 28px wide strip; the canvas gains the freed space
- When: the user clicks again (▶)
- Then: the panel expands again to the default width

**AC4** (Persistence of the state):
- When: the user collapses the palette and closes the editor
- Then: the next time it is opened, the palette is still collapsed

## Dependencies

- Blocked by: UC-05 (diagram editor), REQ-040 (viewpoint mechanism)
- Related: REQ-145 (drag & drop + entity lookup), REQ-146 (delete behavior)
- Affects: ADR-007 (canvas library), ADR-020 (explorer navigation model)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft |
