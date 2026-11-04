# Spec Delta

## MODIFIED Requirements

### Requirement: Parts of the batch contract not supported yet

Until the related acceptance criteria are implemented, the system SHALL reject with 422, without writing anything, a batch that is sent with `dryRun=true`, and SHALL reject an item that sets `parentId` or `lifecycle` as an item error. Connections are written as described in `repository/connection-write`.

Trace: US-141 AC2 (us-036-connection-metatypes), AC3 (follows later), ADR-021

#### Scenario: Batch with connections

- **WHEN** a batch contains entities and connections between them
- **THEN** the entities and the connections are written, and the batch is no longer rejected for its connections

#### Scenario: Dry run

- **WHEN** a batch is sent with `dryRun=true`
- **THEN** the request is rejected with 422 and no entity is written
