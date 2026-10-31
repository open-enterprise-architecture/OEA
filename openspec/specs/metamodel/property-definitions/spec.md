# metamodel/property-definitions Specification

## Purpose

Defines the organization's own property definitions with their data types, enum values and categories, and what happens to entity values when they change.

## Requirements

### Requirement: Property definitions

A principal with `metamodel:write` SHALL create, change and delete property definitions with `scope=organization` and one of the data types `string`, `text`, `integer`, `boolean`, `date`, `url` or `enum`; names SHALL be unique case-insensitively. Each change SHALL be audited (`metamodel.property-created`, `-updated`, `-deleted`).

Trace: UC-04 step 6, REQ-032, ADR-022

#### Scenario: Enum property with values

- **WHEN** a metamodel administrator creates the enum property `Trust Level` and adds the values `low`, `medium` and `high`
- **THEN** listing the property definitions returns `Trust Level` with its three values in order, and audit events exist for the property and each value

#### Scenario: Name taken

- **WHEN** a metamodel administrator creates a property named `status` while the imported property `Status` exists
- **THEN** the request is rejected with 409 and problem type `name-taken`

### Requirement: Changing definitions with existing values

The data type of a property definition SHALL NOT change while entity values exist, including in the history. A definition SHALL NOT be deleted while it is mapped, and a category while definitions belong to it (409 `in-use`); a deleted definition that appears in the history SHALL be retired. Deleting an enum value SHALL remove it from the entities that use it.

Trace: REQ-032 AC4, ADR-022, owner decision 2026-10-31

#### Scenario: Enum value in use

- **WHEN** a metamodel administrator deletes the enum value `high` that two entities use
- **THEN** the value is deleted, both entities get a new version without a value for the property, and an audit event `metamodel.enum-value-deleted` names two entities

### Requirement: Read-only property definitions

Built-in and imported property definitions, their enum values and categories SHALL NOT be changed or deleted; such a request SHALL be rejected with 409 and problem type `read-only-metatype`.

Trace: ADR-002

#### Scenario: Imported property

- **WHEN** a metamodel administrator adds a value to the imported property `Status`
- **THEN** the request is rejected with 409, and the values of `Status` are unchanged
