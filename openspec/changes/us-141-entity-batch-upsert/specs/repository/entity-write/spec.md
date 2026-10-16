# Spec Delta

## Purpose

Defines how entities of the EA repository are written against the metamodel: which names and property values are accepted, how the necessity of a property is enforced, how every change is versioned and how it is audited. The rules apply to every write path; this change introduces the first one, the batch write.

## ADDED Requirements

### Requirement: Entities typed by an element metatype

An entity SHALL have a non-blank name of at most 255 characters, a description of at most 2000 characters and exactly one metatype of the instance that is not a connection type. The metatype of an existing entity SHALL NOT be changed by a write.

Trace: REQ-153 (error cases), ADR-022, US-141 AC1

#### Scenario: Unknown metatype

- **WHEN** an entity is written with a metatype ID that does not exist
- **THEN** the entity is rejected with the error code `METATYPE_NOT_FOUND` for the field `metatypeId`

#### Scenario: Metatype changed on update

- **WHEN** an existing entity is written with another metatype than its own
- **THEN** the entity is rejected with the error code `METATYPE_CHANGE_NOT_SUPPORTED` and stays unchanged

### Requirement: Property values checked against the mapped definitions

The system SHALL accept a property value only for a property definition with an active mapping for the metatype of the entity (the mapping with the latest `validFrom` not after today) and SHALL convert and check it according to the data type, length, range, pattern and enum values of the definition. A value for a property without active mapping SHALL be ignored with a warning.

Trace: REQ-149 (error cases), ADR-022

#### Scenario: Value outside the enum values

- **WHEN** an `Application Component` is written with the value `unknown` for the property `Status`
- **THEN** the entity is rejected with an error for the field of that value and the code `INVALID_VALUE`

#### Scenario: Property not mapped to the metatype

- **WHEN** an entity is written with a value for a property definition that is not mapped to its metatype
- **THEN** the value is ignored and the result contains a warning with the code `PROPERTY_NOT_MAPPED`

### Requirement: Necessity of properties

A mandatory property without value SHALL be an error; a property with necessity `warning` without value SHALL produce a warning; an optional property without value SHALL produce no notice. When an entity is created, the default value of a mapping SHALL apply to a property without value.

Trace: REQ-149 AC1, AC2, ADR-022

#### Scenario: Mandatory property missing

- **WHEN** an entity of a metatype with a mandatory property is written without a value for it
- **THEN** the entity is rejected with an error with the field `properties` and the code `MANDATORY_MISSING`

#### Scenario: Property with necessity warning missing

- **WHEN** an entity of a metatype with a property of necessity `warning` is written without a value for it
- **THEN** the entity is written and the result contains a warning with the field `properties[<property name>]`

### Requirement: Versioned changes with delta history

The system SHALL create an entity with version 1 and increment the version with every change. For every change it SHALL record the version, the time and the person, and for each changed system field (name, description, logical flag) and each changed property only the value before the change. A write that changes nothing SHALL create no new version.

Trace: ADR-022 (delta versioning), ADR-016, US-141 AC1

#### Scenario: Entity changed

- **WHEN** the name and the `Status` of an entity in version 1 are changed
- **THEN** the entity has version 2, and the history of version 2 contains the previous name and the previous `Status` value, but no other field

#### Scenario: Unchanged write

- **WHEN** an entity is written again with the same name, description and property values
- **THEN** its version stays the same and no history and no audit event are written

### Requirement: Audit of entity changes

Every created or changed entity SHALL be audited in the hash-chained audit trail with the event type `entity.created` or `entity.updated`, the pseudonymous actor, the entity reference, the changed system fields and properties with their old and new values, and the batch ID of the request.

Trace: ADR-033 §3, REQ-154 (context), US-141 AC1

#### Scenario: Created entity audited

- **WHEN** a person creates an `Application Component` named `CRM System` with `Status` `active`
- **THEN** the audit trail contains `entity.created` with the person as actor, the entity ID and name, the new values of name and `Status` and the batch ID, and the chain verifies

### Requirement: Agent headers as informational audit fields

A value of the header `X-Agent-Id` SHALL be stored only in the informational audit field `agent_label` (at most 100 characters, control characters removed) and the header `X-Agent-Run` in `agent_run`; `agent_id` SHALL stay empty for persons.

Trace: ADR-033 §7, REQ-154 AC5 (service accounts later), US-141 AC4 (headers)

#### Scenario: Write with agent headers

- **WHEN** a person writes an entity sending `X-Agent-Id: repo-scanner` and `X-Agent-Run: run-42`
- **THEN** its audit event has `agent_label` `repo-scanner`, `agent_run` `run-42` and no `agent_id`

### Requirement: Restricted properties not writable yet

Until audit values of restricted properties can be encrypted (REQ-160 AC4), the system SHALL reject a value for a property whose visibility mode is not `public`.

Trace: REQ-160 AC4, ADR-033 §3

#### Scenario: Property with restricted visibility

- **WHEN** an entity is written with a value for a property whose visibility mode is not `public`
- **THEN** the entity is rejected with the error code `RESTRICTED_PROPERTY_NOT_SUPPORTED`
