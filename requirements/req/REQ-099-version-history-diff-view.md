---
id: REQ-099
title: Version history field-by-field diff
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

# REQ-099: Version history field-by-field diff

## Statement

When a version is clicked, the system MUST display a field-by-field diff between this version and its successor (before/after values, color-highlighted: red=removed, green=added). Properties MUST be compared as individual fields (dot notation), not as a JSON blob. The system MUST also enable the direct comparison of two arbitrarily selected versions (cumulative diff).

## Rationale

A JSON blob diff is unreadable for business users. A field-by-field diff allows changes to be recognized quickly without technical background knowledge and makes the timeline usable for EA practitioners.

## Acceptance Criteria

**AC1** (Only changed fields):
- When: a version is clicked
- Then: the diff shows only the changed fields; identical fields are hidden

**AC2** (Dot notation for properties):
- When: `properties.owner` was changed
- Then: the entry appears as "properties.owner: 'Kurt' → 'Michael'"

**AC3** (Cumulative diff):
- When: versions v2 and v7 are selected for comparison
- Then: the system shows the cumulative diff over all 5 transitions (v2→v3, …, v6→v7)

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-100 (snapshots)
- **Downstream effects**: REQ-101 (full restore starts from the diff view)

## Implementation Notes

- Diff algorithm: deep compare of two snapshot JSONs with dot-notation flattening
- Color highlighting: CSS classes for red (removed) and green (added)
- Cumulative diff: stepwise application of the diff transitions to the base snapshot

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
