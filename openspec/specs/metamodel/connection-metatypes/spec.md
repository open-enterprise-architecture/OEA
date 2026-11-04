# metamodel/connection-metatypes Specification

## Purpose

Defines connection metatypes: metatypes whose instances connect a source and a target entity, which metatypes they may connect, how base types and packages handle them, and how clients ask which connection metatypes may connect two metatypes.

## Requirements

### Requirement: Connection metatypes with allowed endpoints

A metamodel administrator SHALL create a metatype as connection metatype with optional lists of allowed source and target metatypes; an empty list allows every metatype. Whether a metatype is a connection SHALL NOT change. Changing the lists SHALL be audited like other metatype changes.

Trace: US-036, REQ-036, US-032 AC1 (relation), owner decision 2026-11-04

#### Scenario: DataFlow between applications

- **WHEN** an administrator creates `DataFlow` as connection metatype with `Application Component` and `DataFlow` as allowed sources and targets
- **THEN** `GET /metamodel/metatypes/{id}` returns `isConnection=true` and both metatypes for each direction

#### Scenario: Relation as connection metatype

- **WHEN** an administrator creates `Contains` as connection metatype from `SecurityZone` to `Technology Component`
- **THEN** connections of `Contains` can be written from a security zone to a technology component

#### Scenario: Connection flag fixed

- **WHEN** an administrator changes an existing element metatype to a connection metatype
- **THEN** the request is rejected with 422 and the metatype is unchanged

### Requirement: Subtypes and connections as endpoints

An allowed endpoint metatype SHALL also allow its subtypes. A connection metatype MAY be an allowed endpoint, so that a connection starts or ends at another connection. The base type of a connection metatype SHALL be a connection metatype, that of an element metatype an element metatype (422).

Trace: US-036 AC2, AC3, ADR-010, owner decision 2026-11-04

#### Scenario: Subtype as source

- **WHEN** `CloudZone` has the base type `SecurityZone` and `Contains` allows `SecurityZone` as source
- **THEN** a connection of `Contains` from a cloud zone is accepted

#### Scenario: Mixed base type

- **WHEN** an administrator sets the element metatype `SecurityZone` as base type of the connection metatype `DataFlow`
- **THEN** the request is rejected with 422

### Requirement: Connection metatypes in packages

A package SHALL bring connection metatypes with their allowed endpoints in `connectionConstraints`; each named metatype SHALL be part of the package or built-in (422 otherwise). A re-import SHALL replace the constraints of its own metatypes.

Trace: US-036 AC4, REQ-114, ADR-002

#### Scenario: Imported connection metatype

- **WHEN** a package with a connection metatype that allows one of its element metatypes as source is imported
- **THEN** the metatype is listed as connection metatype with that source, and it is read-only

### Requirement: Allowed connections between two metatypes

`GET /metamodel/allowed-connections` SHALL list for a source and a target metatype every connection metatype that may connect them, with the direction `source-to-target`, `target-to-source` or `bidirectional`, and `allowed=false` if there is none.

Trace: ADR-026, US-036

#### Scenario: Direction of a data flow

- **WHEN** a client asks for the allowed connections from `Application Component` to `Application Component`
- **THEN** the answer is `allowed=true` with `DataFlow` and the direction `bidirectional`
