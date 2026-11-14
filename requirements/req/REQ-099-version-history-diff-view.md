---
id: REQ-099
title: Version history field-by-field diff
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

# REQ-099: Version history field-by-field diff

## Statement

When a version is clicked, the system MUST display a field-by-field diff between this version and its predecessor (before/after values, color-highlighted: red=removed, green=added). Each property MUST be compared as a field of its own, named by its label, not as a JSON blob. The system MUST also enable the direct comparison of two arbitrarily selected versions as the net diff of their states.

## Rationale

A JSON blob diff is unreadable for business users. A field-by-field diff allows changes to be recognized quickly without technical background knowledge and makes the timeline usable for EA practitioners.

## Acceptance Criteria

**AC1** (Only changed fields):

- When: a version is clicked
- Then: the diff shows only the changed fields; identical fields are hidden

**AC2** (Each property a field of its own):

- When: the property `owner` was changed
- Then: the entry appears as "Owner: Kurt → Michael", with the label of the property

**AC3** (Net diff of two versions):

- When: versions v2 and v7 are selected for comparison
- Then: the system shows the fields whose value differs between the state of v2 and the state of v7; a field changed and changed back between them is not shown

## Dependencies

- **Prerequisites**: REQ-098 (timeline), REQ-100 (snapshots)
- **Downstream effects**: REQ-101 (full restore starts from the diff view)

## Implementation Notes

- Diff: both states are reconstructed from the history tables (ADR-022, see REQ-100) and compared field by field; the diff of version N is the comparison of N−1 with N (`compareEntityVersions`)
- Color highlighting: red and struck through for the old value, green for the new value; the strike-through and screen reader texts keep the meaning without color
- Values the reader may not read are never shown; such a field appears as "Restricted" or is hidden by the history visibility (REQ-098, REQ-160)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-14 | Change us-099-version-diff | Implemented: the predecessor instead of the successor, labels instead of the dot notation, the net diff of two states instead of the cumulative transitions (owner decisions 2026-11-14) |
