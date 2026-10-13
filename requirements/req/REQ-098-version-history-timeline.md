---
id: REQ-098
title: Version history as a chronological timeline
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

# REQ-098: Version history as a chronological timeline

## Statement

The system MUST display a chronological version history (timeline) for every ArchitectureEntity. For each version, the following information MUST be visible: version number, timestamp (UTC), name of the person who made the change (linked to profile), changed fields (from `EntityVersion.changedFields`) and optional change reason. The timeline MUST be filterable by time period and actor. For more than 50 versions, pagination MUST be applied.

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
- Then: the tab shows "No changes since creation. Created on [date] by [person]."

## Dependencies

- **Prerequisites**: REQ-102 (EntityVersion snapshots must exist)
- **Downstream effects**: REQ-099 (diff view), REQ-100 (snapshot retrieval), REQ-101 (full restore)

## Implementation Notes

- Pagination for > 50 versions; page size configurable
- Filter by time period via `from`/`to` query parameters; actor filter via `actorId`
- Always display timestamps in UTC; formatting in the frontend according to the local timezone offset

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
