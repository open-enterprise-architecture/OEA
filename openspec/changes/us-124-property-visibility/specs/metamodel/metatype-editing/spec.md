# Delta for metamodel/metatype-editing

## ADDED Requirements

### Requirement: Scoping type cannot be deleted

A connection metatype that is the scoping type of a property definition SHALL NOT be deleted. The request SHALL be rejected with 409 `in-use` until no property uses it as scoping type.

Trace: REQ-124, REQ-127

#### Scenario: Delete the scoping type

- **GIVEN** `DomainAssignment` has no connections and is the scoping type of `securityClassification`
- **WHEN** a metamodel administrator deletes `DomainAssignment`
- **THEN** the request is rejected with 409 `in-use`, and the metatype stays
