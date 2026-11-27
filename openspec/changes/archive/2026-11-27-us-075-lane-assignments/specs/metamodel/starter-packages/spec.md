## MODIFIED Requirements

### Requirement: Shipped starter packages with summary

The system SHALL list the starter packages shipped with OEA, each with identifier, name, version, description, the number of metatypes and property definitions, the names of its metatypes, and whether it has been imported into the instance. The packages `oea-starter-togaf-classic`, `oea-starter-bpmn` and `oea-starter-organization` SHALL be shipped.

Trace: REQ-112 AC1, REQ-076, REQ-077, ADR-005, owner decisions 2026-11-27

#### Scenario: Package list of a new instance

- **WHEN** a person with `metamodel:write` lists the starter packages of an instance without metatypes
- **THEN** `oea-starter-togaf-classic` is listed with eight metatypes, among them `Application Component`, `Data Object` and `Technology Component`, `oea-starter-bpmn` with its metatypes, among them `Pool`, `User Task` and `Sequence Flow`, and `oea-starter-organization` with `Organizational Unit`, `Role` and `Person`, all as not imported

## ADDED Requirements

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
