# metamodel/solution-extensions Specification

## Purpose

Defines metamodel extensions per solution: configurations with `scope=solution` that inherit the instance metamodel and add their own metatypes, the rules for their names and base types, the effective metamodel of an extension and the permission that maintains them.

## Requirements

### Requirement: Extension configurations

A principal with `solution-metamodel:write` SHALL create an extension for a solution ID with a name and an edit mode; it SHALL inherit from the instance configuration and be listed with it. An extension without metatypes SHALL be deletable, one with metatypes rejected with 409. Changes SHALL be audited.

Trace: US-037 AC1, REQ-037, BR-10, owner decision 2026-11-04

#### Scenario: Extension for Cloud Migration 2027

- **WHEN** an architect with `solution-metamodel:write` creates an extension for the solution "Cloud Migration 2027" with `editMode=gui-and-import`
- **THEN** `GET /metamodel/configurations` lists it with `scope=solution` and the instance configuration as parent

#### Scenario: Without permission

- **WHEN** a person holding only `viewer` creates an extension
- **THEN** the request is rejected with 403

### Requirement: Metatypes of an extension

A principal with `solution-metamodel:write` SHALL create, change and delete metatypes in an extension and map properties to them; the instance metamodel SHALL stay unchanged. A name used in the instance or twice in the extension SHALL be rejected with 409, and an instance metatype SHALL NOT take a name used in an extension.

Trace: US-037 AC1, AC3, AC4, REQ-037, BR-09, owner decision 2026-11-04

#### Scenario: CloudService only in the extension

- **WHEN** an architect creates `CloudService` in the extension of "Cloud Migration 2027"
- **THEN** `CloudService` is part of the effective metamodel of the extension, and `GET /metamodel/metatypes` of the instance does not list it

#### Scenario: Instance type redefined

- **WHEN** an architect creates `Application Component` in an extension
- **THEN** the request is rejected with 409 `name-taken` and nothing is created

#### Scenario: Two architects maintain one extension

- **WHEN** two persons with `solution-metamodel:write` each create a metatype in the same extension
- **THEN** both metatypes are part of the extension and each creation is audited with its author

### Requirement: Effective metamodel of an extension

`GET /metamodel/metatypes?configId=<extension>` SHALL return the instance metatypes and those of the extension, each with its `configId`. A metatype of an extension MAY have an instance metatype or one of the same extension as base type; until solutions exist, its metatypes SHALL NOT be used for entities or catalogs.

Trace: US-037 AC2, REQ-037, owner decision 2026-11-04

#### Scenario: Both groups in the effective metamodel

- **WHEN** a client reads the metatypes of the extension of "Cloud Migration 2027"
- **THEN** the answer contains `Application Component` with the configuration of the instance and `CloudService` with that of the extension

#### Scenario: Entity of an extension metatype

- **WHEN** an architect writes an entity of `CloudService` through the batch
- **THEN** the entity is rejected with `METATYPE_NOT_FOUND`
