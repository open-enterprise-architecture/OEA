---
id: REQ-098
title: Version history as a chronological timeline
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
  adrs:
    - ADR-022
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-098: Version history as a chronological timeline

## Statement

The system MUST display a chronological version history (timeline) for every ArchitectureEntity. For each version, the following information MUST be visible: version number, timestamp (UTC), name of the person who made the change and the changed fields (realigned 2026-11-14: a change reason is a later extension; no write path records one yet). The timeline MUST be filterable by time period and actor. For more than 50 versions, pagination MUST be applied.

## Rationale

Without a traceable change history, an architecture repository is not a trustworthy system of record. Compliance requirements demand gapless audit trails that prove for every EA-relevant state who changed what and when.

## Acceptance Criteria

**AC1** (Timeline with versions):

- When: an entity with 5 versions is opened
- Then: the timeline shows 5 entries, newest first

**AC2** (Filter by actor):

- When: the timeline is filtered by actor "Michael"
- Then: only versions created by Michael are displayed

**AC3** (Entity without history):

- When: an entity without a change history is opened
- Then: the page shows "No changes since creation. Created on [date] by [person]."

## Dependencies

- **Prerequisites**: REQ-102 (EntityVersion snapshots must exist)
- **Downstream effects**: REQ-099 (diff view), REQ-100 (snapshot retrieval), REQ-101 (full restore)

## Implementation Notes

- `GET /entities/{id}/history` (`getEntityHistory`, `entity:read`); the changed fields come from `entity_system_field_history` and `entity_property_value_history` of the version, without values (values follow with REQ-099)
- Paged by cursor like the delta list of a solution: page size 1 to 200, default 50; `nextCursor` with `page` + 1; the page SCR-026 appends the next page with "Load more" (realigned 2026-11-14)
- Filter by time period via `from`/`to` (inclusive timestamps; `from` after `to` answers 400); actor filter via `actorId`; the response names the creation and every person who made a version, independent of the filters
- A field the reader may not read under REQ-160 never shows a value; whether it appears as restricted or is hidden is configurable per authorization role and metatype (`/admin/history-visibility`, `visibility:configure`; the more open setting of several roles applies, default "restricted"; owner decision 2026-11-14)
- Timestamps are delivered in UTC; the frontend formats them in the local time zone
- Implemented by: OpenSpec change `us-098-entity-history`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-14 | Change us-098-entity-history | Implemented: the page SCR-026 opened from the catalog, cursor paging, the history visibility per role and metatype; the change reason dropped from the statement as a later extension, AC3 realigned to the page (owner decisions 2026-11-14) |
