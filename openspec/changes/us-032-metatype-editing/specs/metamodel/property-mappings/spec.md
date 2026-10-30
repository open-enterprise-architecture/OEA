# Spec Delta

## Purpose

Defines how property definitions are mapped to metatypes with their necessity, including the extension of imported and built-in metatypes with organization properties.

## ADDED Requirements

### Requirement: Mapping a property to a metatype

A principal with `metamodel:write` SHALL map a property definition to a metatype with a necessity (`mandatory`, `warning`, `optional`), a valid-from date and a sort order. A property already mapped to the metatype SHALL be rejected with 409. The mapping SHALL be audited as `metamodel.mapping-created` and SHALL apply to entities written afterwards.

Trace: UC-04 step 6, REQ-032 AC1, REQ-032 AC4, REQ-149

#### Scenario: Mandatory property

- **WHEN** a metamodel administrator maps `Trust Level` as mandatory to `SecurityZone`
- **THEN** a `SecurityZone` written without `Trust Level` is rejected with 422 and code `MANDATORY_MISSING`

### Requirement: Extending imported and built-in metatypes

Organization property definitions SHALL be mapped to imported and built-in metatypes as well; the metatype and the mappings of its package stay unchanged. Mappings of the package SHALL NOT be added, changed or removed by administrators.

Trace: owner decision 2026-10-30, ADR-002

#### Scenario: Own property on an imported metatype

- **WHEN** a metamodel administrator maps the organization property `Cost Center` as optional to the imported `Application Component`
- **THEN** reading `Application Component` lists `Status` from the package and `Cost Center` with `scope=organization`

### Requirement: Removing a mapping

An organization mapping SHALL be removed by a principal with `metamodel:write` while no entity of the metatype has a value for the property; otherwise the removal SHALL be rejected with 409 and problem type `in-use`. The removal SHALL be audited as `metamodel.mapping-deleted`.

Trace: REQ-032 AC4

#### Scenario: Unused mapping removed

- **WHEN** a metamodel administrator removes the mapping of `Cost Center` from `Application Component` while no entity has a value for it
- **THEN** the mapping is gone and an audit event `metamodel.mapping-deleted` exists
