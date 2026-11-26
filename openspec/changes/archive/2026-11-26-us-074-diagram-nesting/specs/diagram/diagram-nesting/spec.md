## ADDED Requirements

### Requirement: Nested shapes

A shape MAY name a container (`parentShapeId`): another shape of the same diagram earlier in the drawing order, at most 10 levels deep, without a cycle. A nested shape SHALL lie completely within its container. Positions stay absolute in pt. Nesting SHALL NOT change the repository by itself. Removing a container SHALL remove its nested shapes from the diagram; the entities stay.

Trace: US-074, owner decisions 2026-11-20

#### Scenario: Nested shape saved

- **WHEN** Anna saves the shape of "Billing" nested in the shape of "SAP ERP", completely within it
- **THEN** the view returns "Billing" with `parentShapeId` of the "SAP ERP" shape, and the parent of "Billing" in the repository is unchanged

#### Scenario: Container invalid

- **WHEN** an API call saves a shape nested in an unknown shape, in a shape later in the drawing order, in itself, or in a cycle
- **THEN** the response is 422 naming the shape, and nothing changes

#### Scenario: Containment boundary

- **WHEN** an API call saves a nested shape whose edges lie exactly on the edges of its container, then one that extends 0.01 pt beyond an edge
- **THEN** the first save is accepted, and the second is rejected with 422 naming the shape, and nothing changes

#### Scenario: Nesting depth

- **WHEN** an API call saves a chain of 10 shapes, each nested in the one before, then a chain of 11
- **THEN** the first save is accepted, and the second is rejected with 422 naming the eleventh shape, and nothing changes

#### Scenario: Container removed

- **GIVEN** the shape of "SAP ERP" contains the shapes of "Billing" and "Invoicing"
- **WHEN** Anna removes the shape of "SAP ERP" from the diagram, confirms the removal of 2 nested shapes and saves
- **THEN** the three shapes are gone, and the three entities stay in the repository

#### Scenario: Container of a deleted entity

- **GIVEN** the entity of a container is deleted
- **WHEN** Anna opens the diagram
- **THEN** the container is hidden, and the nested shapes of active entities show at their positions with `parentShapeId` null

### Requirement: Children of a placed entity

Placing an existing entity with direct children SHALL ask whether its direct children are shown. On "Show children", each child whose type the viewpoint allows and that the diagram does not show yet SHALL be nested in a grid at the default sizes, and the container SHALL grow to hold them. `listDiagramEntityChildren` SHALL return the direct children that are not deleted, at most 500.

Trace: US-074, US-135, owner decisions 2026-11-20

#### Scenario: Children shown

- **GIVEN** "SAP ERP" has the children "Billing" and "Invoicing", and the viewpoint allows their type
- **WHEN** Anna adds "SAP ERP" to the diagram and answers "Show children"
- **THEN** "Billing" and "Invoicing" are nested in "SAP ERP", and "SAP ERP" is large enough to hold both

#### Scenario: Children not shown

- **WHEN** Anna adds "SAP ERP" and answers "Only this entity"
- **THEN** only "SAP ERP" is placed

#### Scenario: No question without children

- **WHEN** Anna adds "CRM", which has no children
- **THEN** no question is asked

#### Scenario: Only direct children

- **GIVEN** "Billing" has the child "Dunning"
- **WHEN** Anna adds "SAP ERP" with "Show children"
- **THEN** "Dunning" is not placed, and "Billing" offers "Show children" in its context menu

#### Scenario: Type not allowed

- **GIVEN** the child "ERP Database" has a type the viewpoint does not allow
- **WHEN** Anna adds "SAP ERP" with "Show children"
- **THEN** "ERP Database" is not placed, and a notice names it

#### Scenario: Show children later

- **WHEN** Anna chooses "Show children" on a placed shape of "SAP ERP"
- **THEN** its direct children that the diagram does not show yet are nested in it

#### Scenario: Children list limits

- **WHEN** an entity has 500 children, then 501
- **THEN** the first list has 500 items with `truncated` false, the second 500 with `truncated` true, and the editor says that only the first 500 children were considered

