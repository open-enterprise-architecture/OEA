---
id: US-074
title: Create a Process Diagram and Place BPMN Elements
use_case: UC-10
requirement: REQ-076
priority: must
status: proposed
story_points: 8
created: 2026-06-26
---

# US-074: Create a Process Diagram and Place BPMN Elements

**As** a Business Analyst (Anna, SH-08)  
**I want** to create a new diagram with the viewpoint "Process Model (BPMN 2.0)" and place pools, lanes, tasks, events, and gateways on the canvas,  
**so that** I can model business processes directly in the EA repository without needing a separate tool.

## Acceptance Criteria

- [ ] Viewpoint "bpmn-process-view" is available for selection when creating a diagram
- [ ] Canvas palette shows all built-in BPMN elements grouped: pools/lanes, tasks, events, gateways
- [ ] A pool can be dragged onto the canvas; a pool is a container; lanes can be inserted into the pool
- [ ] Elements (tasks, events, gateways) can be dropped from the palette into a lane by drag; a `bpmn-contained-in` connection is created automatically
- [ ] A SequenceFlow can be connected between two BPMN elements by handle drag
- [ ] Connecting a SequenceFlow to a non-BPMN element is rejected with an error message (BR-01)
- [ ] The diagram saves all entities and connections atomically; after reload the layout is restored correctly
- [ ] Company-specific subtypes (e.g. `approval-task`) also appear in the palette (AC2 REQ-076)

## Technical Notes

- React Flow `parentId` for pool/lane containment; `bpmn-contained-in` connection as repository representation
- Palette grouping: "Containers" (pool, lane, subprocess), "Tasks", "Events", "Gateways"
- Lane resize: lane must be vertically resizable; the pool adjusts automatically

## Out of Scope

- BPMN 2.0 XML export
- Automatic layout (ELK.js for BPMN — v2.0)
