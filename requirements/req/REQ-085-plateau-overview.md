---
id: REQ-085
title: Plateau overview grouped by status
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-17
author: requirements-engineer
references:
  use_cases:
    - UC-11
  business_objects:
    - plateau
    - entity
    - solution
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-085: Plateau overview grouped by status

## Statement

The system MUST display plateaus in a structured overview grouped by status: current baseline (highlighted), target plateaus (with planned `validFrom`), realized plateaus (collapsible, chronologically descending). Each plateau shows name, status, `validFrom`/`validTo` and the number of linked solutions.

## Rationale

Without a structured overview, Kurt cannot quickly see which state is currently productive and which target states are planned. Grouping by status makes the planning state visible at a glance.

## Acceptance Criteria

**AC1** (Grouped overview):

- When: the plateau overview is opened and baseline, target and realized plateaus exist
- Then: the baseline is highlighted, targets are shown with `validFrom` and realized plateaus are displayed as a collapsible section

**AC2** (Empty target section):

- When: no target plateaus exist
- Then: the target section is displayed with the note "No target plateau planned yet"

## Dependencies

- **Prerequisites**: REQ-084 (plateau must be creatable)
- **Downstream effects**: REQ-086 (go-live transition is reachable via the overview)

## Implementation Notes

- Pagination for realized plateaus if more than 20 entries exist
- Solution count as an aggregated counter per plateau (DB query with JOIN)
- Realigned by the OpenSpec change `us-084-plateaus`: the overview is the screen SCR-065 at `/plateaus` (`listPlateaus`); transition plateaus are listed with the target plateaus; the realized plateaus are not paged yet, since they only arise with the plateau go-live

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-084-plateaus | Realigned: SCR-065, transition plateaus with the targets |
