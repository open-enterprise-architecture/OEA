# repository/connection-write Specification

## Purpose

Defines how connections – entities of a connection metatype with a source and a target entity – are written through `createConnection` and the batch: the checks of the endpoints, fixed endpoints, the default name, versioning and audit.

## Requirements

### Requirement: Connections with checked endpoints

A connection SHALL have exactly one existing, not deleted source and target entity, given by ID or external ID, whose metatypes the connection metatype allows. Properties SHALL be checked as for entities. A violation SHALL reject the connection with an error code for the field.

Trace: US-036 AC1, AC3, REQ-036, US-141 AC2

#### Scenario: DataFlow without source

- **WHEN** an architect writes a `DataFlow` connection without a source
- **THEN** it is rejected with the error code `SOURCE_REQUIRED` and nothing is stored

#### Scenario: Connection as source not allowed

- **WHEN** a connection metatype allows only `Application Component` as source and a connection names a `DataFlow` connection as source
- **THEN** it is rejected with the error code `SOURCE_NOT_ALLOWED`

#### Scenario: Unknown target

- **WHEN** a connection names a target external ID that no entity has
- **THEN** it is rejected with the error code `TARGET_NOT_FOUND`

### Requirement: T-relationships

A connection SHALL be a valid source or target of another connection when the connection metatype allows its metatype.

Trace: US-036 AC2, ADR-010

#### Scenario: Branching data flow

- **GIVEN** the data flow DF-1 from AppA to AppB
- **WHEN** the architect writes the data flow DF-2 with the source DF-1 and the target AppC
- **THEN** DF-2 is stored with DF-1 as source

### Requirement: Writing through createConnection and the batch

`POST /connections` SHALL create one connection and answer 201 with source and target. The batch SHALL write `connections` after its `entities`, so that they may name entities of the same batch by external ID, with the strategies, atomicity and limits of entities.

Trace: US-141 AC2, ADR-026

#### Scenario: Entities and connections in one batch

- **WHEN** a batch creates the applications AppA and AppB and a `DataFlow` between them by external ID
- **THEN** the response lists all three items as created and the connection has AppA as source

#### Scenario: Upsert of an existing connection

- **WHEN** a connection with an existing external ID is sent again with another protocol
- **THEN** the connection gets a new version with the new protocol

### Requirement: Fixed endpoints and default name

The source and target of a connection SHALL NOT change; such a write SHALL be rejected with `ENDPOINT_CHANGE_NOT_SUPPORTED`. A connection without a name SHALL be named "source name → target name". Connections SHALL be versioned and audited like entities.

Trace: owner decision 2026-11-04, ADR-022

#### Scenario: Default name

- **WHEN** a `DataFlow` from `CRM` to `ERP` is created without a name
- **THEN** its name is `CRM → ERP`

#### Scenario: Other target on update

- **WHEN** an existing connection is written by external ID with another target
- **THEN** it is rejected with `ENDPOINT_CHANGE_NOT_SUPPORTED` and stays unchanged
