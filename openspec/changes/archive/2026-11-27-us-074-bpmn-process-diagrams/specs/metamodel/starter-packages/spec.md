## MODIFIED Requirements

### Requirement: Shipped starter packages with summary

The system SHALL list the starter packages shipped with OEA, each with identifier, name, version, description, the number of metatypes and property definitions, the names of its metatypes, and whether it has been imported into the instance. The packages `oea-starter-togaf-classic` and `oea-starter-bpmn` SHALL be shipped.

Trace: REQ-112 AC1, REQ-076, ADR-005, owner decision 2026-11-27

#### Scenario: Package list of a new instance

- **WHEN** a person with `metamodel:write` lists the starter packages of an instance without metatypes
- **THEN** `oea-starter-togaf-classic` is listed with eight metatypes, among them `Application Component`, `Data Object` and `Technology Component`, and `oea-starter-bpmn` with its metatypes, among them `Pool`, `User Task` and `Sequence Flow`, both as not imported

## ADDED Requirements

### Requirement: BPMN starter package

`oea-starter-bpmn` SHALL bring the BPMN types of REQ-076 without data, organizational units and assignments: 26 element metatypes, the connection types "Sequence Flow", "Message Flow", "Association", "Calls" and "References Process" limited to BPMN endpoints, en and de labels, and the viewpoint "Process Model (BPMN 2.0)" (notation `bpmn2`) with all of them, default sizes and BPMN elements.

Trace: REQ-076 AC1, AC6, US-074 AC1, owner decision 2026-11-27

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

### Requirement: Starter packages on the metamodel configuration

The metamodel configuration (SCR-030) SHALL show holders of `metamodel:write` the shipped starter packages with their summary and whether each is imported, and "Import" for a package not yet imported, also when the instance has metatypes; the result or the conflicts of the import SHALL be shown. Others SHALL not see the section.

Trace: REQ-112, ADR-005, owner decision 2026-11-27

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
