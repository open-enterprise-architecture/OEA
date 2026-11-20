# Design

## Context

- **The canvas so far:**
  - `diagram_shapes` (V47) holds the shapes.
  - `app.diagramview.DiagramViews` reads the view, saves atomically, creates entities through `EntityBatch.createInTransaction` and updates them through `updateInTransaction`.
  - The details panel reads an entity shown in the diagram.
- **Connections:** connections are entities with `is_connection`, a source and a target. The batch checks their endpoints against the connection metatype (codes `SOURCE_*`, `TARGET_*`). `getAllowedConnections` answers which connection metatypes join two metatypes.
- **The viewpoint:** it lists its allowed connection types (`connectionTypeIds`) and their effective length (change 9).

## Goals / Non-Goals

**Goals:**

- Edges stored with the layout.
- New connections created atomically.
- Existing connections offered on request.
- The connected new element.
- Edges in the details panel.

**Non-Goals:**

- Routing modes.
- Connections of connections.
- Deletion from the repository.
- Viewpoint conflicts.

## Architecture Constraints

- **ADR-051:** SQL in `core.diagram` and `core.entity` only. `app.diagramview` uses `app.entity` (no cycle).
- **ADR-032:** `entity:read` for the view and the connection search; `entity:write` for the save. A new connection of a type used as `scopingConnectionType` needs `visibility-scope:assign`, as in the batch; otherwise the whole save is rejected with 403.
  - As in `batchWriteEntities`, the controller calls `PropertyVisibility.requireScopeAssignment` with the metatypes of `newConnections` before the service runs, so the 403 comes before the diagram lock, `If-Match` and every 422.
  - The contract gets the condition on `saveDiagramView` (`"conditions": [{"when": "a new connection of a type used as scopingConnectionType is written", "permissions": ["visibility-scope:assign"]}]`), and the handler `@ConditionsEnforcedByHandler` with `probeRole` `integration-writer`, a `probePath` naming a probe diagram that `HandlerEnforcedConditionsTests` inserts (as the probe dashboard), and a `probeBody` with one new connection of the scoping probe metatype.
- **ADR-033:** new connections are audited as entity creations, and `diagram.layout-saved` carries the edge counts.

## Decisions

### D1 Table `diagram_edges`

```sql
CREATE TABLE diagram_edges (
    id              UUID    PRIMARY KEY,                -- client-generated
    diagram_id      UUID    NOT NULL REFERENCES diagrams (id) ON DELETE CASCADE,
    connection_id   INTEGER NOT NULL REFERENCES entities (id),
    source_shape_id UUID    NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE,
    target_shape_id UUID    NOT NULL REFERENCES diagram_shapes (id) ON DELETE CASCADE,
    points          JSONB   NOT NULL DEFAULT '[]'       -- [[x, y], ...] in pt, at most 50
                    CHECK (jsonb_typeof(points) = 'array')
);
CREATE INDEX diagram_edges_diagram ON diagram_edges (diagram_id);
CREATE INDEX diagram_edges_connection ON diagram_edges (connection_id);
```

- The migration is `V48__diagram_edges.sql` (the next free number after V47), with `REVOKE ALL ... FROM PUBLIC` and `SELECT, INSERT, UPDATE, DELETE` for `oea_app` as for `diagram_shapes`.
- The JSON shape of the bend points and their limits are checked by the service; coordinates are stored as numbers with at most two decimals.
- **Hidden edges:** an edge is hidden while its connection is deleted or one of its shapes is hidden (the entity of the shape is deleted). Hidden edges are not in the view, so the client cannot send them; the save keeps them, as it keeps hidden shapes.
- **Replace order** (the FKs to `diagram_shapes` require the shapes before the edges), in `DiagramEdgeStore` of `core.diagram`:
  1. delete the stored edges of the diagram that are not in the request, except the hidden ones whose two shapes keep their entity;
  2. replace the shapes (change 11); removing a shape cascades to its edges, the hidden ones included;
  3. upsert the edges of the request by ID (`ON CONFLICT (id) DO UPDATE ... WHERE diagram_edges.diagram_id = EXCLUDED.diagram_id`, as for shapes).
- **Indexes for the connection search:** `entity_connections_source` and `entity_connections_target` (V19) exist; no new index is needed on `entity_connections`.

### D2 Contract

- **`DiagramView.edges`:** `DiagramEdge` items with `id`, `connection` (`id`, `name`, `metatypeId`), `sourceShapeId`, `targetShapeId`, `points` (`[{x, y}]`) and `missingMandatory` (as on shapes).
- **`DiagramViewWrite`:**
  - `edges` (`maxItems` 4000): `id`, either `connectionId` or `newConnectionKey`, `sourceShapeId`, `targetShapeId` and `points` (`maxItems` 50, coordinates as for shapes);
  - `newConnections` (`maxItems` 500): `key` (uuid), `metatypeId`, optional `name`, `description` and `properties`.

  The endpoints of a new connection are taken from the entities of the shapes of its first edge. A new connection without an edge is rejected with 422.
