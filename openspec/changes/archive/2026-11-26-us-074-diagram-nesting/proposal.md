# Proposal

## Why

The hierarchy of element entities (US-135, change `us-135-component-hierarchy`) is not visible on diagrams: placing an entity with children shows only the entity. The owner decided on 2026-11-20 that placing an entity with children asks whether its direct children are shown too; if so, they are nested in it as in an ArchiMate composition, and the parent becomes a container that grows with them. The technique also serves the pools and lanes of BPMN (change 18) and comes before BPMN.

This is S17 change 16 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| User story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) (pools and lanes as containers follow with change 18) |
| Use case | UC-10, UC-13 (hierarchy, US-135) |
| ADRs | [ADR-007](../../../adrs/ADR-007-canvas-library.md) (canvas library), [ADR-021](../../../adrs/ADR-021-implicit-parent-child-connection.md) (hierarchy), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screens | SCR-021 "Diagram editor", SCR-031 "Viewpoints" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-20 and 2026-11-26:**

- Placing an entity with children asks whether its direct children are shown; if so, they are nested in it and the container grows with them. Only direct children.
- A child that is already placed elsewhere on the diagram is not nested a second time; a hierarchy line from the container to the existing shape shows the relation. Where the automatic composition connection of the pair exists and the viewpoint allows its type, that connection is shown as an edge instead.
- A new element dropped from the palette into a container is created as a child of the container's entity.
- Moving a shape into a container or out of it asks "New parent?" – depending on a setting that is configured globally and can be overridden per viewpoint. "Yes" changes the parent of the entity in the repository (versioned as with `setEntityParent`); "No", and a disabled question, change only the picture.

**Decisions and assumptions of this change (open for review):**

- **Shapes as containers:** a shape gets an optional `parentShapeId` (another shape of the same diagram, drawn before it, at most 10 levels). Positions stay absolute in pt; a nested shape lies completely within its container.
- **Larger shapes:** width and height of a shape go from 1–2000 pt to 1–10 000 pt so that containers can hold many children (the columns widen from `NUMERIC(6,2)` to `NUMERIC(7,2)`). The default sizes of a viewpoint stay 1–2000 pt.
- **Children of an entity:** a new read `listDiagramEntityChildren` gives the direct children that are not deleted, with whether the viewpoint allows their type and the composition connection of the pair; at most 500 (`truncated`). Shapes and search results carry `childCount` and `parentEntityId`.
- **Hierarchy lines:** stored per diagram (`hierarchyLines`, from a container shape to a shape of a child entity); shown only while the hierarchy holds.
- **Parent changes with the save:** `saveDiagramView` carries `parentChanges` (entity, expected version, new parent or none) and new entities carry a parent; they are written in the save's transaction through a transaction-bound variant of the parent write of `setEntityParent` (`EntityHierarchy`), with its rules, the automatic composition connection and its audit. A new entity with a parent ends at version 2 (created, then placed).
- **Setting:** the global value `askNewParent` (default: ask) through `getDiagramSettings` and `updateDiagramSettings` at `/admin/diagram-settings` (`entity:read` or `viewpoint:manage` to read, `viewpoint:manage` to write), and `askNewParent` per viewpoint (`null` = global). The diagram view carries the effective value. System-defined viewpoints get the value from their package and stay read-only; the starter package sets none and keeps its version.
- **Removing a container** removes its nested shapes from the diagram too (the entities stay); the confirmation names their number.

**Not in this change:**

- grandchildren or the whole subtree on request (only direct children);
- automatic layout beyond the initial grid of the children;
- BPMN pools and lanes (change 18).

## Capabilities

### New Capabilities

- `diagram/diagram-nesting`: containers, the children of a placed entity, hierarchy lines, "New parent?" and its setting.

### Modified Capabilities

- `diagram/diagram-canvas`: the size limit of a shape.
- `metamodel/viewpoints`: the setting `askNewParent` per viewpoint.

## Impact

- **Contract:** `parentShapeId`, `childCount` and `parentEntityId` on shapes, `hierarchyLines`, `parentChanges`, the parent of new entities, `askNewParent` on the view and the viewpoint, `listDiagramEntityChildren`, `getDiagramSettings` and `updateDiagramSettings`.
- **Database:** a migration for `diagram_shapes.parent_shape_id`, the wider size checks, `diagram_hierarchy_lines`, `viewpoints.ask_new_parent` and the global setting.
- **Backend:** `core` stores, the diagram services, the parent write of `setEntityParent` in the save's transaction, the viewpoint package section.
- **Frontend:** nested nodes on the canvas, the children question, the "New parent?" dialog, hierarchy lines, "Show children", the setting on SCR-031.
- **Documentation:** the manual "Diagrams" and "Viewpoints" (en/de), SCREENS.md, the business object diagram, `docs/data-model.puml`, the audit catalog and the sprint plan.
