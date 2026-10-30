# Spec Delta

## Purpose

Defines the organization's own property definitions with their data types, enum values and categories, and the rules that keep existing entity values valid.

## ADDED Requirements

### Requirement: Property definitions

A principal with `metamodel:write` SHALL create, change and delete property definitions with `scope=organization` and one of the data types `string`, `text`, `integer`, `boolean`, `date`, `url` or `enum`; names SHALL be unique case-insensitively. Each change SHALL be audited (`metamodel.property-created`, `-updated`, `-deleted`).

Trace: UC-04 step 6, REQ-032, ADR-022

#### Scenario: Enum property with values

- **WHEN** a metamodel administrator creates the enum property `Trust Level` and adds the values `low`, `medium` and `high`
- **THEN** listing the property definitions returns `Trust Level` with its three values in order, and audit events exist for the property and each value

#### Scenario: Name taken

- **WHEN** a metamodel administrator creates a property named `status` while the imported property `Status` exists
- **THEN** the request is rejected with 409 and problem type `name-taken`

### Requirement: Existing values stay valid

The data type of a property definition SHALL NOT change while entity values exist, including those in the entity history. A property definition SHALL NOT be deleted while it has mappings or values, an enum value SHALL NOT be deleted while entities use it, and a category SHALL NOT be deleted while property definitions belong to it; each with 409 and problem type `in-use`.

Trace: REQ-032 AC4, ADR-022

#### Scenario: Enum value in use

- **WHEN** a metamodel administrator deletes the enum value `high` that two entities use
- **THEN** the request is rejected with 409 and problem type `in-use`, and the value stays

### Requirement: Read-only property definitions

Built-in and imported property definitions, their enum values and categories SHALL NOT be changed or deleted; such a request SHALL be rejected with 409 and problem type `read-only-metatype`.

Trace: ADR-002

#### Scenario: Imported property

- **WHEN** a metamodel administrator adds a value to the imported property `Status`
- **THEN** the request is rejected with 409, and the values of `Status` are unchanged
