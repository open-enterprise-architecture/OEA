# diagram/diagram-viewpoint-change Specification

## Purpose

Changing the viewpoint of an existing diagram, and the conflicts that arise when shown shapes or edges are no longer allowed by the diagram's viewpoint, whether after a viewpoint change, a viewpoint edit or a package re-import; a diagram with conflicts can only be saved once they are removed.

## Requirements

### Requirement: Change the viewpoint of a diagram

A principal with `entity:write` SHALL change the viewpoint of a diagram to another existing viewpoint with `changeDiagramViewpoint`; the change SHALL be saved at once with `If-Match` (428, 412), SHALL raise the version, SHALL keep all shapes, edges and hierarchy lines as they are, and SHALL be audited as `diagram.viewpoint-changed` with the old and new viewpoint and the number of conflicts.

Trace: US-074, UC-10, owner decisions 2026-11-19

#### Scenario: Viewpoint changed

- **WHEN** Anna changes "Order to Cash" from "Application Landscape" to "Integration View" with its current version
- **THEN** the response is 200 with the new viewpoint, the next version and the number of conflicts, every shape and edge is kept, and `diagram.viewpoint-changed` is audited

#### Scenario: Same viewpoint

- **WHEN** Anna changes "Order to Cash" to the viewpoint it already has
- **THEN** nothing changes, the version stays, and nothing is audited

#### Scenario: Viewpoint change rejected

- **WHEN** Anna sends an unknown viewpoint ID, a stale version or none, or Franz without `entity:write` changes the viewpoint, or the diagram is deleted
- **THEN** the response is 422 naming `viewpointId`, 412, 428, 403 or 404, and nothing changes

#### Scenario: Unsaved changes on the canvas

- **GIVEN** Anna has unsaved changes on the canvas
- **WHEN** she chooses "Change viewpoint"
- **THEN** she is asked to save or discard them first

### Requirement: Conflicts of a diagram

A shown shape or edge whose entity's metatype is not among the allowed types of the diagram's viewpoint (exact metatype, as in the save) SHALL be a conflict, however it arose (a viewpoint change or edit, a package re-import). The view SHALL mark each conflict with `conflict` and carry `conflictCount`. Shapes and edges of deleted entities SHALL NOT be conflicts while hidden.

Trace: US-074, owner decisions 2026-11-19 and 2026-11-26

#### Scenario: Conflict after a viewpoint change

- **GIVEN** "Order to Cash" shows "Customer DB" of "Data Object", which "Integration View" does not allow
- **WHEN** Anna changes the viewpoint to "Integration View" and the canvas reloads
- **THEN** the shape of "Customer DB" is marked as a conflict, and the view carries `conflictCount` 1

#### Scenario: Conflict after a viewpoint edit

- **WHEN** Kurt removes "Data Object" from "Application Landscape"
- **THEN** the shapes of "Data Object" in its diagrams are conflicts

#### Scenario: Conflict resolved by the viewpoint

- **GIVEN** the shape of "Customer DB" is a conflict
- **WHEN** Kurt allows "Data Object" in "Integration View"
- **THEN** the shape is no conflict anymore

#### Scenario: Edge conflict

- **GIVEN** "Order to Cash" shows an edge of "Data Flow"
- **WHEN** Anna changes its viewpoint to one that allows the shown element types but not "Data Flow"
- **THEN** the edge is marked as a conflict, and its shapes are not

#### Scenario: Subtype of an allowed type

- **GIVEN** "Integration View" allows "Application Component" but not its subtype "SaaS Application"
- **WHEN** Anna changes "Order to Cash", which shows a "SaaS Application", to "Integration View"
- **THEN** the shape of the "SaaS Application" is a conflict

### Requirement: Saving only without conflicts

`saveDiagramView` SHALL answer 409 `diagram-conflicts`, after the 422 of the layout and before the 403 of the visibility, naming every shape and edge of the saved layout that is a conflict, whatever else the save changes, and SHALL change nothing; a save without a conflict left SHALL be accepted. The editor SHALL list the conflicts with "Remove from diagram" for each and for all.

Trace: US-074, owner decisions 2026-11-19

#### Scenario: Save blocked

- **GIVEN** "Order to Cash" has 2 conflicts
- **WHEN** Anna moves another shape and saves, and then saves without any change
- **THEN** both responses are 409 `diagram-conflicts` naming the 2 shapes, and nothing changes

#### Scenario: Layout error first

- **GIVEN** "Order to Cash" has a conflict
- **WHEN** an API call saves it with a new shape of an unknown entity
- **THEN** the response is 422 naming the new shape, not 409

#### Scenario: Conflicts removed

- **GIVEN** "Order to Cash" has 2 conflicts
- **WHEN** Anna chooses "Remove all conflicts", moves another shape and saves
- **THEN** the save is accepted, and the diagram has no conflict

#### Scenario: One conflict left

- **GIVEN** "Order to Cash" has 2 conflicts
- **WHEN** Anna removes one of them and saves
- **THEN** the response is 409 `diagram-conflicts` naming the other one, and nothing changes

#### Scenario: Conflict of an entity update

- **GIVEN** "Order to Cash" has a conflict
- **WHEN** Anna changes the description of a shown entity in the details panel and saves
- **THEN** the response is 409 `diagram-conflicts`, and the entity is unchanged

#### Scenario: Container with a conflict

- **GIVEN** a container shape is a conflict and holds 2 nested shapes of allowed types
- **WHEN** Anna removes the container with "Remove from diagram" and saves
- **THEN** the container and its nested shapes are gone, and the save is accepted

#### Scenario: Read-only reader

- **WHEN** Franz, a reader, opens a diagram with conflicts
- **THEN** he sees the marks and the list of conflicts without actions

### Requirement: Viewpoint change in the editor

The diagram page SHALL offer holders of `entity:write` "Change viewpoint" with the viewpoints to choose from and, before the change, the number of shapes and edges that will become conflicts; after the change the canvas SHALL reload with the new viewpoint, its palette and its default sizes for new shapes.

Trace: US-074, UC-10

#### Scenario: Preview of conflicts

- **WHEN** Anna chooses "Change viewpoint" and selects "Integration View"
- **THEN** the dialog names the 1 shape that will become a conflict before she confirms

#### Scenario: No conflicts

- **WHEN** Anna selects a viewpoint that allows every shown type
- **THEN** the dialog says that no conflicts arise
