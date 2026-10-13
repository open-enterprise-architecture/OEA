---
id: REQ-106
title: Partial Restore restoredFields Log
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

For every partial restore, the new EntityVersion entry MUST contain the exact list of restored fields in dot notation as `restoredFields` (e.g. `["description", "properties.owner"]`). A partial restore without selected fields (`restoredFields` empty) MUST be rejected. In the timeline (UC-14), the entry MUST be readable as "Partially restored from vN (field1, field2)".

## Rationale

Without a `restoredFields` log, it is not recognizable in the timeline what was actually changed in a partial restore — the audit trail would be incomplete and unusable for compliance purposes.

## Acceptance Criteria

**AC1** (restoredFields in the version entry):
- When: a partial restore is executed with `description` and `properties.owner`
- Then: the new EntityVersion entry contains `restoredFields=["description","properties.owner"]`

**AC2** (Reject empty selection):
- When: no fields were selected and the confirm button is clicked
- Then: the button is disabled; an API call with empty `restoredFields` is rejected with HTTP 422

**AC3** (Timeline readability):
- When: the timeline is displayed after a partial restore
- Then: the entry shows "Partially restored from v4 (description, properties.owner)"

## Dependencies

- **Prerequisites**: REQ-102 (audit snapshot), REQ-103 (atomic transaction), REQ-105 (field selection)
- **Consequences**: none

## Implementation Notes

- `restoredFields` array as a JSONB column in the `entity_versions` table
- API validation: `restoredFields` must not be empty; HTTP 422 for an empty array
- Timeline display: field list as a collapsible tooltip for long lists

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
