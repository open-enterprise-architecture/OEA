# diagram/diagram-connections Specification

## Purpose

Defines the connections of a diagram: its edges, drawing new connections, showing existing connections on request, adding a connected element at the default length, removing edges, and the limits of edges.

## Requirements

### Requirement: Edges of a diagram

The view SHALL return the edges of a diagram: edge ID, connection (ID, name, metatype), source and target shape, bend points in pt and missing mandatory properties. An edge SHALL show a connection whose metatype the viewpoint allows, from a shape of its source to a shape of its target entity; a connection MAY have several edges. Edges of a deleted connection or of a hidden shape SHALL be left out and kept.

Trace: business object Diagram (DiagramEdge), US-074 AC6, owner decisions 2026-11-19

#### Scenario: Saved edge

- **GIVEN** "Order to Cash" shows "SAP ERP" and "CRM Cloud" with an edge of the connection "SAP ERP → CRM Cloud"
- **WHEN** Franz reads the view
- **THEN** it has the edge with the connection, the two shapes and its bend points

#### Scenario: Connection twice

- **GIVEN** "SAP ERP" has two shapes in "Order to Cash"
- **WHEN** Anna draws the connection "SAP ERP → CRM Cloud" from each shape of "SAP ERP" and saves
- **THEN** the view has two edges of the same connection

#### Scenario: Wrong shapes

- **WHEN** an API call saves an edge whose source shape does not show the connection's source entity, or whose shape is not in the layout
- **THEN** the response is 422 naming the edge, and nothing changes

#### Scenario: Type not allowed

- **WHEN** an API call saves a new edge of a connection whose metatype the viewpoint does not allow
- **THEN** the response is 422 naming the edge, and nothing changes

#### Scenario: Deleted connection

- **GIVEN** "Order to Cash" has an edge of the connection "SAP ERP → CRM Cloud"
- **WHEN** the connection is deleted, Anna saves the diagram, and the connection is restored
- **THEN** the edge is left out of the view while the connection is deleted and shows again after the restore

#### Scenario: Edge of another diagram

- **WHEN** an API call saves an edge whose ID belongs to another diagram, or two edges with the same ID
- **THEN** the response is 422 naming the edge, and nothing changes

### Requirement: Draw a new connection

A holder of `entity:write` SHALL draw a connection from one shape to another; the editor SHALL offer the connection metatypes the viewpoint and the metamodel allow between the two metatypes. The connection SHALL be created with the save, checked like every connection write, atomic with the layout; a missing mandatory value SHALL NOT block it. A scoping connection type SHALL need `visibility-scope:assign`.

Trace: US-074 AC6, AC7, UC-10 step 7, BR-01, owner decisions 2026-11-19

#### Scenario: Connection drawn

- **WHEN** Anna drags from "SAP ERP" to "CRM Cloud", chooses "Flow" and saves
- **THEN** the connection "SAP ERP → CRM Cloud" of type "Flow" exists, audited as created, and the view has its edge

#### Scenario: No allowed type

- **WHEN** Anna drags between two shapes whose metatypes no allowed connection type joins
- **THEN** the editor says that no connection is allowed here, and no edge is drawn

#### Scenario: Endpoint not allowed

- **WHEN** an API call saves a new connection whose metatype does not allow the source or target metatype
- **THEN** the response is 422 naming the new connection with the code of the endpoint, and nothing changes

#### Scenario: Between new entities

- **WHEN** Anna places two new entities, connects them and saves
- **THEN** both entities and the connection are created in the same save

#### Scenario: New connection without an edge

- **WHEN** an API call saves a new connection that neither an edge references nor names its own endpoints, or whose edges join shapes of different entities
- **THEN** the response is 422 naming the new connection, and nothing changes

#### Scenario: Scoping connection type

- **GIVEN** "Owned by" is the scoping connection type of a property
- **WHEN** an API call by a holder of `entity:write` without `visibility-scope:assign` saves a new "Owned by" connection
- **THEN** the response is 403, and nothing changes

#### Scenario: Created incomplete

- **GIVEN** "Flow" has a mandatory property without default value
- **WHEN** Anna draws a "Flow" connection and saves
- **THEN** the connection is created, and its edge names the missing property

### Requirement: Show existing connections on request

When a shape is placed whose entity has connections to entities already shown, the editor SHALL offer them ("N connections available"); only on request SHALL their edges be added, all or selected ones. Only connections that are not deleted and whose metatype the viewpoint allows SHALL be offered.

Trace: owner decisions 2026-11-19

#### Scenario: Offered when the second end is placed

- **GIVEN** "Order to Cash" shows "SAP ERP", and the connection "SAP ERP → CRM Cloud" exists
- **WHEN** Anna places "CRM Cloud"
- **THEN** the editor shows "1 connection available" and adds no edge by itself

