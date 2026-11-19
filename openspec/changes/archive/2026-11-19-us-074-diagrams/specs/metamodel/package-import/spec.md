## ADDED Requirements

### Requirement: Diagrams of a viewpoint dropped at a re-import

When a re-import deletes a viewpoint of the package, the diagrams of that viewpoint, the deleted ones included, SHALL be removed for good in the same transaction, each audited as `diagram.deleted` with the cause `viewpoint-deleted`, while the entities stay. The import result and `metamodel.package-imported` SHALL name the number of deleted diagrams. A failed import SHALL delete no diagram.

Trace: US-092 AC3, US-093 AC1, ADR-033, owner decision 2026-11-19

#### Scenario: Viewpoint with diagrams dropped

- **GIVEN** a package installed "Technology Landscape", which 2 diagrams use
- **WHEN** a new version of the package without "Technology Landscape" is imported
- **THEN** the viewpoint and both diagrams are deleted, each diagram audited with the cause `viewpoint-deleted`, and the import result has `diagramsDeleted` 2

#### Scenario: Viewpoint kept

- **GIVEN** a package installed "Application Landscape", which 2 diagrams use
- **WHEN** a new version of the package that still carries "Application Landscape" is imported
- **THEN** both diagrams stay, and the import result has `diagramsDeleted` 0

#### Scenario: Failed import

- **GIVEN** a package installed "Technology Landscape", which 2 diagrams use
- **WHEN** a new version without "Technology Landscape" is rejected with 422
- **THEN** the viewpoint and both diagrams stay