- **New connections created incomplete:** the owner decision of 2026-11-20 (an entity placed new on the canvas is created without the values of mandatory properties) applies to new connections as well, since connections are entities; the edge reports `missingMandatory` and the details panel completes it. Every other write stays strict. (Assumption, open for review.)
- **`findDiagramConnections`:** `POST /views/diagrams/{id}/connections/query`, `entity:read`.
  - The body is `{entityIds: integer[], shownEntityIds: integer[]}`: `entityIds` are the entities just placed (1–2000 items), `shownEntityIds` the other entities on the canvas (0–2000 items).
  - The answer is `{items: [{connection, sourceEntityId, targetEntityId}], truncated}` for the connections that are not deleted, have one end in `entityIds` and the other in `entityIds` or `shownEntityIds`, and have a metatype the viewpoint allows; ordered by connection ID, at most 5000 items, `truncated` when there are more. Restricting one end to the placed entities keeps the answer to what the offer needs, so the cap does not hide the connections of the placed entity behind unrelated ones.
  - A `POST` is used because the lists are too long for a query string; it is a read: not audited, no `If-Match`, the normal rate limit. The CSRF origin header of `CsrfOriginFilter` applies as to every `POST`, the generated client sends it. The body falls under the default request limit of 1 MB (the 2 MB pattern of the save matches only `/views/diagrams/{id}`).
  - 400 for an empty `entityIds` or more than 2000 items in either list; 404 for an unknown or deleted diagram.
- **Conventions:** `maxLength` on every request string (`name` 255, `description` 2000, the string values of `properties` as in `entityUpdates`); the answer is not paged, so `PageMeta` does not apply; `UuidIdParam` for `id`. `check_openapi_conventions.py` needs no allowlist entry.
- **Request size:** the save limit `oea.web.request-size.diagram-view` rises from 2 MB to 4 MB. An edge without bend points takes about 150 bytes, a bend point about 30: 2000 shapes (about 0.3 MB) and 4000 edges with 20 bend points on average (about 3 MB) fit. The worst case of every limit at once (4000 edges with 50 bend points each, about 6.6 MB) answers 413; the list limits are tested with small items.

### D3 Save order and checks

Within the transaction of the save:

0. Before the service: the contract checks (400, including more than two decimals of a bend point) and `visibility-scope:assign` for scoping connection types (403, see the constraints).
1. Lock the diagram row and check `If-Match`.
2. Validate the shapes (change 11) and the edges:
   - unique edge IDs, not used by another diagram (`DUPLICATE_EDGE_ID`, `EDGE_ID_IN_USE`);
   - both shapes in the saved layout;
   - exactly one of `connectionId` and `newConnectionKey`.
   - **An existing connection** must be a connection entity that is not deleted, its source shape must show its source entity and its target shape its target entity, and an edge that is new or shows another connection than stored must have a connection metatype the viewpoint allows (a stored edge that keeps its connection is accepted, as for shapes).
   - **A new connection** must have a metatype the viewpoint allows, a unique key, and must be referenced by at least one edge; all edges of one new connection must join shapes of the same source entity and the same target entity (new entities by key).
   - Each `entityUpdates` item names an entity that a shape **or an edge** of the request shows, once.
3. Write the entity updates (change 12) and create the new entities (`createInTransaction`, unchanged).
4. Create the new connections through a new `EntityBatch.createConnectionsInTransaction(principal, List<ConnectionInput>, agent)`. `createInTransaction` takes only `EntityInput` without endpoints, so it cannot create connections; the new method runs the connection steps of an atomic batch create (`prepareConnection` and `writeConnection`: endpoints by ID, `SOURCE_NOT_FOUND`, `SOURCE_NOT_ALLOWED`, `TARGET_NOT_ALLOWED`, the name "Source → Target" when blank, `entity_connections`, history, `entity.created`) with `EntityValidator.Necessity.WARN`, requires a surrounding transaction, collects all issues before writing, and throws `AtomicBatchFailedException` with paths `connections[i]...`.
   - Source and target are the entity IDs of the shapes of the first edge; the new entities exist at this point, so their IDs are known (two calls, entities first, keep the ordering simple).
   - `DiagramViews` maps `connections[i]` to `newConnections[i]`, the endpoint fields (`sourceId`, `targetId`) to `newConnections[i]` with the batch code, and `connectionMetatypeId` to `newConnections[i].metatypeId`.
5. Replace the edges and shapes in the order of D1, raise the version, and audit `diagram.layout-saved` with `edgesAdded`, `edgesChanged`, `edgesRemoved` and `connectionsCreated` in addition to the existing keys (`added`, `changed` and `removed` stay the shape counts). Hidden edges are not counted.

