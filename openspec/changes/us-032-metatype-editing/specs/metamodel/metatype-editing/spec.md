# Spec Delta

## Purpose

Defines how metamodel administrators create, change and delete the organization's own metatypes, and why built-in and imported metatypes stay read-only.

## ADDED Requirements

### Requirement: Creating a metatype

A principal with `metamodel:write` SHALL create a metatype with name, abbreviation and sort order; it SHALL get `scope=organization` in the instance configuration, be audited as `metamodel.metatype-created` with the actor, and be usable for entities at once. A name already used by any metatype, compared case-insensitively, SHALL be rejected with 409.

Trace: US-032 AC1, US-032 AC2, US-032 AC3, REQ-032 AC1, REQ-032 AC2, ADR-002

#### Scenario: SecurityZone created

- **WHEN** a metamodel administrator creates the metatype `SecurityZone`
- **THEN** it is listed with `scope=organization`, entities of `SecurityZone` can be written through the batch API, and an audit event `metamodel.metatype-created` names the administrator

#### Scenario: Name of an imported metatype

- **WHEN** a metamodel administrator creates a metatype named `application component`
- **THEN** the request is rejected with 409 and problem type `name-taken`, and no metatype is stored

#### Scenario: Without permission

- **WHEN** a person holding only `enterprise-architect` creates a metatype
- **THEN** the request is rejected with 403

### Requirement: Changing and deleting a metatype

A metatype with `scope=organization` SHALL be changed and deleted by a principal with `metamodel:write`, audited as `metamodel.metatype-updated` and `metamodel.metatype-deleted`. Deleting it SHALL be rejected with 409 and problem type `in-use` naming the number of its entities while any exist, including deleted ones, or while a catalog uses it.

Trace: REQ-032 AC3, REQ-032 AC4

#### Scenario: Metatype with entities

- **WHEN** a metamodel administrator deletes `SecurityZone`, which has five entities
- **THEN** the request is rejected with 409 and problem type `in-use` stating five entities, and the metatype stays

### Requirement: Read-only metatypes

Built-in and imported metatypes SHALL NOT be changed or deleted; such a request SHALL be rejected with 409 and problem type `read-only-metatype`. Mapping organization properties to them stays possible (`metamodel/property-mappings`).

Trace: REQ-032 AC5, ADR-002, owner decision 2026-10-30

#### Scenario: Imported metatype

- **WHEN** a metamodel administrator renames the imported metatype `Application Component`
- **THEN** the request is rejected with 409 and problem type `read-only-metatype`, and the metatype is unchanged
