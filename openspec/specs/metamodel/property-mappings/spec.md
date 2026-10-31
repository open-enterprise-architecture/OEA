# metamodel/property-mappings Specification

## Purpose

Defines how property definitions are mapped to metatypes and removed again, including the extension of imported and built-in metatypes with organization properties and the marking of entities that miss a new mandatory property.

## Requirements

### Requirement: Mapping a property to a metatype

A principal with `metamodel:write` SHALL map an organization property definition to a metatype with a necessity (`mandatory`, `warning`, `optional`), a valid-from date and a sort order. A property already mapped in the base type chain of the metatype or its subtypes SHALL be rejected with 409. The mapping SHALL be audited as `metamodel.mapping-created`.

Trace: UC-04 step 6, REQ-032 AC1, REQ-149

#### Scenario: Mandatory property for new entities

- **WHEN** a metamodel administrator maps `Trust Level` as mandatory to `SecurityZone` and a new `SecurityZone` is written without it
- **THEN** the new entity is rejected with 422 and code `MANDATORY_MISSING`

### Requirement: Marking entities without a new mandatory property

A new mandatory mapping SHALL succeed regardless of existing entities and SHALL mark every entity of the metatype and its subtypes that has no value for the property; the response and the audit event SHALL state the number of marked entities. A mark SHALL be cleared when the entity gets a value or the property is removed from its metatype.

Trace: REQ-149 AC1, owner decision 2026-10-31

#### Scenario: Existing entities marked

- **WHEN** a metamodel administrator maps `Trust Level` as mandatory to `SecurityZone`, which has five entities without a value
- **THEN** the mapping is stored, the response states five marked entities, and the five entities are marked as missing `Trust Level`

### Requirement: Extending imported and built-in metatypes

Organization property definitions SHALL be mapped to imported and built-in metatypes as well; the metatype and the mappings of its package stay unchanged. Mappings of the package SHALL NOT be added, changed or removed by administrators.

Trace: owner decision 2026-10-30, ADR-002

#### Scenario: Own property on an imported metatype

- **WHEN** a metamodel administrator maps the organization property `Cost Center` as optional to the imported `Application Component`
- **THEN** reading `Application Component` lists `Status` from the package and `Cost Center` with `scope=organization`

### Requirement: Removing a mapping

A principal with `metamodel:write` SHALL remove an organization mapping at any time. The values of the property SHALL be deleted from the entities of the metatype and its subtypes, each affected entity getting a new version in its history. The removal SHALL be audited as `metamodel.mapping-deleted` with the number of affected entities.

Trace: REQ-032 AC4, owner decision 2026-10-31

#### Scenario: Mapping with values removed

- **WHEN** a metamodel administrator removes the mapping of `Cost Center` from `Application Component` while three entities have a value for it
- **THEN** the mapping is gone, the three entities have a new version without `Cost Center`, and an audit event `metamodel.mapping-deleted` names three entities
