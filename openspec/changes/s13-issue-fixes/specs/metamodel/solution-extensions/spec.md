## ADDED Requirements

### Requirement: An extension names an existing solution

A metamodel extension SHALL name an existing solution. Creating an extension for an unknown solution SHALL be answered with 422 naming `solutionId`, and nothing SHALL be stored. A solution SHALL not be removable while an extension names it.

Trace: US-037, US-038, issue #237

#### Scenario: Extension of an existing solution

- **WHEN** a metamodel administrator creates an extension for the solution "ERP Migration"
- **THEN** the extension is created and names "ERP Migration"

#### Scenario: Unknown solution

- **WHEN** an extension is created for a solution ID that does not exist
- **THEN** the request is answered with 422 naming `solutionId`, and no extension is stored

### Requirement: Migration of extensions without a solution

The migration that adds the reference SHALL abort, and SHALL list every extension whose solution does not exist, instead of deleting or inventing data. Without such extensions it SHALL add the reference.

Trace: issue #237, owner decision 2026-11-15

#### Scenario: Orphaned extension

- **GIVEN** an extension whose solution does not exist
- **WHEN** the migration runs
- **THEN** it aborts with a message naming the extension, and the schema is unchanged

#### Scenario: No orphaned extension

- **WHEN** every extension names an existing solution and the migration runs
- **THEN** the reference is added
