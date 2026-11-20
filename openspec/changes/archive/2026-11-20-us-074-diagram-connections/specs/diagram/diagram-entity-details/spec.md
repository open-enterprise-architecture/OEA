## MODIFIED Requirements

### Requirement: Read the details of a shown entity

A principal with `entity:read` SHALL read for an entity shown in a diagram by a shape or an edge its name, description, metatype, version, values and missing mandatory properties; unreadable values SHALL be redacted, and each mapped property SHALL say whether the reader may read and change it. An entity without a shape or edge in the diagram, a deleted entity or a deleted diagram SHALL answer 404.

Trace: UC-10 step 6, US-074, REQ-160, owner decisions 2026-11-19 and 2026-11-20, diagram/diagram-connections

#### Scenario: Details of a shown entity

- **GIVEN** "Order to Cash" shows "SAP ERP" with the values "Status" = "active" and "Investment" = 5 000 000
- **WHEN** Franz, holding `entity:read`, reads the details of "SAP ERP" in "Order to Cash"
- **THEN** he gets name, description, metatype, version and both values, each marked as not changeable for him

#### Scenario: Restricted value

- **GIVEN** "Investment" is readable only for the role `finance`
- **WHEN** Franz, without that role, reads the details of "SAP ERP"
- **THEN** "Investment" is redacted

#### Scenario: Connection shown by an edge

- **GIVEN** "Order to Cash" has an edge of the connection "SAP ERP → CRM Cloud"
- **WHEN** Franz reads the details of "SAP ERP → CRM Cloud" in "Order to Cash"
- **THEN** he gets its name, description, metatype, version and values

#### Scenario: Entity not shown

- **WHEN** Franz reads the details of an entity that has no shape and of a connection that has no edge in "Order to Cash"
- **THEN** each response is 404

### Requirement: Change entities with the diagram

A save of the diagram SHALL carry changes of entities shown by a shape or an edge (name, description, property values; not the endpoints) with the entity version read, and values of new entities and connections. They SHALL be written like every entity write: metamodel and visibility checked, mandatory values required, an entity with an open issue rejected, versioned, audited, atomic with the layout.

Trace: UC-10 step 6, US-074, repository/entity-write, solution/solution-go-live, owner decisions 2026-11-19 and 2026-11-20

#### Scenario: Value completed

- **GIVEN** "Invoice Approval" is incomplete because "Process Owner" has no value
- **WHEN** Anna enters "Process Owner" in the details panel and saves the diagram
- **THEN** "Invoice Approval" has the value in a new version, audited as an entity change, and its shape is no longer marked as incomplete

#### Scenario: Mandatory value still missing

- **GIVEN** "Invoice Approval" is incomplete
- **WHEN** Anna changes only its description in the details panel and saves
- **THEN** the response is 422 naming the entity with `MANDATORY_MISSING`, and nothing changes, the layout included

#### Scenario: New entity with values

- **WHEN** Anna places a new "Business Process" "Invoice Approval", enters "Process Owner" in the details panel and saves
- **THEN** "Invoice Approval" is created with the value and is not incomplete

#### Scenario: Restricted property

- **GIVEN** "Investment" is writable only for the role `finance`
- **WHEN** an API call by Anna, without that role, changes "Investment" of "SAP ERP" with the diagram
- **THEN** the response is 403, and nothing changes

#### Scenario: Entity with an open issue

- **GIVEN** "SAP ERP" has an unresolved open issue of a solution
- **WHEN** Anna changes "SAP ERP" with the diagram
- **THEN** the response is 409 `entity-blocked` naming "SAP ERP" and its open issue, and nothing changes

#### Scenario: Connection changed

- **WHEN** Anna changes the description of the connection "SAP ERP → CRM Cloud" shown by an edge and saves
- **THEN** the connection has the description in a new version, audited as an entity change

#### Scenario: Entity not shown

- **WHEN** an API call changes an entity that has no shape and no edge in the saved layout
- **THEN** the response is 422 naming the change, and nothing changes

#### Scenario: Limits

- **WHEN** an API call carries 500 entity changes, then 501, a name of 255 and of 256 characters, a description of 2000 and of 2001 characters, and an expected version of 1 and of 0
- **THEN** 500, the name of 255, the description of 2000 and the expected version 1 are accepted, and the others are rejected with 400, and nothing changes

### Requirement: Details panel

Selecting a shape or an edge SHALL open the details panel of its entity on the canvas page, with name, description, metatype and the property values in the form of the entity's metatype. Holders of `entity:write` SHALL change the values they may write; the changes SHALL count as unsaved changes of the diagram, and missing mandatory values SHALL be marked. A reader SHALL see the panel read-only.

Trace: UC-10 step 6, US-074, SCR-021, owner decisions 2026-11-19 and 2026-11-20, diagram/diagram-connections

#### Scenario: Edit and save

- **WHEN** Anna selects "SAP ERP", changes "Status" to "retiring" and chooses "Save"
- **THEN** the diagram and the entity are saved in one step, and the panel shows the new version

#### Scenario: Edge selected

- **WHEN** Anna selects the edge of "SAP ERP → CRM Cloud"
- **THEN** the panel shows the connection with its metatype and values, editable for her

#### Scenario: Mandatory marked

- **WHEN** Anna selects the incomplete "Invoice Approval"
- **THEN** the panel marks "Process Owner" as required and missing

#### Scenario: Version conflict

- **WHEN** Anna saves and the server answers 409 for "SAP ERP"
- **THEN** the editor says that "SAP ERP" was changed by someone else, keeps her changes and offers to reload the entity, which discards only the changes of "SAP ERP"

#### Scenario: Reader

- **WHEN** Franz, holding only `entity:read`, selects "SAP ERP"
- **THEN** the panel shows its values without input fields
