---
id: REQ-093
title: Viewpoint deletion with diagram warning
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-12
  business_objects:
    - viewpoint
    - metamodel-configuration
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-093: Viewpoint deletion with diagram warning

## Statement

When deleting a user-defined viewpoint, the system MUST check whether diagrams reference this viewpoint and display a warning with the number of affected diagrams. Deletion is not blocked; after deletion, affected diagrams no longer reference a valid viewpoint and receive a corresponding note in the UI.

## Rationale

Viewpoints may be used by many diagrams in production. A silent deletion without a warning would surprise users and create orphaned diagram references unnoticed.

## Acceptance Criteria

**AC1** (Warning for a used viewpoint):

- When: a viewpoint that is used by 5 diagrams is deleted
- Then: the system shows the warning "5 diagrams will be affected"; after confirmation it is deleted

**AC2** (No warning for an unused viewpoint):

- When: a viewpoint that is used by no diagram is deleted
- Then: a direct confirmation occurs without a warning dialog

## Dependencies

- **Prerequisites**: REQ-090 (viewpoint management)
- **Downstream effects**: affected diagrams show a note about the missing viewpoint

## Implementation Notes

- Pre-delete check: COUNT of diagrams referencing this viewpoint ID
- Affected diagrams receive `viewpointId=null` after deletion; UI shows a notice banner
- Warning as a modal dialog before executing the deletion

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
