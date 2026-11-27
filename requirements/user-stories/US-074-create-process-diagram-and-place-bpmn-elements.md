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

OpenSpec change `us-074-diagram-connections` (S17) – connections on the canvas (UC-10 step 7, SCR-021): the view carries the edges of the diagram (`DiagramEdge`: the connection, the source and target shape, bend points in pt; V48, `diagram_edges`); a holder of `entity:write` draws a connection with a handle from one shape to another, chooses among the connection types that the viewpoint and the metamodel allow between the two types (`getAllowedConnections`), and the connection is created with the save, atomically with the layout (`newConnections` of `saveDiagramView`, `EntityBatch.createConnectionsInTransaction`, endpoint codes of the batch, audit `entity.created` and `diagram.layout-saved` with the edge counts; a missing mandatory value does not block it; a scoping connection type needs `visibility-scope:assign`). Existing connections show only on request: placing a shape of an existing entity offers "N connections available" (`findDiagramConnections`) with "Show all", "Choose…" and "Dismiss". "Add connected element" places a new element at the default length of the connection type; a connection may have several edges, bend points are added, moved and removed, and "Remove from diagram" on an edge keeps the connection. The details panel opens on an edge and reads and changes the connection (`getDiagramEntityDetails`, `entityUpdates`). AC6 is covered for generic connections (the SequenceFlow of BPMN follows with `us-074-bpmn-process-diagrams`), the generic part of AC7 (a connection of a type the viewpoint does not allow between the two types is not drawn: "No connection is allowed here"), and AC8 for connections (the edges are restored after a reload). Routing modes (US-122) are not part of it.

OpenSpec change `us-074-diagram-nesting` (S17) – nested shapes (owner decisions 2026-11-20 and 2026-11-26, SCR-021 and SCR-031): a shape may lie in a container shape of the same diagram (`parentShapeId`; the container grows with its children, at most 10 levels, positions stay absolute; shapes up to 10 000 pt), placing an entity with children asks whether its direct children are shown and nests them in a grid (`listDiagramEntityChildren`), a child shown elsewhere gets a hierarchy line or its composition edge, moving a shape into or out of a container asks "New parent?" depending on the setting `askNewParent` (global, overridable per viewpoint and in packages; `getDiagramSettings`, `updateDiagramSettings`) and saves the parent change with the diagram (`parentChanges`, `EntityHierarchy.setParentInTransaction`, audit `entity.parent-changed`), and a new element dropped into a container is created as a child of the container's entity. The technique serves the pools and lanes of BPMN, which follow with `us-074-bpmn-process-diagrams` ("lanes can be nested" of AC2 stays open until then).

OpenSpec change `us-074-diagram-viewpoint-change` (S17) – changing the viewpoint of a diagram (owner decisions 2026-11-19 and 2026-11-26, SCR-021 and SCR-031): a holder of `entity:write` changes the viewpoint with "Change viewpoint" (`changeDiagramViewpoint`, `PUT /diagrams/{id}/viewpoint`, `If-Match`; saved at once, audited as `diagram.viewpoint-changed`; the dialog names the shapes and edges that become conflicts before the change); shapes and edges whose type the viewpoint does not allow are conflicts – computed when the view is read, however they arose (a viewpoint change or edit, a package re-import) – marked with a text, listed as "Conflicts (N)" with "Remove from diagram" per conflict and "Remove all conflicts", and `saveDiagramView` answers 409 `diagram-conflicts` until none is left; the warning on SCR-031 counts exactly the diagrams with such shapes or edges (`countViewpointAffectedDiagrams`).

OpenSpec change `s17-entity-delete` (S17) – "Delete from repository" on the canvas (REQ-146, SCR-021): a holder of `entity:delete` deletes the entity of a shape or edge from the context menu or with Ctrl+Delete after a confirmation that names the children, connections and active solutions (`previewEntityDeletion`); `deleteEntity` soft-deletes the entity, its children on all levels and their connections with the version of the preview, at once and outside the explicit save, and the canvas removes their shapes and edges while the other unsaved changes stay. The stored shapes stay hidden and show again after `restoreDeletedEntity` (`entity:restore`).

