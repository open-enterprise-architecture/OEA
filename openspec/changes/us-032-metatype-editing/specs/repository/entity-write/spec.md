# Spec Delta

## MODIFIED Requirements

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

A mandatory property without value that the writer can change SHALL be an error, also for an entity marked as missing it; a property the writer cannot change SHALL NOT be checked; a property with necessity `warning` without value SHALL produce a warning; an optional property without value SHALL produce no notice. When an entity is created, the default value of a mapping SHALL apply to a property without value. Setting the value SHALL clear the mark.

Trace: REQ-149 AC1, AC2, ADR-022, owner decision 2026-10-31

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

- **WHEN** an entity is written without a value for a mandatory property whose visibility mode is not `public`
- **THEN** the missing value is not reported and the entity is written
