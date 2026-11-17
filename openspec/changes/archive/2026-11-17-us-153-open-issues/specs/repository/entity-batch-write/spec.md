## ADDED Requirements

### Requirement: Updates of a blocked entity in a batch

An item of `batchWriteEntities` that updates an entity with an open issue of a solution SHALL fail with the item error `OPEN_ISSUES`, following the atomicity rules of the batch. Creating entities SHALL NOT be affected.

Trace: US-153 AC1

#### Scenario: Update of a blocked entity

- **WHEN** a batch with `atomic: false` updates "Salesforce", which has an open issue, and another entity
- **THEN** the item of "Salesforce" fails with `OPEN_ISSUES` and stays unchanged, and the other item is written

#### Scenario: Atomic batch

- **WHEN** the same batch is sent with `atomic: true`
- **THEN** nothing is written
