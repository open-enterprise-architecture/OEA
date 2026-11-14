---
id: REQ-100
title: Historical entity state as an immutable snapshot
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-14
author: requirements-engineer
references:
  use_cases:
    - UC-14
  business_objects:
    - entity
    - entity-version
    - person
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-100: Historical entity state as an immutable snapshot

## Statement

The system MUST make the complete entity state of every historical version retrievable, reconstructed from the history tables (ADR-022). The complete state MUST be expandable in the UI ("Show complete state vN") and contain all fields, including the properties mapped to the metatype at the time of the version that were empty. The versions MUST be immutable — no deleting, no editing.

## Rationale

The diff alone is not sufficient for compliance evidence. Sometimes it must be proven how an entity looked completely at a certain point in time — e.g., for audits or regulatory evidence.

## Acceptance Criteria

**AC1** (Complete state expandable):

- When: "Show complete state v4" is clicked in the timeline
- Then: all fields of the snapshot, including empty properties, are displayed

**AC2** (Deletion rejected):

- When: a DELETE request is sent to `/entities/{id}/versions/{version}`
- Then: the API responds with HTTP 405 Method Not Allowed

**AC3** (Editing rejected):

- When: a PUT request is sent to `/entities/{id}/versions/{version}`
- Then: the API responds with HTTP 405 Method Not Allowed

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-102 (snapshot creation)
- **Downstream effects**: REQ-099 (diff), REQ-101 (full restore)

## Implementation Notes

- No snapshot is stored: the value of a field at version N is the value of its first history row after N, or the current value (`getEntityVersion`)
- HTTP 405 with `Allow: GET` instead of 422 — the method is not allowed in general, not just in an individual case
- The property set comes from the mappings of the metatype at the time of the version (effective date); mappings that a package re-import removed are not recorded, so an empty property of such a mapping is missing from older states

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-14 | Change us-099-version-diff | Implemented: reconstruction from the history tables instead of `entity_versions.snapshot`, the property set from the mappings at the time, the version path `/entities/{id}/versions/{version}` (owner decisions 2026-11-14) |
