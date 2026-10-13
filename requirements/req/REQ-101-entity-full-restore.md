---
id: REQ-101
title: Full restore of an entity to an earlier state
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-15
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-101: Full restore of an entity to an earlier state

## Statement

The system MUST enable restoring an ArchitectureEntity to any earlier state. Before execution, a confirmation dialog MUST be displayed that contains the diff (current vs. target version) and the optional change reason. Immutable fields (`id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy`) MUST NOT be changed in the process.

## Rationale

Faulty changes happen. Without a restore option, changes have to be reverted manually, which is error-prone and leaves no audit trail.

## Acceptance Criteria

**AC1** (Restore to earlier state):

- When: an entity is restored to version v3
- Then: `name`, `description` and `properties` are set to v3 values; `id` is unchanged

**AC2** (Confirmation dialog with diff):

- When: the restore is initiated
- Then: the confirmation dialog shows which fields differ between the current state and the target version

**AC3** (Cancel without change):

- When: the dialog is canceled
- Then: the entity remains unchanged

## Dependencies

- **Prerequisites**: REQ-102 (snapshot backup before restore), REQ-103 (atomic transaction)
- **Downstream effects**: REQ-102 (audit snapshot after restore)

## Implementation Notes

- Immutable fields: `id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy` — ignore from snapshot
- Diff calculation as in REQ-099 (dot notation, field by field)
- Persist the change reason in `EntityVersion.changeReason`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