The order of the answers extends the one of change 12: 400, 403 of a scoping connection type, 412 or 428, 422 of the layout and the edges, 403 of a restricted property, 422 of a deleted entity, 409 `entity-version-conflict`, 409 `entity-blocked`, 422 of the changes, 403 and 422 of the new entities, then 403 and 422 of the new connections.

Edges of a deleted connection, or of a shape of a deleted entity, are kept and left out of the view, like shapes (change 11).

### D4 Details panel for edges

- `getDiagramEntityDetails` accepts a connection shown by an edge that is not hidden, besides an entity shown by a shape; 404 otherwise. `DiagramViews.details` looks up the shown edges as it looks up the shown shapes. This changes the requirement "Read the details of a shown entity" of `diagram/diagram-entity-details` (MODIFIED delta).
- `entityUpdates` may name a connection shown by an edge of the request (name, description and values; the endpoints cannot change). This changes "Change entities with the diagram" (MODIFIED delta).
- The panel opens on a selected edge; for a new connection it edits the name, description and values of `newConnections` as for new entities. This changes "Details panel" (MODIFIED delta).

### D5 Frontend

- **Edges:** Vue Flow edges with a custom edge component: a straight line through the bend points, an arrow at the target, and the connection name as a label. The visual hint of the connection metatype's mapping gives the color.
- **Drawing:** from a handle to another shape. The editor computes the allowed connection metatypes from `getAllowedConnections` (both directions considered, as the metamodel defines) intersected with the viewpoint's connection types. A type allowed only `target-to-source` is created with the dragged-to shape as source, so the edge always runs from the shape of the connection's source.
  - With none, it shows the message "No connection is allowed here".
  - With one, that type is used.
  - With several, it shows a choice.
- **Offer:** after a shape of an existing entity is placed (a new entity has no connections yet), the editor calls `findDiagramConnections` with the placed entity as `entityIds` and the other saved entities on the canvas as `shownEntityIds`, and filters out connections that already have an edge between those shapes. With a result, it shows a non-modal notice "N connections available" with "Show all", "Choose…" and "Dismiss". Nothing is added without a choice.
- **"Add connected element":** an action on a shape. It offers a choice of connection metatype and target metatype, with the allowed ones only: the viewpoint's connection types for which the source metatype may be the source, and the viewpoint's element metatypes they allow as target (`getAllowedConnections` per pair, cached for the session of the editor), and places the new shape at the effective length (D2 of change 9) to the right of the source, with the gap measured between the facing borders and the shape vertically centered. The new entity then gets its inline name input.
- **Bend points:** double-click on an edge adds one; dragging moves it; double-click on a bend point removes it.
- **Keyboard:**
  - an edge is selectable by `Tab`;
  - `Delete` removes it from the diagram;
  - the context menu has "Remove from diagram", "Details" and, on shapes, "Add connected element".
- **Removing a shape** removes its edges locally; a new connection without an edge left is dropped, and the edits of a connection no edge shows any more are discarded (as for entities without a shape). The editor blocks the 4001st edge, the 51st bend point of an edge and the 501st new connection with a notice.

### D6 Documentation

- **The manual "Diagrams" (en/de):** drawing connections, the offer, "Add connected element", bend points, and removing edges.
- **SCREENS.md (SCR-021), the business object Diagram (DiagramEdge with shapes as endpoints, hidden edges) and `docs/data-model.puml`.**
- **The audit catalog:** the new keys of `diagram.layout-saved`.
- **US-074 and UC-10:** AC6, AC7 (generic part), AC8 (connections) and step 7.
- **The sprint plan.**

## Data Model

The new `DiagramEdge` (D1, V48), with source and target shapes and the hidden state added to the business object Diagram and to `docs/data-model.puml`.

## API

D2 and D4: `DiagramEdge`, `edges` and `missingMandatory` in `DiagramView`; `edges` and `newConnections` in `DiagramViewWrite`; the condition and the extended order of answers on `saveDiagramView`; `findDiagramConnections`; the descriptions of `getDiagramEntityDetails` and `entityUpdates` for connections shown by edges.

## Risks / Trade-offs

- **[Risk]** `findDiagramConnections` over 2000 entities may return many connections.
  - **Mitigation:** one end restricted to the placed entities, a cap of 5000 with `truncated`, and the existing indexes on `entity_connections` source and target (V19).
- **[Risk]** A save near every limit at once exceeds 4 MB and answers 413.
  - **Mitigation:** the limits are generous for real diagrams; the editor shows the 413 as for the shapes (change 11).
- **[Trade-off]** New connections are created in a second call after the new entities instead of one combined call; the batch logic is reused, not duplicated.
- **[Trade-off]** Straight edges with manual bend points only; routing modes come with US-122.
