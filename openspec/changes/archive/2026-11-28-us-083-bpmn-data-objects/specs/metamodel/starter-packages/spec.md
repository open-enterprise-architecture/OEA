## MODIFIED Requirements

### Requirement: BPMN starter package

`oea-starter-bpmn` 1.1.0 SHALL bring the BPMN types of REQ-076 and REQ-081: 28 element metatypes with "BPMN Data Object" and "BPMN Data Store", the connection types of 1.0.0 and "Data Input Association", "Data Output Association" and "Realized By" with open data ends, their properties, en and de labels, and the viewpoint "Process Model (BPMN 2.0)" (`bpmn2`) with all of them.

Trace: REQ-076 AC1, AC6, REQ-081 AC1, US-074 AC1, US-083, owner decisions 2026-11-27 and 2026-11-28

#### Scenario: BPMN imported next to TOGAF

- **GIVEN** `oea-starter-togaf-classic` is imported
- **WHEN** Kurt imports `oea-starter-bpmn`
- **THEN** the BPMN metatypes exist with `scope=imported` and the origin `oea-starter-bpmn`, the viewpoint "Process Model (BPMN 2.0)" can be chosen for a new diagram, and the TOGAF content is unchanged

#### Scenario: BPMN imported alone

- **WHEN** Kurt imports `oea-starter-bpmn` into an instance without metatypes
- **THEN** the import succeeds, and no connection type is marked as composition

#### Scenario: Subtypes of the BPMN types

- **GIVEN** `oea-starter-bpmn` is imported
- **WHEN** Kurt reads "User Task" and "Message Start Event"
- **THEN** their base types are "Task" and "Start Event"

#### Scenario: Name taken

- **GIVEN** the organization has its own metatype "Task"
- **WHEN** Kurt imports `oea-starter-bpmn`
- **THEN** the import is rejected with 409 naming "Task", and nothing is imported

#### Scenario: Re-import of 1.1.0

- **GIVEN** `oea-starter-bpmn` 1.0.0 is imported and has diagrams
- **WHEN** Kurt imports 1.1.0
- **THEN** the data types and data associations are added and allowed in "Process Model (BPMN 2.0)", the IDs of the existing types and of the viewpoint are unchanged, and the diagrams, their layouts and the entities are unchanged without conflicts

#### Scenario: TOGAF data object as source

- **GIVEN** the TOGAF and the BPMN package are imported
- **WHEN** a batch writes a "Data Input Association" from the TOGAF "Data Object" "Order" to a task, and one to a gateway
- **THEN** the first is stored, and the second is rejected with 422 for its target

### Requirement: Starter packages on the metamodel configuration

The metamodel configuration (SCR-030) SHALL show holders of `metamodel:write` the shipped starter packages with their summary, the imported version, "Import" for a package not yet imported, also when the instance has metatypes, and "Update to <version>" for one imported in another version; the result or the conflicts SHALL be shown. Others SHALL not see the section.

Trace: REQ-112, ADR-005, US-083, owner decisions 2026-11-27 and 2026-11-28

#### Scenario: Import BPMN on a used instance

- **GIVEN** the instance has the TOGAF metatypes
- **WHEN** Kurt opens the metamodel configuration and chooses "Import" on "BPMN 2.0 process modeling"
- **THEN** the number of created metatypes is shown, and the package is listed as imported without "Import"

#### Scenario: Conflicts shown

- **WHEN** the import of a starter package is rejected with 409
- **THEN** the section lists the conflicting names, and nothing is imported

#### Scenario: Reader

- **WHEN** a person without `metamodel:write` opens the metamodel configuration
- **THEN** no section "Starter packages" is shown

#### Scenario: Update an older import

- **GIVEN** `oea-starter-bpmn` 1.0.0 is imported and 1.1.0 is shipped
- **WHEN** Kurt opens the metamodel configuration and chooses "Update to 1.1.0" on "BPMN 2.0 process modeling"
- **THEN** the package is imported with `merge`, the result is shown, and the package is listed with the imported version 1.1.0 without "Update"
