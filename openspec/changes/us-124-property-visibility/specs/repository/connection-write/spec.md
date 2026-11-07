# Delta for repository/connection-write

## ADDED Requirements

### Requirement: Connections of a scoping type

While a connection metatype is the scoping type of a property definition, writing a connection of that metatype SHALL need `visibility-scope:assign` besides `entity:write`. Without it the request SHALL be rejected with 403 and audited, a batch as a whole.

Trace: REQ-161 AC1, REQ-161 AC4, REQ-127, ADR-032

#### Scenario: Architect creates a scoping connection

- **GIVEN** `DomainAssignment` is the scoping type of `securityClassification`
- **WHEN** an architect with `entity:write` but without `visibility-scope:assign` creates a `DomainAssignment` connection
- **THEN** the request is rejected with 403, no connection exists, and the denial is audited

#### Scenario: Scoping connection in a batch

- **WHEN** a service account with `integration-writer` sends a batch with an entity and a `DomainAssignment` connection
- **THEN** the batch is rejected with 403, and neither the entity nor the connection is written

#### Scenario: With the permission

- **WHEN** a person holding `enterprise-architect` and `security-admin` creates the `DomainAssignment` connection
- **THEN** the connection is created

#### Scenario: Type no longer used for scoping

- **WHEN** `securityClassification` is set back to `public` and an architect then creates a `DomainAssignment` connection
- **THEN** the connection is created with `entity:write` alone