OpenSpec change `us-074-bpmn-process-diagrams` (S17) – BPMN process diagrams (owner decisions 2026-11-27, SCR-021 and SCR-030): the starter package `oea-starter-bpmn` (26 element types, the connection types "Sequence Flow", "Message Flow", "Association", "Calls" and "References Process" limited to BPMN endpoints, the properties "Association direction" and "Group category", en and de labels, the viewpoint "Process Model (BPMN 2.0)"; importable alone or next to the TOGAF package, from the start page or the new section "Starter packages" of SCR-030), 19 further element keys in the catalog, BPMN symbols and lines on the canvas of a `bpmn2` viewpoint, the palette grouped by symbol, pools, lanes and sub-processes as the only containers of such a canvas (also for "Show children"), a new element dropped into a lane becomes a child of the lane's entity (the entity hierarchy replaces the connections `bpmn-contained-in`, `bpmn-lane-in-pool` and `bpmn-lane-in-lane`), and the warnings "Sequence flow crosses a pool boundary" and "Message flow within one pool" computed in the editor (the save is accepted). AC1 and AC4 to AC9 are done; AC2 and AC3 stay open until the data objects (change 20) follow.

OpenSpec change `us-074-bpmn-pools-and-lanes` (S17) – lanes and collapsed sub-processes (owner decisions 2026-11-27, SCR-021): the lanes of a pool, nested ones included, stack automatically (they fill the width of their container right of a label band of 30 pt and lie one below the other; a lane is at least 30 pt high and as high as its content; a lane is resized at its lower edge and the pool follows; a lane dropped on another lane becomes its sibling, dropped on a label band it is nested; reordering within a pool asks no "New parent?"); pools can be switched to vertical per diagram (`orientation`, the label band at the top and the lanes side by side); a selected lane offers icons to insert a lane before or after it; sub-processes can be collapsed per diagram (`collapsed`, drawn at 90 × 60 pt with "+", the nested shapes and the edges and lines touching them hidden; REQ-076 AC8); both flags are stored with the shapes (V52) and saved with the layout. "Lanes can be nested" and the remaining parts of AC3 are done; AC2 and AC3 stay open for the data objects (change 20).

## Acceptance Criteria

- [x] Viewpoint "Process Model (BPMN 2.0)" (`bpmn-process-view`) of the package `oea-starter-bpmn` is available for selection when creating a diagram
- [ ] Canvas palette shows all built-in BPMN elements grouped: pools/lanes, tasks (incl. call activity), events (incl. message/timer start, message/terminate end), gateways, data, artifacts (text annotation, group)
- [ ] Every element of the BPMN 2.0.2 Descriptive conformance sub-class can be placed and saved (REQ-076 AC7); lanes can be nested (stacked automatically, change `us-074-bpmn-pools-and-lanes`)
- [x] A pool can be dragged onto the canvas; a pool is a container; lanes can be inserted into the pool (lanes stack automatically, horizontally or, in a vertical pool, side by side, and a selected lane offers icons to insert a lane next to it; change `us-074-bpmn-pools-and-lanes`)
- [x] Elements (tasks, events, gateways) can be dropped from the palette into a lane by drag; the element becomes a child of the lane's entity and its shape is nested in the lane
- [x] A SequenceFlow can be connected between two BPMN elements by handle drag
- [x] Connecting a SequenceFlow to a non-BPMN element is not offered, and the API rejects it with 422 (BR-01); a sequence flow across pools is marked with a warning and saved
- [x] The diagram saves all entities and connections atomically; after reload the layout is restored correctly
- [x] Company-specific subtypes (e.g. `approval-task`) also appear in the palette (AC2 REQ-076)

## Technical Notes

- Vue Flow `parentNode` for pool/lane containment; the entity hierarchy (parent of the entity) as repository representation
- Palette grouping: "Pools and lanes", "Activities", "Events", "Gateways", "Artifacts", "Other"
- Lane resize: a lane is resized at its far edge along the lanes (bottom, or right in a vertical pool); the pool adjusts automatically (change `us-074-bpmn-pools-and-lanes`); sub-processes can be collapsed per diagram (REQ-076 AC8)

## Out of Scope

- BPMN 2.0 XML export
- Automatic layout (ELK.js for BPMN — v2.0)
