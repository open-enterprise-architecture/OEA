## ADDED Requirements

### Requirement: Deltas of a blocked entity

Capturing a delta `modified` or `retiring` of an entity with an open issue SHALL be rejected with 409 of type `entity-blocked` naming the entity, its open issues and their solution. Deltas captured before the issue arose SHALL stay editable and removable.

Trace: US-153 AC1, owner decision 2026-11-17

#### Scenario: Modify a blocked entity

- **WHEN** Michael captures a delta `modified` of "Salesforce", which has an open issue
- **THEN** the answer is 409 `entity-blocked`, and no delta is stored

#### Scenario: Retire a blocked entity

- **WHEN** Michael captures a delta `retiring` of "Salesforce"
- **THEN** the answer is 409 `entity-blocked`

#### Scenario: Entity without open issues

- **WHEN** the issue of "Salesforce" is resolved, and Michael captures the delta `modified`
- **THEN** the delta is stored

### Requirement: Snapshots in the checks of deltas

The checks of a delta SHALL treat the snapshots of its solution as part of the metamodel: a property or enumeration value that a snapshot names SHALL be accepted in a delta of that solution and rejected in a delta of any other solution.

Trace: US-154 AC3

#### Scenario: Snapshot property in the own solution

- **WHEN** "CRM Replacement" kept "Cost Center", and Michael sets it in a delta of "CRM Replacement"
- **THEN** the delta is stored

#### Scenario: Snapshot property in another solution

- **WHEN** Michael sets "Cost Center" in a delta of "ERP Extension", which has no snapshot
- **THEN** the answer is 422 naming the property
