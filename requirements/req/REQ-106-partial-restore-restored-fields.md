---
id: REQ-106
title: Partial Restore restoredFields Log
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-15
author: requirements-engineer
references:
  use_cases:
    - UC-16
  business_objects:
    - entity
    - entity-version
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-106: Partial Restore restoredFields Log

## Statement

For every partial restore, the new EntityVersion entry MUST contain the exact list of restored fields as `restoredFields`, by their keys: `name`, `description`, `isLogical` or the ID of a property definition (no dot notation, owner decision 2026-11-14). A partial restore without selected fields MUST be rejected. In the timeline (UC-14), the entry MUST be readable as "Partially restored from vN (label1, label2)".

## Rationale

Without a `restoredFields` log, it is not recognizable in the timeline what was actually changed in a partial restore — the audit trail would be incomplete and unusable for compliance purposes.

## Acceptance Criteria

**AC1** (restoredFields in the version entry):

- When: a partial restore is executed with `description` and the property `owner`
- Then: the new EntityVersion entry contains `restoredFields` with `description` and the ID of `owner`

**AC2** (Reject empty selection):

- When: no fields were selected and the confirm button is clicked
- Then: the button is disabled; an API call with empty `restoredFields` is rejected with HTTP 422

**AC3** (Timeline readability):

- When: the timeline is displayed after a partial restore
- Then: the entry shows "Partially restored from v4 (Description, Owner)"

## Dependencies

- **Prerequisites**: REQ-102 (audit snapshot), REQ-103 (atomic transaction), REQ-105 (field selection)
- **Consequences**: none

## Implementation Notes

- `entity_version_headers.restored_fields TEXT[]`, at least one entry, only with `restored_from_version` (V33, change us-104-partial-restore)
- API: `restoreEntity` with `fields`; an empty or unknown selection gives 422 `validation-failed` naming `fields`
- Timeline display: the labels of the restored fields; a property the reader may not read is restricted, a hidden one left out

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-15 | Change us-104-partial-restore | Realigned: field keys instead of dot notation, labels in the timeline, storage in the version header (statement, AC1, AC3, implementation notes) |
