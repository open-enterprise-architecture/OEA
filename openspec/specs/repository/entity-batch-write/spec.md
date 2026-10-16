# repository/entity-batch-write Specification

## Purpose

Defines the batch write of entities (`POST /entities/batch`) through which external systems and import scripts populate the repository: the size limit, the strategies by `externalId`, atomicity and the result per item. Connections, `dryRun` and service accounts follow with the remaining acceptance criteria of US-141.

## Requirements

### Requirement: Batch write by a person with entity:write

The system SHALL accept a batch of entities from a principal holding `entity:write` and reject it otherwise without writing anything.

Trace: REQ-154, ADR-032, US-141 AC1

#### Scenario: Batch without permission

- **WHEN** a person without `entity:write` sends a batch
- **THEN** the request is rejected with 403 and no entity is written

### Requirement: Batch size limit

The system SHALL reject a batch with more entities than the configured limit with 422 without processing any item. The limit SHALL default to 500 and SHALL NOT be configurable above 500.

Trace: REQ-154 AC4, US-141 (technical notes)

#### Scenario: Batch above the limit

- **WHEN** a batch with 501 entities is sent
- **THEN** the request is rejected with 422 and no entity is written

### Requirement: Strategies by externalId

The system SHALL identify an existing entity by its `externalId`, which is unique in the instance. With strategy `upsert` (default) it SHALL create an entity whose `externalId` is unknown and update the existing one otherwise; with `create-only` an existing `externalId` SHALL be an item error; with `skip-existing` an existing entity SHALL be left unchanged and reported as `skipped`. An entity without `externalId` SHALL always be created.

Trace: REQ-153, REQ-154, US-141 AC1, ADR-026

#### Scenario: Upsert of a repository scan

- **WHEN** 200 `Application Component` entities with `externalId` are sent with strategy `upsert`, of which 150 are new and 50 exist with other names
- **THEN** the summary reports 150 created and 50 updated, the 50 entities carry the new names, and no `externalId` exists twice

#### Scenario: Repeated upsert

- **WHEN** the same batch is sent a second time unchanged
- **THEN** the summary reports all items as unchanged and no entity is created

#### Scenario: Create-only with an existing externalId

- **WHEN** a batch with strategy `create-only` contains an `externalId` that exists
- **THEN** that item has status `error` with the code `EXTERNAL_ID_EXISTS` and the existing entity is unchanged

#### Scenario: Skip-existing

- **WHEN** a batch with strategy `skip-existing` contains one existing and one new `externalId`
- **THEN** the existing entity is unchanged and reported as `skipped`, the new one is created

### Requirement: Merge of an update and duplicates

On update, fields and properties not contained in the item SHALL stay unchanged and an empty property value SHALL remove the value. An `externalId` that occurs more than once in a batch SHALL be an error of every later occurrence.

Trace: REQ-153, ADR-026, US-141 AC1 ("no duplicates")

#### Scenario: Partial update by a source

- **WHEN** an existing entity with a `Status` value is upserted with a new name and without properties
- **THEN** the entity carries the new name and keeps its `Status` value

#### Scenario: Duplicate externalId in a batch

- **WHEN** a batch contains the same new `externalId` twice
- **THEN** the first item is created and the second has status `error` with the code `DUPLICATE_EXTERNAL_ID`

### Requirement: Atomicity and results per item

With `atomic=false` (default) the system SHALL write every valid item independently of the others and answer 200 with the status of every item (`created`, `updated`, `unchanged`, `skipped` or `error`), its index, ID, `externalId`, errors and warnings, and a summary of the counts. With `atomic=true` it SHALL write either all items or none and answer 422 with the errors of all faulty items when one item fails.

Trace: REQ-154 AC1, error cases, US-141 AC1

#### Scenario: Partial errors without atomicity

- **WHEN** 100 new entities are sent with `atomic=false`, 5 of them without a value for a mandatory property
- **THEN** the response is 200 with 95 created and 5 errors, and each faulty item has status `error` with the code `MANDATORY_MISSING`

#### Scenario: Error in an atomic batch

- **WHEN** the same 100 entities are sent with `atomic=true`
- **THEN** the response is 422, it lists the errors of the 5 faulty items with their index, and no entity is written

### Requirement: Parts of the batch contract not supported yet

Until the related acceptance criteria are implemented, the system SHALL reject with 422, without writing anything, a batch that contains connections or is sent with `dryRun=true`, and SHALL reject an item that sets `parentId` or `lifecycle` as an item error.

Trace: US-141 AC2, AC3 (follow later), ADR-021

#### Scenario: Batch with connections

- **WHEN** a batch contains at least one connection
- **THEN** the request is rejected with 422 and no entity is written
