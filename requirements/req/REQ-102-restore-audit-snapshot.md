---
id: REQ-102
title: Audit snapshot before every restore
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

# REQ-102: Audit snapshot before every restore

## Statement

Before every restore (full UC-15 or partial UC-16), the system MUST save the current entity state as an immutable EntityVersion snapshot. The new EntityVersion entry MUST contain `restoredFromVersion` (source version). This snapshot MUST be visible in the timeline (UC-14) as a restore entry.

## Rationale

Every restore must itself be traceable. Without an audit snapshot, a restore would invisibly erase the current state — which breaks the audit trail and violates compliance requirements.

## Acceptance Criteria

**AC1** (Snapshot entry after restore):

- When: a restore is executed
- Then: a new EntityVersion entry with `restoredFromVersion=N` is visible in the timeline

**AC2** (Marking in timeline):

- When: the timeline is displayed after a restore
- Then: the new entry shows the marking "Restored from vN"

**AC3** (Snapshot before restore retained):

- When: the restore is completed
- Then: the state BEFORE the restore is present as an independent snapshot in `entity_versions`

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-103 (atomic transaction)
- **Downstream effects**: REQ-101 (full restore), REQ-104–REQ-106 (partial restore)

## Implementation Notes

- Snapshot creation as the first step within the transaction (before the entity update)
- Add `restoredFromVersion` field to the `entity_versions` table
- Timeline display: icon/label for restore entries

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
