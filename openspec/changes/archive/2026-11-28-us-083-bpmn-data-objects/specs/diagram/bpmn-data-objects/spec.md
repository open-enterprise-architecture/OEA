## Purpose

Defines how data objects and data stores are drawn in a BPMN diagram, how data associations connect them to tasks, which direction they have and how an output association records what a task does with the data.

## ADDED Requirements

### Requirement: Data object and data store symbols

A viewpoint of notation `bpmn2` MAY map any element metatype to `bpmn:DataObject` or `bpmn:DataStore`. The canvas SHALL draw a data object as a document, with a collection marker when the view marks its "Collection" value as true, and a data store as a cylinder.

Trace: US-083 AC1, REQ-081 AC1, AC4, US-074 AC2, owner decision 2026-11-28

#### Scenario: TOGAF data object as BPMN data object

- **GIVEN** an organization viewpoint of notation `bpmn2` maps the TOGAF "Data Object" to `bpmn:DataObject`
- **WHEN** Anna places the existing "Order" with "Add existing"
- **THEN** "Order" is drawn as a document, and no new entity is created

#### Scenario: Default type with collection

- **WHEN** Anna places a new "BPMN Data Object" from the palette group "Data", sets "Collection" and saves
- **THEN** it is drawn as a document with the collection marker, and after reloading the view it still is

#### Scenario: Collection not readable

- **GIVEN** "Collection" is restricted to a role Anna does not have
- **WHEN** Anna opens the diagram
- **THEN** the data object is drawn without the collection marker

#### Scenario: Data store

- **WHEN** Anna places a "BPMN Data Store"
- **THEN** it is drawn as a cylinder

### Requirement: Data associations and their direction

A new data input association SHALL run from a shape drawn as data object or data store, a new data output association to such a shape, and a new "Realized By" in a `bpmn2` diagram from a data store; the editor SHALL offer only the fitting type. Another direction SHALL be rejected with 422 and change nothing. Data associations SHALL be drawn dashed with an open arrow.

Trace: US-083 AC3, AC5, AC8, BR-07, REQ-081 AC2, AC5, owner decision 2026-11-28

#### Scenario: Input association

- **WHEN** Anna drags from "Order" to the task "Review order" and saves
- **THEN** a "Data Input Association" from "Order" to "Review order" exists, drawn dashed with an arrow at the task

#### Scenario: Output association

- **WHEN** Anna drags from "Review order" to "Order"
- **THEN** the editor offers "Data Output Association" and not "Data Input Association", and after the save the output association exists

#### Scenario: Wrong direction

- **WHEN** an API call saves a new "Data Input Association" from a task shape to the shape of "Order"
- **THEN** it is rejected with 422 `DATA_ASSOCIATION_DIRECTION` naming the new connection, and nothing changes

#### Scenario: Source not drawn as data

- **WHEN** an API call saves a new "Data Input Association" from a gateway shape to a task
- **THEN** it is rejected with 422 `DATA_ASSOCIATION_DIRECTION` naming the new connection, and nothing changes

#### Scenario: No data association between flow nodes

- **WHEN** Anna drags from a gateway to a task
- **THEN** no data association and no "Realized By" is offered

#### Scenario: Realized by

- **GIVEN** an organization viewpoint of notation `bpmn2` also allows the TOGAF "System Software"
- **WHEN** Anna drags from the data store "Order DB" to "PostgreSQL", chooses "Realized By" and saves
- **THEN** the connection "Order DB → PostgreSQL" exists

#### Scenario: Realized by from a data object

- **WHEN** an API call saves a new "Realized By" from a data object shape in a `bpmn2` diagram
- **THEN** it is rejected with 422 `DATA_ASSOCIATION_DIRECTION`, and nothing changes

#### Scenario: Existing connection shown

- **GIVEN** a batch wrote a "Data Input Association" from a task to "Order"
- **WHEN** Anna shows it with "Show existing connections" and saves
- **THEN** the save is accepted

### Requirement: Transformation of an output association

The details panel of a data output association SHALL show "Transformation type" (create, enrich, overwrite, delete, read-only), "Affected attributes" as tags (at most 50, each 1–100 characters, stored one per line, at most 5049 characters) and "Condition" (at most 2000 characters), saved with the diagram like other values.

Trace: US-083 AC4, REQ-081 AC2, AC3, owner decision 2026-11-28 (BR-08 retired)

