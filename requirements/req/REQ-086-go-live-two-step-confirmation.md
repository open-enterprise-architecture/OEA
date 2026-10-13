---
id: REQ-086
title: Go-live two-step confirmation
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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

# REQ-086: Go-live two-step confirmation

## Statement

Before executing a go-live transition, the system MUST display a confirmation dialog that (1) summarizes the diff (P1→baseline, P0→realized), (2) lists non-implemented solutions and open CEL violations as warnings and (3) requires manual entry of the exact plateau name as the second confirmation step.

## Rationale

Go-live is irreversible. A two-step confirmation prevents accidental transitions during a highly critical operation that puts the entire architecture repository into a new productive state.

## Acceptance Criteria

**AC1** (Button disabled without name entry):
- When: the confirmation dialog is displayed without a name entry
- Then: the "Confirm go-live" button is disabled

**AC2** (Wrong name):
- When: a wrong plateau name is entered
- Then: the button remains disabled; no error message is required

**AC3** (Correct name):
- When: the exact plateau name is entered
- Then: the button is enabled and go-live can be executed

## Dependencies

- **Prerequisites**: REQ-084 (plateau exists), REQ-087 (warnings without blocking)
- **Downstream effects**: REQ-088 (atomic transition)

## Implementation Notes

- Name validation in the frontend via real-time comparison (no API request needed)
- Diff display: P1 becomes baseline, P0 becomes realized — name both changes
- CEL violations and non-implemented solutions as separate warning sections

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
