## ADDED Requirements

### Requirement: Restore of a blocked entity

A full or partial restore of an entity with an open issue of a solution SHALL be rejected with 409 of type `entity-blocked` naming the open issues and their solution, and nothing changes. The preview SHALL stay readable.

Trace: US-153 AC1

#### Scenario: Restore a blocked entity

- **WHEN** an architect restores "Salesforce", which has an open issue, to version 1
- **THEN** the answer is 409 `entity-blocked`, and "Salesforce" keeps its version

#### Scenario: Restore after the resolution

- **WHEN** the issue is resolved, and the architect restores "Salesforce"
- **THEN** the restore runs