#### Scenario: Children of an unknown entity

- **WHEN** an API call lists the children of an unknown, deleted or connection entity, or in an unknown or deleted diagram
- **THEN** the response is 404

#### Scenario: Children without entity:read

- **WHEN** a principal without `entity:read` lists the children of "SAP ERP"
- **THEN** the response is 403

### Requirement: Hierarchy line to a child placed elsewhere

A child already on the diagram SHALL NOT be nested a second time. The editor SHALL show the composition connection of the pair as an edge where it is not deleted and the viewpoint allows its type, else a hierarchy line from the container to the child's shape. A hierarchy line SHALL be saved only while the child's parent is the container's entity, and shown only while that holds.

Trace: US-074, owner decision 2026-11-26

#### Scenario: Hierarchy line

- **GIVEN** "Billing" is already on the diagram, outside "SAP ERP", and no composition connection of the pair is allowed in the viewpoint
- **WHEN** Anna adds "SAP ERP" with "Show children"
- **THEN** a hierarchy line runs from "SAP ERP" to the existing "Billing" shape, and "Billing" is not placed twice

#### Scenario: Composition connection instead

- **GIVEN** the automatic composition connection from "SAP ERP" to "Billing" exists and the viewpoint allows its type
- **WHEN** Anna adds "SAP ERP" with "Show children" while "Billing" is already placed
- **THEN** that connection is shown as an edge, and no hierarchy line is drawn

#### Scenario: Line without hierarchy

- **WHEN** an API call saves a hierarchy line whose child shape's entity is not a child of the container's entity
- **THEN** the response is 422 naming the line, and nothing changes

#### Scenario: Line valid through a parent change

- **WHEN** an API call saves a hierarchy line from "SAP ERP" to "CRM" together with a parent change of "CRM" to "SAP ERP"
- **THEN** the save is accepted, and the view shows the line

#### Scenario: Hierarchy changed later

- **GIVEN** a hierarchy line from "SAP ERP" to "Billing" is saved
- **WHEN** "Billing" is moved under "CRM" in the browser and Anna opens the diagram
- **THEN** the line is not shown

### Requirement: New parent on moving a shape

When the effective setting asks, moving a shape into a container whose entity is not its parent, or out of the container of its parent, SHALL ask "New parent?". "Yes" SHALL save the new parent (or none) with the diagram, versioned with the rules and audit of `setEntityParent`. "No", or a disabled question, SHALL change only the picture.

Trace: US-135, owner decisions 2026-11-26

#### Scenario: New parent confirmed

- **GIVEN** the setting asks
- **WHEN** Anna drags "CRM" into "SAP ERP", answers "Yes" and saves
- **THEN** "CRM" is nested in "SAP ERP", its parent is "SAP ERP" in a new version, and `entity.parent-changed` is audited

#### Scenario: New parent declined

- **GIVEN** the setting asks
- **WHEN** Anna drags "CRM" into "SAP ERP", answers "No" and saves
- **THEN** "CRM" is nested in the picture only, and its parent is unchanged

#### Scenario: Moved out

- **GIVEN** the setting asks and "Billing" is a child of "SAP ERP" nested in it
- **WHEN** Anna drags "Billing" out to the canvas, answers "Yes" and saves
- **THEN** "Billing" has no parent anymore

#### Scenario: Question disabled

- **GIVEN** the effective setting does not ask
- **WHEN** Anna drags "CRM" into "SAP ERP" and saves
- **THEN** no question is asked, and the parent of "CRM" is unchanged

#### Scenario: No question for the current parent

- **WHEN** Anna moves "Billing" within "SAP ERP", its parent
- **THEN** no question is asked

#### Scenario: Parent change rejected

- **WHEN** a save carries a parent change that would create a cycle, name a stale entity version, or hit an entity with an open issue
- **THEN** the response is 422, 409 `entity-version-conflict` or 409 `entity-blocked` as for entity updates, and nothing changes

#### Scenario: Hierarchy depth of a parent change

