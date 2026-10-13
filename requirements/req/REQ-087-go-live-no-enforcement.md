---
id: REQ-087
title: Go-live without enforcement through warnings
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
    - solution
    - architecture
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-087: Go-live without enforcement through warnings

## Statement

Go-live MUST NOT be blocked by non-implemented solutions or open CEL rule violations. These states MUST be displayed as warnings in the confirmation dialog (count + list), but do not block execution.

## Rationale

Freedom is the greatest added value. Operators can consciously decide to put a plateau into production even if not all solutions are implemented — e.g., in a phased migration. Enforced blocking would not reflect real transition situations.

## Acceptance Criteria

**AC1** (Non-implemented solutions as a warning):
- When: 3 non-implemented solutions exist at go-live
- Then: the dialog shows the warning "3 solutions not implemented"; go-live remains executable

**AC2** (CEL violations as a warning):
- When: CEL rule violations exist at go-live
- Then: the dialog shows the warning "N business rule violations"; go-live remains executable

**AC3** (No warning section without warnings):
- When: neither non-implemented solutions nor CEL violations exist at go-live
- Then: no warning section is displayed in the dialog

## Dependencies

- **Prerequisites**: REQ-086 (confirmation dialog)
- **Downstream effects**: REQ-088 (go-live execution)

## Implementation Notes

- Warnings are informative; no HTTP error status for these states
- The decision lies with the user; no automatic blocking by the system
- Warning list: solution names and violations as an expandable list in the dialog

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
