## MODIFIED Requirements

### Requirement: Shapes of a diagram

A shape SHALL show an element entity whose metatype the viewpoint allows; one entity MAY have several shapes in a diagram. Position SHALL be within ±100 000 pt, width and height 1–10 000 pt, with at most two decimals. A save SHALL carry at most 2000 shapes and 500 new entities. Removing a shape SHALL leave its entity in the repository.

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

- **WHEN** an API call saves widths and heights of 1 and 10 000, positions of −100 000 and 100 000, 2000 shapes and 500 new entities, then a width of 0.99 or 10 000.01, a position of 100 000.01 or −100 000.01, 2001 shapes or 501 new entities
- **THEN** the first save is accepted, and each of the others is rejected with 400, and nothing changes
