---
id: REQ-103
title: Restore as an atomic transaction
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

# REQ-103: Restore as an atomic transaction

## Statement

Snapshot backup, entity update and writing of the new EntityVersion entry MUST occur in a single DB transaction for every restore (UC-15 and UC-16). In case of a technical error, a complete rollback MUST occur; no inconsistent state (snapshot without update or update without snapshot) is permissible.

## Rationale

A partial commit would produce either a lost audit snapshot or an invalid entity. Both states permanently damage the audit trail and cannot be detected or repaired automatically.

## Acceptance Criteria

**AC1** (Rollback on DB error):
- When: a simulated DB error occurs after the snapshot is written
- Then: a complete rollback occurs; entity is unchanged; no snapshot in `entity_versions`

**AC2** (Successful restore):
- When: the restore completes successfully
- Then: snapshot, entity update and version entry are all present and consistent

## Dependencies

- **Prerequisites**: none (fundamental technical requirement)
- **Downstream effects**: REQ-101 (full restore), REQ-102 (audit snapshot), REQ-106 (partial restore)

## Implementation Notes

- Spring `@Transactional` with explicit rollback on all exceptions
- Isolation at least `REPEATABLE READ`
- Order within the transaction: (1) snapshot, (2) entity update, (3) EntityVersion entry

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
