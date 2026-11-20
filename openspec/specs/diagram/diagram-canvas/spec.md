# diagram/diagram-canvas Specification

## Purpose

Defines the canvas of a diagram: reading its view, searching entities to add, saving its layout with new entities atomically, its shapes and their limits, incomplete entities, the audit of the layout and the canvas editor (SCR-021).

## Requirements

### Requirement: Read the diagram view

A principal with `entity:read` SHALL read the view of a diagram: the diagram with its version, its viewpoint with the effective mappings and default sizes, and its shapes in drawing order with shape ID, entity (ID, name, metatype), position and size in pt. Shapes of a deleted entity SHALL be left out and SHALL show again when the entity is restored. A deleted or unknown diagram SHALL answer 404.

Trace: US-074 AC8, business object Diagram (DiagramLayout, BR-DG-02), owner decisions 2026-11-19

#### Scenario: Saved layout

- **GIVEN** "Order to Cash" shows "SAP ERP" at (100, 50) with 90 × 30 pt
- **WHEN** Franz, holding `entity:read`, reads its view
- **THEN** the view has a shape of "SAP ERP" at (100, 50) with 90 × 30 pt

#### Scenario: Deleted entity

- **GIVEN** "Order to Cash" shows "Legacy CRM", which is deleted
- **WHEN** Franz reads its view
- **THEN** the view has no shape of "Legacy CRM"

#### Scenario: Deleted diagram

- **GIVEN** the diagram "Old Process" is deleted
- **WHEN** Franz reads its view, or Anna saves its layout
- **THEN** the response is 404

#### Scenario: Without permission

- **WHEN** a principal without `entity:read` reads the view
- **THEN** the response is 403

### Requirement: Search entities for a diagram

A principal with `entity:read` SHALL search, page by page and ordered by name, the element entities that are not deleted and whose metatype the diagram's viewpoint allows, filtered by a name part of at most 255 characters (ignoring case, wildcards matched literally) and optionally by one allowed metatype. A metatype the viewpoint does not allow SHALL answer 422; a deleted or unknown diagram 404.

Trace: US-074 AC1, UC-10 step 2, owner decisions 2026-11-19

#### Scenario: Search by name

- **GIVEN** the entities "SAP ERP", "SAP BW" and "Salesforce" of allowed metatypes, and "SAP Process" of a metatype the viewpoint does not allow
- **WHEN** Anna searches the entities of "Order to Cash" for "sap"
- **THEN** the page has "SAP BW" and "SAP ERP" in this order, and not "SAP Process"

#### Scenario: Filter limit

- **WHEN** an API call searches with a name part of 255 characters, and then of 256 characters
- **THEN** the first is answered with 200 and the second with 400

### Requirement: Save the diagram layout

A principal with `entity:write` SHALL save the layout of a diagram with `If-Match` and its version in one request: all shapes in drawing order and the new entities created on the canvas. The save SHALL be atomic: the new entities are created with the checks and audit of an entity write, the shapes replace the stored ones, and the version rises; if anything is invalid, nothing SHALL change. 428 without `If-Match`, 412 when stale.

Trace: US-074 AC8, UC-10 step 9, owner decisions 2026-11-19

#### Scenario: Layout saved

- **WHEN** Anna moves "SAP ERP" to (300, 80), resizes it to 120 × 40 pt and saves with the current version
- **THEN** the response is 200 with the next version, and reading the view gives the new position and size

#### Scenario: New entity

- **WHEN** Anna places a new "Application Component" named "CRM Cloud" from the palette and saves
- **THEN** the entity "CRM Cloud" exists in the repository with version 1, audited as `entity.created` like a batch create, and the view has its shape

#### Scenario: Atomic

- **WHEN** Anna saves two new entities, one with a metatype the viewpoint does not allow
- **THEN** the response is 422 naming that new entity, no entity is created and the layout is unchanged

#### Scenario: Mandatory property

- **WHEN** Anna saves a new entity of a metatype with a mandatory property without default value
- **THEN** the entity is created without the value and marked as incomplete, and the save succeeds

#### Scenario: Stale version

- **GIVEN** Kurt saved the diagram after Anna opened it
- **WHEN** Anna saves with her version, and then without `If-Match`
- **THEN** the first request gives 412, the second 428, and nothing changes

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `entity:read`, saves a layout
- **THEN** the response is 403, and nothing changes

### Requirement: Shapes of a diagram

A shape SHALL show an element entity whose metatype the viewpoint allows; the same entity MAY have several shapes in one diagram. Position SHALL be within ±100 000 pt, width and height 1–2000 pt, with at most two decimals. A save SHALL carry at most 2000 shapes and at most 500 new entities. Removing a shape SHALL leave its entity in the repository.

Trace: business object Diagram BR-DG-01, BR-DG-02, owner decisions 2026-11-19

#### Scenario: Entity twice

- **WHEN** Anna places "SAP ERP" twice in "Order to Cash" and saves
- **THEN** the view has two shapes of "SAP ERP"

#### Scenario: Type not allowed

- **WHEN** an API call saves a new shape of an entity whose metatype the viewpoint does not allow
- **THEN** the response is 422 naming the shape, and nothing changes

#### Scenario: Unknown or deleted entity

- **WHEN** an API call saves a shape of an unknown or deleted entity
- **THEN** the response is 422 naming the shape, and nothing changes

#### Scenario: Shape of another diagram

- **WHEN** an API call saves a shape whose ID belongs to another diagram
- **THEN** the response is 422 naming the shape, and nothing changes

#### Scenario: Removed from the diagram

- **WHEN** Anna removes the shape of "SAP ERP" with "Remove from diagram" and saves
- **THEN** the view has no shape of "SAP ERP", and "SAP ERP" stays in the repository

