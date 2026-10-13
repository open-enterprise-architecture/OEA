---
id: REQ-104
title: Partial restore three-step wizard
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

# REQ-104: Partial restore three-step wizard

## Statement

The system MUST provide a three-step wizard for selective restore: step 1 general properties (`name`, `description`, `isLogical`), step 2 custom properties (all configured properties from `snapshot.propertyDefinitions`), step 3 connection overview (informational: changed/added/removed). Each step MUST be individually skippable. Steps without differences between the source and the current state MUST be skipped automatically with a note.

## Rationale

A full restore overwrites correct changes. The wizard enables surgical corrections without collateral damage and gives users full control over the restore operations.

## Acceptance Criteria

**AC1** (Source version prefilled):

- When: the wizard is started from the timeline (UC-14) for version v3
- Then: source version v3 is prefilled

**AC2** (Automatic skipping):

- When: step 1 contains no differences in general properties
- Then: step 1 is skipped automatically with the note "No differences in general properties"; wizard switches directly to step 2

**AC3** (Confirm button on empty selection):

- When: no fields were selected in any step
- Then: the confirm button is disabled (per REQ-106)

## Dependencies

- **Prerequisites**: REQ-098 (timeline as entry point), REQ-105 (field selection), REQ-106 (validation)
- **Downstream effects**: REQ-102 (audit snapshot), REQ-103 (atomic transaction)

## Implementation Notes

- Wizard steps with step indicator (1/3, 2/3, 3/3) and skip link
- Diff calculation for each step separately; load only changed fields
- Step 3 (connection overview) is purely informational; no selection needed

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
