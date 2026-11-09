# Delta for repository/entity-write

## MODIFIED Requirements

### Requirement: Necessity of properties

A mandatory property without value SHALL be an error, also for an entity marked as missing it; a property the writer cannot change SHALL NOT be checked. A property with necessity `warning` without value SHALL produce a warning. On create, the default value of a mapping SHALL apply to a property without value. Setting the value SHALL clear the mark. A writer can change a public property, and a `role-restricted` property with one of its write roles.

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

## ADDED Requirements

### Requirement: Writing restricted properties

A value of a `role-restricted` property SHALL be written or removed only by a principal holding one of its write roles. A batch or connection that names such a property without that role SHALL be rejected with 403 as a whole and the denial audited. Values of `connection-scoped` properties SHALL be rejected with `RESTRICTED_PROPERTY_NOT_SUPPORTED`. Writing an entity without naming a restricted property SHALL keep its value.

Trace: US-126 AC3, REQ-126, REQ-160 AC4, owner decision 2026-11-08

#### Scenario: Write attempt without write role

- **WHEN** Anna, without a write role, writes `investmentCost` of an application through the batch
- **THEN** the response is 403, nothing in the batch is written, and the value stays unchanged

#### Scenario: Write with write role

- **WHEN** Kurt, holding the write role `enterprise-architect`, writes `investmentCost` = 120
- **THEN** the value is stored and its audit change is encrypted

#### Scenario: Restricted value kept

- **WHEN** Anna writes the description of the same application without naming `investmentCost`
- **THEN** the entity is written and `investmentCost` keeps its value

#### Scenario: Connection-scoped property

- **WHEN** an entity is written with a value for a `connection-scoped` property
- **THEN** the entity is rejected with the error code `RESTRICTED_PROPERTY_NOT_SUPPORTED`

## REMOVED Requirements

### Requirement: Restricted properties not writable yet

**Reason**: Replaced by "Writing restricted properties": audit values of restricted properties are now encrypted (REQ-160 AC4), so holders of a write role may write them.

**Migration**: `role-restricted` values are written by holders of a write role; `connection-scoped` values stay rejected until change 3 of S10.
