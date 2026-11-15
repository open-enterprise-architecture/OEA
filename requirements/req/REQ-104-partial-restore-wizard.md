---
id: REQ-104
title: Partial restore three-step wizard
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-15
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

The system MUST provide a wizard for selective restore with four steps: step 1 general properties (`name`, `description`, `isLogical`), step 2 custom properties (the properties mapped at the source version and those with a value then or now), step 3 connection overview (informational: connections changed or added since the source version, each linked to its own history, where it is restored on its own; removed connections follow with deletion), step 4 summary with the selection, the warnings and an optional reason. Each step MUST be individually skippable. Steps without differences between the source and the current state MUST be skipped automatically with a note.

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

- Wizard SCR-029 with a step indicator, "Skip", "Back" and "Next" (change us-104-partial-restore)
- One preview (`previewEntityRestore`) feeds all steps; the state of the source version supplies the identical fields
- Step 3 (connection overview) is purely informational; no selection needed

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-15 | Change us-104-partial-restore | Realigned: four steps including the summary; the connection step links each changed connection to its own history (statement, implementation notes; owner decisions 2026-11-15) |
