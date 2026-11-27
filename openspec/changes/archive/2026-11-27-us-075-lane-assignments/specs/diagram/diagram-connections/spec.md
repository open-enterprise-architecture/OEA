## MODIFIED Requirements

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

## ADDED Requirements

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