#### Scenario: Enrich

- **WHEN** Anna sets "enrich" and the attributes "status" and "reviewedBy" on the output association and saves
- **THEN** the connection has these values, readable through the API, with the attributes as "status" and "reviewedBy" on two lines

#### Scenario: Attribute limits

- **WHEN** Anna enters 50 tags of 100 characters, then a 51st tag or a tag of 101 characters
- **THEN** the 50 tags are kept, and the editor refuses the 51st and the long tag

#### Scenario: Server limits

- **WHEN** an API call saves "Affected attributes" of 5049 characters and "Condition" of 2000, then of 5050 or of 2001
- **THEN** the first save is accepted, and each of the others is rejected with 422 naming the value, and nothing changes

#### Scenario: Read and create

- **GIVEN** "Review order" reads "Order"
- **WHEN** Anna sets "create" on the output association from "Review order" to "Order" and saves
- **THEN** the save is accepted without a warning

### Requirement: Data objects do not nest

A shape drawn as data object or data store placed in a pool, lane or sub-process SHALL lie in it for the layout only: its entity parent stays, no "New parent?" is asked, a new one is created without a parent, and "Show children" does not place such a child. A save that sets a parent for it SHALL be rejected with 422.

Trace: owner decision 2026-11-28, REQ-081 AC1

#### Scenario: Data object dropped into a lane

- **WHEN** Anna moves "Order" into the lane "Purchasing" and saves
- **THEN** the shape lies in "Purchasing", the parent of "Order" is unchanged, and no "New parent?" is asked

#### Scenario: New data object in a lane

- **WHEN** Anna drops a new "BPMN Data Object" into the lane and saves
- **THEN** it is created without a parent, and a task dropped into the same lane gets the lane as parent

#### Scenario: Parent through the API

- **WHEN** an API call saves a parent change, or a new entity with a parent, for a shape drawn as data object
- **THEN** it is rejected with 422 `DATA_OBJECT_PARENT`, and nothing changes

#### Scenario: Show children

- **GIVEN** the lane "Purchasing" has the children "Check order", a task, and "Order", drawn as data object
- **WHEN** Anna chooses "Show children" on "Purchasing"
- **THEN** "Check order" is nested in it, and "Order" is not placed and is named in the notice

### Requirement: Realized by on a data store

A holder of `entity:write` SHALL set "Realized by" on a shape drawn as data store from its context menu, searching any active element by name; the viewpoint need not allow its type. The save SHALL create a "Realized By" connection without an edge, the data store SHALL show each target as a badge, and "×" SHALL remove it with `entity:delete`.

Trace: US-083 AC5, REQ-081 AC5, owner decision 2026-11-28

#### Scenario: Realized by from the context menu

- **WHEN** Anna right-clicks the data store "Order DB", chooses "Realized by…", searches "Postg", selects the System Software "PostgreSQL" and saves
- **THEN** a "Realized By" connection from "Order DB" to "PostgreSQL" exists, audited as created, the diagram has no shape for "PostgreSQL", and "Order DB" shows the badge "PostgreSQL"

#### Scenario: Remove the realizing element

- **WHEN** Anna removes the badge "PostgreSQL" and saves
- **THEN** the connection is deleted softly, restorable, and the badge is gone

#### Scenario: Without entity:delete

- **WHEN** a holder of `entity:write` without `entity:delete` opens the badge, or an API call by them removes the connection
- **THEN** "×" is not offered, and the API call is rejected with 403, and nothing changes

#### Scenario: Not a data store

- **WHEN** an API call saves a "Realized By" without an edge from a task shape or a data object shape
- **THEN** it is rejected with 422 `DATA_ASSOCIATION_DIRECTION` naming the new connection, and nothing changes

#### Scenario: Not offered elsewhere

- **WHEN** Anna opens the context menu of a data object or a task
- **THEN** "Realized by…" is not offered

### Requirement: Data associations in the catalog

Data associations SHALL be ordinary connections, so that a catalog of data objects can join the tasks of their output associations.

Trace: US-083 AC7, REQ-081 AC7

#### Scenario: Tasks that enrich an object

- **GIVEN** "Review order" enriches "Order" and "Invoice" has no output association
- **WHEN** a catalog of "BPMN Data Object" has an inbound join over "Data Output Association" to "Task" with its name
- **THEN** "Order" shows "Review order" and "Invoice" an empty cell
