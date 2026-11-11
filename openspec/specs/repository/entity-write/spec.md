# repository/entity-write Specification

## Purpose

Defines how entities of the EA repository are written against the metamodel: which names and property values are accepted, how the necessity of a property is enforced, how every change is versioned and how it is audited. The rules apply to every write path; this change introduces the first one, the batch write.

## Requirements

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

The system SHALL accept a property value only for a property definition with an active mapping for the metatype of the entity or one of its base types (the mapping with the latest `validFrom` not after today) and SHALL convert and check it according to the data type, length, range, pattern and enum values of the definition. A value for a property without active mapping SHALL be ignored with a warning.

Trace: REQ-149 (error cases), ADR-022

#### Scenario: Value outside the enum values

- **WHEN** an `Application Component` is written with the value `unknown` for the property `Status`
- **THEN** the entity is rejected with an error for the field of that value and the code `INVALID_VALUE`

#### Scenario: Property not mapped to the metatype

- **WHEN** an entity is written with a value for a property definition that is not mapped to its metatype
- **THEN** the value is ignored and the result contains a warning with the code `PROPERTY_NOT_MAPPED`

### Requirement: Necessity of properties

A mandatory property without value SHALL be an error, also for an entity marked as missing it; a property the writer cannot change (not public and no write role) SHALL NOT be checked. A property with necessity `warning` without value SHALL produce a warning. On create, the default value of a mapping SHALL apply to a property without value. Setting the value SHALL clear the mark.

Trace: REQ-149 AC1, AC2, ADR-022, owner decision 2026-10-31, REQ-126

#### Scenario: Mandatory property missing

- **WHEN** an entity of a metatype with a mandatory property is written without a value for it
- **THEN** the entity is rejected with an error with the field `properties` and the code `MANDATORY_MISSING`

#### Scenario: Property with necessity warning missing

- **WHEN** an entity of a metatype with a property of necessity `warning` is written without a value for it
- **THEN** the entity is written and the result contains a warning with the field `properties[<property name>]`

#### Scenario: Marked entity written again

- **WHEN** an entity marked as missing the mandatory property `Trust Level` is written with a changed description and without `Trust Level`
- **THEN** the entity is rejected with 422 and code `MANDATORY_MISSING`

#### Scenario: Mandatory property the writer cannot change

- **WHEN** an entity is written without a value for a mandatory property whose write roles the writer does not hold
- **THEN** the missing value is not reported and the entity is written

#### Scenario: Mandatory restricted property of a writer

- **WHEN** a holder of a write role of the mandatory `role-restricted` property writes an entity without a value for it
- **THEN** the entity is rejected with `MANDATORY_MISSING`

#### Scenario: Write with agent headers

- **WHEN** a person writes an entity sending `X-Agent-Id: repo-scanner` and `X-Agent-Run: run-42`
- **THEN** its audit event has `agent_label` `repo-scanner`, `agent_run` `run-42` and no `agent_id`

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

### Requirement: Writing restricted properties

A value of a `role-restricted` property SHALL be written or removed only by a principal holding one of its write roles, and a value of a `connection-scoped` property only for an entity in the writer's scope. A batch or connection that names such a property otherwise SHALL be rejected with 403 as a whole and the denial audited. Writing an entity without naming a restricted property SHALL keep its value.

Trace: US-126 AC3, US-127 AC1, AC2, REQ-126, REQ-127 AC3, REQ-162, REQ-160 AC4

#### Scenario: Write attempt without write role

- **WHEN** Anna, without a write role, writes `investmentCost` of an application through the batch
- **THEN** the response is 403, nothing in the batch is written, and the value stays unchanged

#### Scenario: Write with write role

- **WHEN** Kurt, holding the write role `enterprise-architect`, writes `investmentCost` = 120
- **THEN** the value is stored and its audit change is encrypted

#### Scenario: Restricted value kept

- **WHEN** Anna writes the description of the same application without naming `investmentCost`
- **THEN** the entity is written and `investmentCost` keeps its value

#### Scenario: Connection-scoped value in scope

- **WHEN** Michael writes `securityClassification` of "ERP Warehouse", which is assigned to his element
- **THEN** the value is stored and its audit change is encrypted

#### Scenario: Connection-scoped property

- **WHEN** Michael writes `securityClassification` of "CRM Sales", or of a new entity without assignment
- **THEN** the response is 403 and nothing in the batch is written
