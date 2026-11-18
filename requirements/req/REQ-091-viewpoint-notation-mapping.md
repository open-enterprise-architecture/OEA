---
id: REQ-091
title: Viewpoint notation mapping consistency
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
- Implemented by the OpenSpec change `us-091-viewpoint-notations` (owner decisions 2026-11-17): the element catalog is shipped as data and read with `listNotationElements` (`GET /admin/viewpoints/notations`, `entity:read` or `viewpoint:manage`); keys carry the prefix `archimate:`, `uml:` or `bpmn:` (not `archimate3:`), and `generic:Box` and `generic:Arrow` fit every notation (AC1: the dropdown shows the elements of the notation and the generic ones). AC2: an element of another notation, an unknown element, a shape of the wrong kind (element shape for an entity type, relationship shape for a connection type), a mapping of a type the viewpoint does not allow and a second mapping of the same type are rejected with 422 naming `mappings`; more than 1000 mappings, a key over 100 characters, a size outside 1–2000 and a visual hint not of the form `#rrggbb` with 400. AC3: changing the notation in the dialog on SCR-031 clears the elements that no longer fit

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-17 | Change us-091-viewpoint-notations | Implemented; prefixes, generic elements and limits realigned |
