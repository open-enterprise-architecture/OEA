# Delta for catalog/catalog-joins

## ADDED Requirements

### Requirement: Hint without a possible join

When no connection metatype of the instance metamodel admits the primary metatype of a connection catalog as source or target, the dialog "Joins" SHALL explain that the catalog shows the connections with their source and target only. This SHALL be a hint, not an error.

Trace: REQ-065 BR-04, owner decision 2026-11-05

#### Scenario: Connection catalog without joins

- **GIVEN** a catalog for `Data Flow` and no connection metatype that starts or ends at `Data Flow`
- **WHEN** Lukas opens the dialog "Joins"
- **THEN** it shows the hint "This catalog lists connections. No connection type starts or ends at them, so the catalog shows each connection with its source and target."
