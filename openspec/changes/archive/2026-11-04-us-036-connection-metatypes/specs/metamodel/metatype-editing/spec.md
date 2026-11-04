# Spec Delta

## MODIFIED Requirements

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
