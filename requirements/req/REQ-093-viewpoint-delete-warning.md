---
id: REQ-093
title: Viewpoint deletion with diagram warning
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-17
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

When deleting a user-defined viewpoint, the system MUST check whether diagrams reference this viewpoint and display a warning with the number of diagrams that will be deleted with it. Deletion is not blocked; after the confirmation the viewpoint and its diagrams are deleted in one transaction and the entities stay.

## Rationale

Viewpoints may be used by many diagrams in production. A silent deletion without a warning would surprise users and create orphaned diagram references unnoticed.

## Acceptance Criteria

**AC1** (Warning for a used viewpoint):

- When: a viewpoint that is used by 5 diagrams is deleted
- Then: the system shows the warning "5 diagrams will be deleted"; after confirmation the viewpoint and its diagrams are deleted

**AC2** (No warning for an unused viewpoint):

- When: a viewpoint that is used by no diagram is deleted
- Then: a direct confirmation occurs without a warning dialog

## Dependencies

- **Prerequisites**: REQ-090 (viewpoint management)
- **Downstream effects**: the diagrams of the viewpoint are deleted for good (each audited as `diagram.deleted` with the cause `viewpoint-deleted`), the entities stay

## Implementation Notes

- Realigned by the OpenSpec change `us-090-viewpoints`: until diagrams exist, deleting a viewpoint asks for a plain confirmation (AC2); the count of diagrams (AC1) follows with the diagrams
- Realigned by the OpenSpec change `us-074-diagrams` (owner decision 2026-11-19): `diagramCount` on the viewpoint, the warning "N diagrams will be deleted" (instead of "will be affected"), and the deletion of the diagrams with the viewpoint

- Pre-delete check: COUNT of diagrams referencing this viewpoint ID
- Affected diagrams receive `viewpointId=null` after deletion; UI shows a notice banner
- Warning as a modal dialog before executing the deletion

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-090-viewpoints | Plain confirmation implemented (AC2); AC1 with the diagrams |
