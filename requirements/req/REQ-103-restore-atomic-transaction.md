---
id: REQ-103
title: Restore as an atomic transaction
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-15
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

# REQ-103: Restore as an atomic transaction

## Statement

The entity update, the new EntityVersion entry with its history rows and the audit event MUST be written in a single DB transaction for every restore (UC-15 and UC-16). In case of a technical error, a complete rollback MUST occur; no inconsistent state (version without update, update without version, or either without audit event) is permissible. The state before the restore is the previous version and is never changed (REQ-102).

## Rationale

A partial commit would produce either a lost audit snapshot or an invalid entity. Both states permanently damage the audit trail and cannot be detected or repaired automatically.

## Acceptance Criteria

**AC1** (Rollback on DB error):

- When: a simulated error occurs after the entity was written (e.g. writing the audit event fails)
- Then: a complete rollback occurs; the entity is unchanged; no new version and no audit event exist

**AC2** (Successful restore):

- When: the restore completes successfully
- Then: entity update, version entry with `restoredFromVersion` and audit event `entity.restored` are all present and consistent

## Dependencies

- **Prerequisites**: none (fundamental technical requirement)
- **Downstream effects**: REQ-101 (full restore), REQ-102 (audit snapshot), REQ-106 (partial restore)

## Implementation Notes

- Spring `@Transactional` with explicit rollback on all exceptions
- Isolation at least `REPEATABLE READ`
- Order within the transaction: (1) lock the entity and check `If-Match`, (2) entity update with the version entry and its history rows, (3) audit event `entity.restored` (change us-101-entity-restore)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-15 | Change us-101-entity-restore | Realigned: no separate snapshot is written, the transaction covers entity update, version entry and audit event (statement, AC1, AC2, implementation notes; owner decision 2026-11-15) |
