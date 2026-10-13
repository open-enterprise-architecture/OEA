---
id: REQ-091
title: Viewpoint notation mapping consistency
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

# REQ-091: Viewpoint notation mapping consistency

## Statement

The `notationElement` dropdown in the viewpoint editor MUST display exclusively elements of the selected notation (prefix filter: `archimate3:*`, `uml:*`, `bpmn:*`). Selecting an element with the wrong prefix MUST be rejected with HTTP 422 on saving.

## Rationale

An ArchiMate viewpoint with UML elements would lead to inconsistent diagrams that cannot be rendered correctly in any notation. Consistency between notation and mapping elements must be enforced in both the frontend and the backend.

## Acceptance Criteria

**AC1** (Dropdown filter by notation):
- When: `notation=archimate3` is selected
- Then: the dropdown shows only `archimate3:*` elements

**AC2** (API validation):
- When: an API call tries to save a `bpmn:Task` element in an `archimate3` viewpoint
- Then: the API responds with HTTP 422

**AC3** (Dropdown reset on notation change):
- When: the notation is changed in the form
- Then: the dropdown contents update immediately; already selected incompatible elements are reset

## Dependencies

- **Prerequisites**: REQ-090 (viewpoint editor)
- **Downstream effects**: none

## Implementation Notes

- Prefix filter client-side in the dropdown and server-side in the validation logic
- Notation change triggers reactive update of the mapping fields
- Error message on HTTP 422 explicitly names the inconsistent element

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
