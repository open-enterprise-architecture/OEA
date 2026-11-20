---
id: US-074
title: Create a Process Diagram and Place BPMN Elements
use_case: UC-10
requirement: REQ-076
priority: must
status: in-progress
story_points: 8
created: 2026-06-26
---

# US-074: Create a Process Diagram and Place BPMN Elements

**As** a Business Analyst (Anna, SH-08)  
**I want** to create a new diagram with the viewpoint "Process Model (BPMN 2.0)" and place pools, lanes, tasks, events, and gateways on the canvas,  
**so that** I can model business processes directly in the EA repository without needing a separate tool.

## Implementation

OpenSpec change `us-074-diagrams` (S17) – creating a diagram: a person or service account with `entity:write` creates a diagram with a name, a description and a viewpoint (`createDiagram`, V46), and finds, renames, deletes and restores it on the page "Diagrams" (SCR-067), in the browser of the sidebar and in the navigation tree. The BPMN elements of the criteria below follow with the change `us-074-bpmn-process-diagrams`.

OpenSpec change `us-074-diagram-canvas` (S17) – the canvas of a diagram (SCR-021): a person with `entity:read` reads the view of a diagram (`getDiagramView`: shapes in pt in drawing order, the default sizes of the viewpoint) and searches the entities the viewpoint allows (`searchDiagramEntities`); a holder of `entity:write` places new entities from the palette (name and metatype only) and existing ones with "Add existing", moves, resizes and removes shapes ("Remove from diagram" leaves the entity) and saves the whole layout atomically with "Save" (`saveDiagramView`, V47, audit `diagram.layout-saved`). The generic part of AC2 (every allowed element type can be placed; the grouping and the BPMN types follow with `us-074-bpmn-process-diagrams`) and AC8 for shapes (the layout is restored after a reload; connections follow with `us-074-diagram-connections`) are covered. A new entity whose type has a mandatory property without default value is created incomplete and marked as such on the canvas (owner decision 2026-11-20).

OpenSpec change `us-074-diagram-entity-details` (S17) – the details panel of the canvas (UC-10 step 6, SCR-021): selecting a shape opens the details of its entity (`getDiagramEntityDetails`: name, description, metatype, version, the values with the redaction of the other reads, per property whether the caller may read and change it, the missing mandatory values); holders of `entity:write` change name, description and values in the form of the metatype (inputs of the dialogs of SCR-061, references through `searchDiagramReferenceTargets`) and save them with the layout (`entityUpdates` of `saveDiagramView`, audit `entity.updated`, atomic with the layout); an incomplete entity is completed this way. An entity with another version than the one read rejects the save with 409 `entity-version-conflict` (issue #69), one with an open issue of a solution with 409 `entity-blocked`. The person assignment of user tasks (`bpmn-task-assigned-to`) follows with `us-075-lane-assignments`.

OpenSpec change `us-074-diagram-connections` (S17) – connections on the canvas (UC-10 step 7, SCR-021): the view carries the edges of the diagram (`DiagramEdge`: the connection, the source and target shape, bend points in pt; V48, `diagram_edges`); a holder of `entity:write` draws a connection with a handle from one shape to another, chooses among the connection types that the viewpoint and the metamodel allow between the two types (`getAllowedConnections`), and the connection is created with the save, atomically with the layout (`newConnections` of `saveDiagramView`, `EntityBatch.createConnectionsInTransaction`, endpoint codes of the batch, audit `entity.created` and `diagram.layout-saved` with the edge counts; a missing mandatory value does not block it; a scoping connection type needs `visibility-scope:assign`). Existing connections show only on request: placing a shape of an existing entity offers "N connections available" (`findDiagramConnections`) with "Show all", "Choose…" and "Dismiss". "Add connected element" places a new element at the default length of the connection type; a connection may have several edges, bend points are added, moved and removed, and "Remove from diagram" on an edge keeps the connection. The details panel opens on an edge and reads and changes the connection (`getDiagramEntityDetails`, `entityUpdates`). AC6 is covered for generic connections (the SequenceFlow of BPMN follows with `us-074-bpmn-process-diagrams`), the generic part of AC7 (a connection of a type the viewpoint does not allow between the two types is not drawn: "No connection is allowed here"), and AC8 for connections (the edges are restored after a reload). Routing modes (US-122) and "Delete from repository" (`s17-entity-delete`) are not part of it.

## Acceptance Criteria

- [ ] Viewpoint "bpmn-process-view" is available for selection when creating a diagram
- [ ] Canvas palette shows all built-in BPMN elements grouped: pools/lanes, tasks (incl. call activity), events (incl. message/timer start, message/terminate end), gateways, data, artifacts (text annotation, group)
- [ ] Every element of the BPMN 2.0.2 Descriptive conformance sub-class can be placed and saved (REQ-076 AC7); lanes can be nested
- [ ] A pool can be dragged onto the canvas; a pool is a container; lanes can be inserted into the pool
- [ ] Elements (tasks, events, gateways) can be dropped from the palette into a lane by drag; a `bpmn-contained-in` connection is created automatically
- [ ] A SequenceFlow can be connected between two BPMN elements by handle drag
- [ ] Connecting a SequenceFlow to a non-BPMN element is rejected with an error message (BR-01)
- [ ] The diagram saves all entities and connections atomically; after reload the layout is restored correctly
- [ ] Company-specific subtypes (e.g. `approval-task`) also appear in the palette (AC2 REQ-076)

## Technical Notes

- Vue Flow `parentId` for pool/lane containment; `bpmn-contained-in` connection as repository representation
- Palette grouping: "Containers" (pool, lane, subprocess), "Tasks", "Events", "Gateways"
- Lane resize: lane must be vertically resizable; the pool adjusts automatically

## Out of Scope

- BPMN 2.0 XML export
- Automatic layout (ELK.js for BPMN — v2.0)
