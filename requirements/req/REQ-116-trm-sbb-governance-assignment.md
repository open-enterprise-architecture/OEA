---
id: REQ-116
title: SBB Governance Assignment per TRM Category
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-116: SBB Governance Assignment per TRM Category

## Statement

The system MUST allow setting `preferredStandard` (single selection of an SBB), `acceptedAlternatives` (multi-select SBBs), and `deprecatedOptions` (multi-select SBBs) for every TRM category (including `scope=imported`). An SBB MUST NOT appear in more than one of the three lists within the same category (mutually exclusive). When `preferredStandard` changes, the system MUST mark all entities in this TRM category whose `instanceOfSBBId` does not match the new `preferredStandard` in the UI with the note "Deviation from TRM standard".

## Rationale

The SBB governance assignment is the core of the technology standards mechanism. Without it, the TRM is an empty taxonomy with no steering effect on the current-state landscape.

## Acceptance Criteria

**AC1** (Set preferredStandard and deviation note):

- When: `preferredStandard=PostgreSQL 17` is set in the category "Database Management"
- Then: all entities in this category with a different `instanceOfSBBId` show the note "Deviation from TRM standard"

**AC2** (Mutually exclusive):

- When: the same SBB is to be set in both `preferredStandard` and `acceptedAlternatives`
- Then: the system responds with HTTP 422 "SBB may only appear in one list"

**AC3** (Imported category editable):

- When: a TRM category with `scope=imported` is opened
- Then: SBB assignments are editable; name and parent category are locked

## Dependencies

- **Prerequisites**: REQ-108 (SBBs), REQ-115 (TRM categories), REQ-111 (scope protection allows SBB assignment despite the imported category)
- **Consequences**: REQ-119 (standards drift analysis uses preferredStandard), REQ-120 (diversity analysis)

## Implementation Notes

- Mutually-exclusive validation: backend checks all three lists for overlaps before saving
- Deviation note: calculation via JOIN on the entity table; asynchronous update possible after a `preferredStandard` change
- UI: three separate dropdown fields per category; SBBs already in one list are grayed out in the others

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