- **WHEN** a save carries a parent change that puts a leaf entity on level 10, then one that puts it on level 11
- **THEN** the first save is accepted, and the second is rejected with 422 naming the parent change, and nothing changes

#### Scenario: Without entity:write

- **WHEN** Franz, holding only `entity:read`, opens the diagram
- **THEN** the canvas is read-only, and he cannot move shapes or save

### Requirement: New element in a container

A new element dropped from the palette into a container SHALL be nested in it and created as a child of the container's entity, also when the container's entity is new in the same save, independent of the setting.

Trace: owner decision 2026-11-26

#### Scenario: New child

- **WHEN** Anna drops a new "Application Component" "Payments" into "SAP ERP" and saves
- **THEN** "Payments" is created with the parent "SAP ERP" and nested in it

#### Scenario: New child of a new container

- **WHEN** Anna creates "Shop" and drops a new "Cart" into it, then saves once
- **THEN** both are created, "Cart" has the parent "Shop", and `entity.parent-changed` is audited for "Cart"

### Requirement: Setting "Ask for a new parent"

`askNewParent` SHALL be stored globally (default true) and per viewpoint as true, false or null (global applies). The diagram view SHALL carry the effective value. Changing the global value SHALL need `viewpoint:manage` and be audited; reading it SHALL need `entity:read` or `viewpoint:manage`.

Trace: owner decisions 2026-11-26

#### Scenario: Global value applies

- **GIVEN** the global value is false and "Cloud Security View" stores null
- **WHEN** Anna opens a diagram of "Cloud Security View"
- **THEN** the view carries `askNewParent` false

#### Scenario: Viewpoint overrides

- **GIVEN** the global value is false and "Cloud Security View" stores true
- **WHEN** Anna opens a diagram of "Cloud Security View"
- **THEN** the view carries `askNewParent` true

#### Scenario: Change the global value

- **WHEN** Kurt, holding `viewpoint:manage`, sets the global value to false with the version read
- **THEN** it is stored with a new version, and `diagram.settings-updated` is audited with the old and new value

#### Scenario: Same value

- **WHEN** Kurt sets the global value to the value it has
- **THEN** the version stays, and nothing is audited

#### Scenario: Global value rejected

- **WHEN** Anna without `viewpoint:manage` changes the global value, or Kurt sends a stale version or none
- **THEN** the response is 403, 412 or 428, and nothing changes

#### Scenario: Read the global value

- **WHEN** Franz, holding only `entity:read`, reads the global value, and a principal with neither `entity:read` nor `viewpoint:manage` reads it
- **THEN** Franz gets it with its version, and the other gets 403

### Requirement: Saving nesting, lines and parents

`saveDiagramView` SHALL save `parentShapeId`, `hierarchyLines` (at most 4000) and `parentChanges` (at most 500, each entity once) in its one transaction; the parent changes SHALL be written before the lines are checked. A shape SHALL be 1–10 000 pt wide and high. The audit `diagram.layout-saved` SHALL also carry `parentsChanged`.

Trace: US-074, US-135

#### Scenario: Limits of lines and parent changes

- **WHEN** an API call saves 4000 hierarchy lines and 500 parent changes, then 4001 lines or 501 parent changes
- **THEN** the first save is accepted, and each of the others is rejected with 400, and nothing changes

#### Scenario: Shape size boundaries

- **WHEN** an API call saves a container of 10 000 × 10 000 pt, then one 10 000.01 pt wide
- **THEN** the first save is accepted, and the second is rejected with 400, and nothing changes

#### Scenario: Parent change of an entity not shown

- **WHEN** an API call carries a parent change of an entity that no shape of the request shows
- **THEN** the response is 422 naming the change, and nothing changes

#### Scenario: Entity twice in parent changes

- **WHEN** an API call names the same entity twice in `parentChanges`
- **THEN** the response is 422 naming the second change, and nothing changes

#### Scenario: Atomic save

- **WHEN** a save carries a valid parent change and an invalid shape
- **THEN** the response is 422, and the parent is unchanged