#### Scenario: Limits

- **WHEN** an API call saves widths and heights of 1 and 2000, positions of −100 000 and 100 000, 2000 shapes and 500 new entities, then a width of 0.99 or 2000.01, a position of 100 000.01 or −100 000.01, 2001 shapes or 501 new entities
- **THEN** the first save is accepted, and each of the others is rejected with 400, and nothing changes

### Requirement: Incomplete entities

A new entity created on the canvas SHALL be created even when a mandatory property of its metatype has no value. An entity SHALL count as incomplete while a mandatory property of its current metatype has no value; the view SHALL mark its shapes as incomplete and name the missing properties. Other writes of the entity SHALL keep requiring the mandatory values.

Trace: repository/entity-write (necessity of properties), owner decision 2026-11-20

#### Scenario: Created incomplete

- **GIVEN** "Business Process" has the mandatory property "Process Owner" without a default value
- **WHEN** Anna places a new "Business Process" named "Invoice Approval" and saves
- **THEN** "Invoice Approval" exists without "Process Owner", and its shape is marked "Incomplete: Process Owner"

#### Scenario: Completed

- **GIVEN** "Invoice Approval" is incomplete
- **WHEN** a value of "Process Owner" is written for it
- **THEN** its shape is no longer marked as incomplete

#### Scenario: Other writes stay strict

- **GIVEN** "Invoice Approval" is incomplete
- **WHEN** a batch changes its description without a value of "Process Owner"
- **THEN** the batch item is rejected with `MANDATORY_MISSING`, as for every entity

### Requirement: Audit of the layout

A saved layout SHALL be audited as `diagram.layout-saved` with the diagram ID, the version, the numbers of added, changed and removed shapes and edges and of created entities and connections; each new entity and connection SHALL be audited as `entity.created`. A rejected save SHALL NOT be audited.

Trace: ADR-033, business object Diagram, US-074 AC8

#### Scenario: Save audited

- **WHEN** Anna adds two shapes and removes one and saves
- **THEN** `diagram.layout-saved` is audited with 2 added and 1 removed

#### Scenario: Edges audited

- **WHEN** Anna draws one new connection, removes one edge and saves
- **THEN** `diagram.layout-saved` is audited with 1 edge added, 1 edge removed and 1 connection created, and the connection is audited as `entity.created`

### Requirement: Canvas editor

The diagram page SHALL show the canvas (SCR-021) with pan and zoom. Holders of `entity:write` SHALL get a palette with the element metatypes of the viewpoint and "Add existing" with a search; new shapes SHALL get the viewpoint's default size. Shapes SHALL be moved, resized and removed; "Save" SHALL save all changes, and leaving with unsaved changes SHALL ask first. A reader SHALL see the canvas read-only.

Trace: US-074 AC1, AC2, AC8, UC-10 steps 1–2, 9, SCR-021, owner decisions 2026-11-19

#### Scenario: Place a new entity

- **GIVEN** the viewpoint stores 120 × 40 pt for "Application Component" and nothing for "Business Process"
- **WHEN** Anna drags both from the palette and names them
- **THEN** the shapes are 120 × 40 pt and 90 × 30 pt, marked as unsaved

#### Scenario: Add an existing entity

- **WHEN** Anna chooses "Add existing", searches "SAP" and picks "SAP ERP"
- **THEN** a shape of "SAP ERP" appears with the default size of its metatype

#### Scenario: Unsaved changes

- **WHEN** Anna moves a shape and leaves the page without saving
- **THEN** the editor asks whether to discard the changes

#### Scenario: Conflict on save

- **WHEN** Anna saves and the server answers 412
- **THEN** the editor says that the diagram was changed by someone else and offers to reload, discarding her changes

#### Scenario: Rejected new entity

- **WHEN** Anna saves and the server answers 422 naming a new entity, for example because its metatype was removed from the viewpoint meanwhile
- **THEN** the editor marks its shape with the reason and keeps all her changes

#### Scenario: Reader

- **WHEN** Franz, holding only `entity:read`, opens "Order to Cash"
- **THEN** he sees the shapes, can pan and zoom, and has no palette, no "Add existing", no "Save" and cannot move shapes

### Requirement: Delete from repository on the canvas

A shape or edge of a stored entity SHALL offer "Delete from repository", in its context menu and with Ctrl+Delete, to holders of `entity:delete`. It SHALL show the deletion preview – children, connections and active solutions – ask for confirmation, delete the entity at once, outside the explicit save, and remove the shapes and edges of every deleted entity from the canvas. The other unsaved changes of the diagram SHALL stay.

Trace: REQ-146, US-074, owner decisions 2026-11-19 and 2026-11-20

#### Scenario: Deleted from the canvas

- **WHEN** Kurt chooses "Delete from repository" on the shape of "ERP" and confirms the warning "3 children, 4 connections, 1 active solution"
- **THEN** "ERP" and its cascade are deleted, their shapes and edges disappear from the canvas, and a shape Kurt moved before stays moved and unsaved

#### Scenario: Without entity:delete

- **WHEN** Anna, without `entity:delete`, opens the menu of a shape
- **THEN** it has no "Delete from repository"

#### Scenario: Changed meanwhile

- **GIVEN** someone changed "ERP" after Kurt opened the confirmation
- **WHEN** Kurt confirms
- **THEN** the dialog says that "ERP" was changed, offers to read the preview again, and nothing is deleted

#### Scenario: Restored later

- **GIVEN** "ERP" was deleted from the canvas of "Order to Cash"
- **WHEN** "ERP" is restored and Anna opens "Order to Cash"
- **THEN** its shapes and edges show again
