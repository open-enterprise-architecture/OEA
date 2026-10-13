---
id: REQ-100
title: Historical entity state as an immutable snapshot
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

The system MUST make the complete entity state of every historical version retrievable (from `entity_versions.snapshot`). The complete state MUST be expandable in the UI ("Show complete state vN") and contain all fields including `propertyDefinitions`. The snapshots MUST be immutable — no deleting, no editing.

## Rationale

The diff alone is not sufficient for compliance evidence. Sometimes it must be proven how an entity looked completely at a certain point in time — e.g., for audits or regulatory evidence.

## Acceptance Criteria

**AC1** (Complete state expandable):

- When: "Show complete state v4" is clicked in the timeline
- Then: all fields of the snapshot, including empty properties, are displayed

**AC2** (Deletion rejected):

- When: a DELETE request is sent to `/entity-versions/{id}`
- Then: the API responds with HTTP 405 Method Not Allowed

**AC3** (Editing rejected):

- When: a PUT request is sent to `/entity-versions/{id}`
- Then: the API responds with HTTP 405 Method Not Allowed

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-102 (snapshot creation)
- **Downstream effects**: REQ-099 (diff), REQ-101 (full restore)

## Implementation Notes

- `entity_versions.snapshot` as JSONB in PostgreSQL; not normalized
- HTTP 405 instead of 422 — the method is not allowed in general, not just in an individual case
- `propertyDefinitions` are frozen at the time of snapshot creation

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
