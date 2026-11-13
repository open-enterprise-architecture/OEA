# metamodel/metatype-editing Specification

## Purpose

Defines how metamodel administrators create, change and delete the organization's own metatypes and their base types, and why built-in and imported metatypes stay read-only.

## Requirements

### Requirement: Creating a metatype

A principal with `metamodel:write` SHALL create a metatype with name, abbreviation, sort order, an optional base type and optionally as connection metatype; it SHALL get `scope=organization`, be audited as `metamodel.metatype-created` with the actor, and be usable at once. A name already used by any metatype, compared case-insensitively, SHALL be rejected with 409.

Trace: US-032 AC1, US-032 AC2, US-032 AC3, REQ-032 AC1, REQ-032 AC2, US-036, ADR-002

#### Scenario: SecurityZone created

- **WHEN** a metamodel administrator creates the metatype `SecurityZone`
- **THEN** it is listed with `scope=organization`, entities of `SecurityZone` can be written through the batch API, and an audit event `metamodel.metatype-created` names the administrator

#### Scenario: Connection metatype created

- **WHEN** a metamodel administrator creates `DataFlow` with `isConnection=true`
- **THEN** it is listed as connection metatype, and connections of `DataFlow` can be written

#### Scenario: Name of an imported metatype

- **WHEN** a metamodel administrator creates a metatype named `application component`
- **THEN** the request is rejected with 409 and problem type `name-taken`, and no metatype is stored

#### Scenario: Without permission

- **WHEN** a person holding only `enterprise-architect` creates a metatype
- **THEN** the request is rejected with 403

### Requirement: Base types

A metatype SHALL have at most one base type of any scope and SHALL inherit the property mappings of all its base types. A base type forming a cycle SHALL be rejected with 422, one mapping a property already mapped in the chain of the metatype or its subtypes with 409.

Trace: owner decision 2026-10-31, business object metamodel-configuration (`extends`)

#### Scenario: Subtype inherits mappings

- **WHEN** a metamodel administrator creates `CloudZone` with base type `SecurityZone`, which maps `Trust Level`
- **THEN** reading `CloudZone` lists `Trust Level` as inherited from `SecurityZone`, and entities of `CloudZone` are validated against it

#### Scenario: Cycle

- **WHEN** a metamodel administrator sets `CloudZone` as base type of `SecurityZone`
- **THEN** the request is rejected with 422, and the base types are unchanged

### Requirement: Changing and deleting a metatype

A metatype with `scope=organization` SHALL be changed, including its base type, and deleted by a principal with `metamodel:write`, audited as `metamodel.metatype-updated` and `-deleted`. A changed base type SHALL remove and add inherited properties as a mapping does. Deleting SHALL be rejected with 409 `in-use` while entities exist, also deleted ones, or a catalog or subtype uses it.

Trace: REQ-032 AC3, REQ-032 AC4, owner decision 2026-10-31

#### Scenario: Metatype with entities

- **WHEN** a metamodel administrator deletes `SecurityZone`, which has five entities
- **THEN** the request is rejected with 409 and problem type `in-use` stating five entities, and the metatype stays

### Requirement: Read-only metatypes

Built-in and imported metatypes SHALL NOT be changed or deleted; such a request SHALL be rejected with 409 and problem type `read-only-metatype`. Mapping organization properties to them, setting their re-import setting and setting "Show parallel changes in the baseline" stay possible.

Trace: REQ-032 AC5, ADR-002, owner decisions 2026-10-30, 2026-10-31 and 2026-11-13

#### Scenario: Imported metatype

- **WHEN** a metamodel administrator renames the imported metatype `Application Component`
- **THEN** the request is rejected with 409 and problem type `read-only-metatype`, and the metatype is unchanged

### Requirement: Scoping type cannot be deleted

A connection metatype that is the scoping type of a property definition SHALL NOT be deleted. The request SHALL be rejected with 409 `in-use` until no property uses it as scoping type.

Trace: REQ-124, REQ-127

#### Scenario: Delete the scoping type

- **GIVEN** `DomainAssignment` has no connections and is the scoping type of `securityClassification`
- **WHEN** a metamodel administrator deletes `DomainAssignment`
- **THEN** the request is rejected with 409 `in-use`, and the metatype stays

### Requirement: Parallel-change setting of a metatype

Each metatype SHALL have the setting "Show parallel changes in the baseline" (`showParallelChanges`), off by default. A principal with `metamodel:write` SHALL set it through `PUT /metamodel/metatypes/{id}/parallel-changes` with `If-Match`, for a metatype of any scope. Each change SHALL raise the version of the metatype and SHALL be audited as `metamodel.metatype-parallel-changes-changed` with the value before and after.

Trace: US-044, owner decision 2026-11-13

#### Scenario: Turning the setting on for a built-in metatype

- **GIVEN** the setting of the built-in metatype `Application Component` is off
- **WHEN** a metamodel administrator turns it on
- **THEN** the metatype shows `showParallelChanges` true and a version raised by 1
- **AND** the metamodel changes list the change with the administrator, under the kind `metatype`

#### Scenario: Default off

- **WHEN** a metamodel administrator creates the metatype `SecurityZone`
- **THEN** its setting is off

#### Scenario: Without metamodel administration

- **WHEN** a person holding only `solution-metamodel:write` sets the setting
- **THEN** the request is rejected with 403 and the setting is unchanged

#### Scenario: Outdated version

- **WHEN** the setting is sent with a version other than the current one
- **THEN** the request is rejected with 412 and the setting is unchanged

### Requirement: Parallel-change setting kept and not inherited

A package re-import SHALL keep the setting "Show parallel changes in the baseline". A subtype SHALL NOT inherit the setting of its base type.

Trace: US-044, ADR-049, owner decision 2026-11-13

#### Scenario: Re-import keeps the setting

- **GIVEN** the setting of an imported metatype is on
- **WHEN** its package is imported again
- **THEN** the setting is still on

#### Scenario: Not inherited

- **GIVEN** the setting of a base type is on and that of its subtype is off
- **WHEN** the baseline of a solution is read
- **THEN** entities of the subtype carry no parallel changes
