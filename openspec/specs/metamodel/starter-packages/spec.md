# metamodel/starter-packages Specification

## Purpose

Defines the metamodel packages shipped with OEA as starting points for a new instance (ADR-005): which packages exist, how they are presented and how one is imported without uploading a file.

## Requirements

### Requirement: Shipped starter packages with summary

The system SHALL list the starter packages shipped with OEA, each with identifier, name, version, description, the number of metatypes and property definitions, the names of its metatypes, and whether it has been imported into the instance. The packages `oea-starter-togaf-classic`, `oea-starter-bpmn` and `oea-starter-organization` SHALL be shipped.

Trace: REQ-112 AC1, REQ-076, REQ-077, ADR-005, owner decisions 2026-11-27

#### Scenario: Package list of a new instance

- **WHEN** a person with `metamodel:write` lists the starter packages of an instance without metatypes
- **THEN** `oea-starter-togaf-classic` is listed with ten metatypes, among them `Application Component`, `Data Object`, `Data Flow` and `Technology Component`, `oea-starter-bpmn` with its metatypes, among them `Pool`, `User Task` and `Sequence Flow`, and `oea-starter-organization` with `Organizational Unit`, `Role` and `Person`, all as not imported

### Requirement: Import of a starter package by identifier

A person with `metamodel:write` SHALL import a shipped starter package by its identifier, with the same validation, transaction, conflict and audit rules as any package import.

Trace: REQ-112, US-033 AC1, ADR-005

#### Scenario: Starter configuration imported

- **WHEN** a person with `metamodel:write` imports `oea-starter-togaf-classic`
- **THEN** the seven metatypes exist with `scope=imported`, each with the property `Status` (planned, active, phasing out, retired), the package is listed as imported, and the audit trail contains the import with one batch ID

#### Scenario: Unknown starter package

- **WHEN** a starter package with an unknown identifier is requested
- **THEN** the request is rejected with 404

### Requirement: Starter configuration offered to the metamodel administrator

The start page SHALL offer the shipped starter packages to a person with `metamodel:write` while the instance has no metatype, and SHALL confirm the result of the import.

Trace: ADR-005 (bootstrapping flow), docs/walking-skeleton.md step 5

#### Scenario: Card on a new instance

- **WHEN** a person with `metamodel:write` opens the start page of an instance without metatypes
- **THEN** the card "Import starter configuration" lists `oea-starter-togaf-classic` with its summary, and importing it shows the number of created metatypes

#### Scenario: No card without permission or after the import

- **WHEN** a person without `metamodel:write` opens the start page, or the instance already has metatypes
- **THEN** no starter configuration card is shown

### Requirement: BPMN starter package

`oea-starter-bpmn` 1.2.0 SHALL bring the BPMN types of REQ-076 and REQ-081: 28 element metatypes with "BPMN Data Object" and "BPMN Data Store", the connection types of 1.0.0, the data associations and "Realized By" with open data ends, their properties, en and de labels, the viewpoint "Process Model (BPMN 2.0)" (`bpmn2`) with all of them, and the section `lineageModel`.

Trace: REQ-076 AC1, AC6, REQ-081 AC1, US-074 AC1, US-083, REQ-081 AC6, owner decisions 2026-11-27 and 2026-11-28

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

#### Scenario: Update over 1.1.0

- **GIVEN** 1.1.0 is imported with diagrams, and no data input part is set
- **WHEN** Kurt updates to 1.2.0
- **THEN** data input and data output name "Data Input Association" and "Data Output Association", the import result lists both parts, and the BPMN types, the viewpoint and the diagrams are unchanged

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

### Requirement: Organization starter package

`oea-starter-organization` 1.0.0 SHALL bring the element types "Organizational Unit", "Role" and "Person" (with the string property "Account ID" of at most 255 characters), the connection types "Belongs To", "Performs Role" and "Responsible" to them, each from any element, en and de labels, the section `organizationModel` and no viewpoint. It SHALL be importable alone or next to the TOGAF and the BPMN package.

Trace: US-075, US-076, REQ-077, owner decisions 2026-11-27

#### Scenario: Import into an instance with the BPMN package

- **GIVEN** the instance has imported `oea-starter-bpmn`
- **WHEN** a metamodel administrator imports `oea-starter-organization`
- **THEN** the three element types and the three connection types exist, and a lane can be connected to an organizational unit by "Belongs To"

#### Scenario: Responsible for a TOGAF object

- **GIVEN** the instance has imported the TOGAF and the organization package
- **WHEN** Anna connects the Application Component "SAP ERP" to the person "Maria Müller" by "Responsible"
- **THEN** the connection is stored

#### Scenario: Responsible names persons only

- **WHEN** Anna connects "SAP ERP" to the role "Approver" by "Responsible"
- **THEN** the connection is rejected with 422, and nothing is stored

#### Scenario: Import alone

- **WHEN** a metamodel administrator imports `oea-starter-organization` into an instance without other packages
- **THEN** the import succeeds, and a re-import changes nothing

### Requirement: Lineage types of the TOGAF starter package

`oea-starter-togaf-classic` 1.5.0 SHALL add "Data Flow" between application and technology elements, "Carries Data" from it to "Data Object", optional data properties on both (classification, owner, personal data categories; protocol, frequency, transformation description), and the section `lineageModel`. An update over 1.4.0 SHALL keep the existing content.

Trace: US-063, US-064, REQ-060, REQ-061, ADR-010, owner decisions 2026-11-28

#### Scenario: Carries data

- **GIVEN** 1.5.0 is imported, with the data flow "SAP ERP → DWH" and the data object "Customer Master"
- **WHEN** Lukas connects the data flow to "Customer Master" by "Carries Data"
- **THEN** the connection is stored with an ID of its own, its source the data flow and its target the data object

#### Scenario: Source without the n-connection

- **WHEN** Lukas connects an Application Component to "Customer Master" by "Carries Data"
- **THEN** the request is rejected with 422 `SOURCE_NOT_ALLOWED`, and nothing is stored

#### Scenario: Target is a connection

- **WHEN** Lukas connects the data flow by "Carries Data" to another data flow
- **THEN** the request is rejected with 422 `TARGET_NOT_ALLOWED`

#### Scenario: No connection of the carrying connection

- **WHEN** Lukas starts a "Realized By" connection, whose source list is empty, at a "Carries Data" connection
- **THEN** the request is rejected with 422 `CONNECTION_DEPTH_EXCEEDED` naming the source

#### Scenario: Text limits

- **WHEN** Lukas saves "Personal data categories" of 500 and of 501 characters, "Transformation description" of 1000 and of 1001, and "Data owner" of 255 and of 256
- **THEN** each first value is stored, and each second is rejected with 422 naming the property, and nothing changes

#### Scenario: Update over 1.4.0

- **GIVEN** 1.4.0 is imported with data objects and diagrams
- **WHEN** Kurt updates to 1.5.0
- **THEN** the new types and properties are added, "Data Flow" is allowed in "Application Landscape" and "Technology Landscape", the existing entities and diagrams keep their values and IDs, and no data object becomes incomplete
