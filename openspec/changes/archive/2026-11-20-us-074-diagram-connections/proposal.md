# Proposal

## Why

The canvas (changes 11 and 12) places and edits entities, but shows no connections. UC-10 needs flows between process steps (US-074 AC6). The owner decided on 2026-11-19:

- existing connections show only on request, offered when the second end of a connection is placed;
- a connection may appear several times;
- not every connection between two shapes needs to show;
- a connected new element is placed at the default length of the connection metatype.

This is S17 change 13 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md) step 7 |
| User Story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) AC6, AC7 (generic part), AC8 (connections) |
| Business Object | [Diagram](../../../business-objects/diagram.md) (DiagramEdge) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-19 used here:**

- Existing connections are shown only on request; the offer appears when the second end is placed.
- Connections and entities may appear several times; not all connections between two shapes need to be shown.
- The default length of a connection metatype is the distance of a connected new element.
- "Save" saves explicitly; `entity:write` edits.

**Decisions and assumptions of this change (open for review):**

- **Edges:** a table of edges per diagram (edge ID, connection, source shape, target shape, bend points in pt), replaced on each save like the shapes. The direction follows the connection: the source shape shows its source entity.
- **Save:** `saveDiagramView` gets `edges` (at most 4000) and `newConnections` (at most 500: key, metatype, source and target given as shape IDs).
  - New connections are created in the save's transaction, after the new entities, through a new `EntityBatch.createConnectionsInTransaction` with the connection checks of the batch (endpoint codes, name from the endpoints); like new entities they are created incomplete when a mandatory value is missing (owner decision 2026-11-20 applied to connections).
  - Their values are entered in the details panel, which now also opens for edges.
  - A scoping connection type needs `visibility-scope:assign` (403 for the whole save, as in the batch).
  - The request limit of the save rises from 2 MB to 4 MB.
- **Viewpoint:** a new edge needs a connection metatype the viewpoint allows. Stored edges stay even if the viewpoint changes later; conflicts follow in change 15.
- **Offer:** a new read `findDiagramConnections` (`POST /views/diagrams/{id}/connections/query`, `entity:read`) returns the connections that are not deleted and are of allowed types between the placed entities (at most 2000) and themselves or the other shown entities (at most 2000). A `POST` is used because the lists are too long for a query string.
- **Connected new element:**
  - The connection metatype is chosen among those the viewpoint allows from the source metatype, and the target metatype among those it allows as target (`getAllowedConnections`, intersected with the viewpoint).
  - The new shape is placed to the right of the source with a gap equal to the effective length of the connection metatype, vertically centered.
- **Drawing:** straight lines with optional bend points (move a bend point by drag, add one by double-click on the edge). Routing modes (US-122) are not part of this change.
- **Deleted connections:** their edges are kept and hidden, as for shapes.
- **Removing:** removing a shape removes its edges. "Remove from diagram" on an edge keeps the connection. "Delete from repository" follows in change 14.
- **Audit:** `diagram.layout-saved` also counts edges; new connections are audited as entity creations.

**Not in this change:**

- connections between connections;
- routing modes;
- "Delete from repository";
- notation-specific arrows;
- the viewpoint change.

## Capabilities

### New Capabilities

- `diagram/diagram-connections`: edges of a diagram, drawing new connections, showing existing connections on request, adding a connected element, removing edges and the limits.

### Modified Capabilities

- `diagram/diagram-entity-details`: the details read, the entity changes and the details panel also cover a connection shown by an edge (the read so far requires a shape).
- `diagram/diagram-canvas`: the audit of the layout also counts edges and created connections.

## Impact

- **Contract:** `DiagramEdge`, `edges` and `newConnections` in `DiagramView`/`DiagramViewWrite`, and `findDiagramConnections`.
- **Backend:**
  - the migration `V48__diagram_edges.sql`;
  - `core.diagram` (`DiagramEdgeStore`, the connection search) and `app.diagramview` (validation, connection creation through the new `EntityBatch.createConnectionsInTransaction`);
  - the scoping check in the controller of the save, with `@ConditionsEnforcedByHandler` and a probe diagram;
  - the save request limit (4 MB);
  - the details read for connections shown as edges.
- **Frontend:**
  - edges in Vue Flow (handles, the type choice, bend points);
  - the offer "N connections available";
  - "Add connected element";
  - the details panel for edges;
  - stories and fakes.
- **Documentation:** the manual "Diagrams" (en/de), SCREENS.md (SCR-021), the business object Diagram, the data model, the audit catalog, US-074, UC-10 and the sprint plan.
