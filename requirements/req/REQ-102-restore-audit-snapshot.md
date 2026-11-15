---
id: REQ-102
title: Audit snapshot before every restore
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

# REQ-102: Audit snapshot before every restore

## Statement

Every restore (full UC-15 or partial UC-16) MUST keep the entity state before the restore as an immutable snapshot: the version before the restore stays unchanged in the version history (delta versioning, ADR-022), so its state remains reconstructable. The restore MUST write exactly one new version that contains `restoredFromVersion` (source version) and the optional reason. This version MUST be visible in the timeline (UC-14) as a restore entry.

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
- Then: the state BEFORE the restore is present unchanged as the previous version in the version history, and its complete state can be retrieved (REQ-100)

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-103 (atomic transaction)
- **Downstream effects**: REQ-101 (full restore), REQ-104–REQ-106 (partial restore)

## Implementation Notes

- No copy is written: the history stores the value before each change, so version N (the state before the restore) is reconstructed like any other version (owner decision 2026-11-15)
- `entity_version_headers.restored_from_version` and `change_reason` mark the version written by a restore (change us-101-entity-restore, V32)
- Timeline display: the badge "Restored from vN" and the reason on SCR-026

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-15 | Change us-101-entity-restore | Realigned: the version before the restore is the snapshot (delta versioning, ADR-022) instead of a copy in `entity_versions`; the restore version records `restoredFromVersion` and the reason (statement, AC3, implementation notes; owner decision 2026-11-15) |