#### Scenario: Added on request

- **WHEN** Anna chooses "Show" on "1 connection available"
- **THEN** the edge of "SAP ERP → CRM Cloud" is added between the two shapes, unsaved

#### Scenario: Nothing to offer

- **WHEN** Anna places an entity without connections to the shown entities
- **THEN** no offer appears

#### Scenario: Limits of the request

- **WHEN** an API call asks for the connections of 1 and of 2000 placed entities with 2000 shown entities, then of 0 or 2001 placed entities or with 2001 shown entities
- **THEN** the first two are answered, and the others are rejected with 400

#### Scenario: Too many connections

- **WHEN** more than 5000 connections match
- **THEN** the answer holds 5000 of them and says that it is truncated

### Requirement: Add a connected element

A holder of `entity:write` SHALL add from a shape a new connected element: a connection metatype and a target metatype allowed by the viewpoint and the metamodel, placed at the default length of the connection metatype from the source shape (90 pt without a stored length), with the new entity and connection saved with the diagram.

Trace: owner decisions 2026-11-19 (default length), UC-10 step 7

#### Scenario: Placed at the default length

- **GIVEN** the viewpoint stores 150 pt for "Flow"
- **WHEN** Anna chooses "Add connected element" on "SAP ERP" with "Flow" and "Application Component"
- **THEN** a new shape is placed 150 pt to the right of "SAP ERP", joined by a new "Flow" edge, both unsaved

#### Scenario: Without a stored length

- **WHEN** Anna adds a connected element with a connection metatype without a stored length
- **THEN** the new shape is placed 90 pt from the source shape

### Requirement: Remove an edge and limits of edges

"Remove from diagram" on an edge SHALL remove only the edge; removing a shape SHALL remove its edges. A save SHALL carry at most 4000 edges with at most 50 bend points each, within ±100 000 pt with at most two decimals, and at most 500 new connections; more SHALL be rejected with 400.

Trace: business object Diagram BR-DG-02, owner decisions 2026-11-19

#### Scenario: Edge removed

- **WHEN** Anna removes the edge of "SAP ERP → CRM Cloud" and saves
- **THEN** the view has no such edge, and the connection stays in the repository

#### Scenario: Shape removed with its edges

- **WHEN** Anna removes the shape of "CRM Cloud" and saves
- **THEN** the view has no edge to "CRM Cloud", and the connections stay in the repository

#### Scenario: Limits

- **WHEN** an API call saves 4000 edges, 50 bend points on an edge, bend points at −100 000 and 100 000 and 500 new connections, then 4001 edges, 51 bend points, a bend point at 100 000.01 or −100 000.01, a coordinate with three decimals or 501 new connections
- **THEN** the first save is accepted, and each of the others is rejected with 400, and nothing changes

### Requirement: Assignment connections without an edge in the save

A save SHALL create new assignment connections that no edge shows, from a shape of the request to an entity or a new entity. It SHALL carry up to 500 `removedConnections` with versions: assignment connections from an entity shown in the request or the stored layout, shown by no edge, soft-deleted as by `deleteEntity`. Removing SHALL need `entity:delete` (403 before every 422).

Trace: US-075 AC4, AC7, US-076 AC2, AC5, REQ-077 AC5, AC8

#### Scenario: Connection without an edge

- **WHEN** a save creates a belongs-to connection from a lane shape to an organizational unit, naming `sourceShapeId` and `targetEntityId`
- **THEN** the connection exists, and the view has no edge for it

#### Scenario: Type outside the organization model

- **WHEN** a save creates a "Flow" connection without an edge
- **THEN** it is rejected with 422 naming the new connection, and nothing changes

#### Scenario: New entity without a shape

- **WHEN** a save creates the organizational unit "Logistics" named only by a new connection
- **THEN** both exist, and the diagram has no shape for "Logistics"

#### Scenario: New entity named by nothing

- **WHEN** a save creates an entity that neither a shape nor a new connection names
- **THEN** it is rejected with 422, and nothing changes

#### Scenario: Removed connections limit

- **WHEN** a save removes 500 assignment connections, then another removes 501
- **THEN** the first is accepted, the second is rejected with 400, and nothing changes

#### Scenario: Connection unrelated to the diagram

- **WHEN** a save removes an assignment connection whose source is on no shape of the request or the stored layout, or a connection that is no assignment
- **THEN** it is rejected with 422 naming `removedConnections[0]`, and nothing changes

#### Scenario: Removed connection still shown

- **WHEN** a save removes a connection that an edge of the same request shows
- **THEN** it is rejected with 422 naming the removed connection, and nothing changes
