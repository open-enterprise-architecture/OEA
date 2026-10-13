---
id: REQ-101
title: Full restore of an entity to an earlier state
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
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

The system MUST enable restoring an ArchitectureEntity to any earlier state. Before execution, a confirmation dialog MUST be displayed that contains the diff (current vs. target version) and the optional change reason. Immutable fields (`id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy`) MUST NOT be changed in the process. Properties the acting principal is not permitted to write MUST NOT be restored and MUST be reported as "not restored (insufficient permission)"; the diff in the confirmation dialog MUST NOT reveal values of properties the principal is not permitted to read (REQ-160, REQ-162).

## Rationale

Faulty changes happen. Without a restore option, changes have to be reverted manually, which is error-prone and leaves no audit trail.

Security review SEC-04 (2026-10-13): a restore that rewrites every property lets a principal change values of restricted properties they may neither read nor write, e.g. resetting a security classification from `restricted` to `public`.

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

**AC4** (Write permission respected):

- Given: a principal without write permission on property P of the entity, and target version v3 with a different value of P
- When: the principal restores the entity to v3
- Then: P keeps its current value, all other permitted fields are restored, and the response and the dialog list P as "not restored (insufficient permission)"

**AC5** (Read permission respected in the diff):

- Given: a principal without read permission on property P
- When: the confirmation dialog is shown
- Then: no current or historical value of P is displayed or contained in the response (`redacted`)

## Dependencies

- **Prerequisites**: REQ-102 (snapshot backup before restore), REQ-103 (atomic transaction), REQ-160 (visibility on all read channels), REQ-162 (write protection on all write paths)
- **Downstream effects**: REQ-102 (audit snapshot after restore)

## Implementation Notes

- Immutable fields: `id`, `entityTypeId`, `sourceEntityId`, `targetEntityId`, `createdAt`, `createdBy` — ignore from snapshot
- Diff calculation as in REQ-099 (dot notation, field by field)
- Persist the change reason in `EntityVersion.changeReason`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-04: restore respects property write and read permissions (AC4, AC5) |
