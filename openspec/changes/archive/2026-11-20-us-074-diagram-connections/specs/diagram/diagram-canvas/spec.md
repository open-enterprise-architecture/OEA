## MODIFIED Requirements

### Requirement: Audit of the layout

A saved layout SHALL be audited as `diagram.layout-saved` with the diagram ID, the version, the numbers of added, changed and removed shapes and edges and of created entities and connections; each new entity and connection SHALL be audited as `entity.created`. A rejected save SHALL NOT be audited.

Trace: ADR-033, business object Diagram, US-074 AC8

#### Scenario: Save audited

- **WHEN** Anna adds two shapes and removes one and saves
- **THEN** `diagram.layout-saved` is audited with 2 added and 1 removed

#### Scenario: Edges audited

- **WHEN** Anna draws one new connection, removes one edge and saves
- **THEN** `diagram.layout-saved` is audited with 1 edge added, 1 edge removed and 1 connection created, and the connection is audited as `entity.created`
