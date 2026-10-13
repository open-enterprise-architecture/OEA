---
id: REQ-145
title: Shape Drag-to-Canvas with Entity Lookup and Inline Creation
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - diagram
    - architecture-element
  business_rules: []
  stakeholders:
    - SH-01
    - SH-03
    - SH-04
    - SH-07
  concept: []
  adrs:
    - ADR-007
    - ADR-019
supersedes: []
superseded_by: []
---

# REQ-145: Shape Drag-to-Canvas with Entity Lookup and Inline Creation

## Statement

After a shape template from the palette is dropped onto the canvas, the system MUST display an entity lookup dialog. The dialog MUST contain a text input field that shows existing entities of the same type as autocomplete suggestions while typing. If the user selects an existing entity, it MUST be inserted into the diagram as a shape reference (no new creation). If no matching entity is found and the user confirms with "OK", a new entity of the corresponding type MUST be created and simultaneously inserted into the diagram. If "Cancel" is pressed or the drop is undone, NO entity may be created.

## Rationale

In OEA there is a strict separation between the model (entities in the solution) and the representation (shapes in the diagram). Every shape in the diagram is a reference to an entity — not the entity itself. This dialog ensures that the user consciously decides whether to reference an existing entity or create a new one. This prevents accidental duplicates and promotes reuse of existing architecture elements.

## Context

| Scenario | Result |
|---|---|
| User drags AC shape onto canvas, types "Catalog-Service", selects existing entity | Shape is inserted, entity unchanged |
| User drags AC shape onto canvas, types "New-Service", no match, presses OK | New entity "New-Service" [AC] is created + shape inserted |
| User drags AC shape onto canvas, presses Cancel | Shape and drop are undone |
| User drags the same entity a second time into the same diagram | System warns: "This entity is already present in the diagram. Insert again?" |

## Type-Specific Fields

### For type=functional

**Inputs (after drop)**:

| Field | Type | Description |
|---|---|---|
| dropPosition | {x, y} | Canvas coordinate of the drop |
| entityType | string | Metamodel type of the dropped shape (e.g. "ApplicationComponent") |
| searchQuery | string | Text entered by the user in the lookup field |

**Processing**:

1. After drop: the entity lookup dialog appears inline on the canvas near the drop position
2. The dialog shows the type badge (e.g. [AC]) and type label as context
3. While typing: `GET /api/v1/entities?type={entityType}&q={searchQuery}` — debounce 200ms
4. Autocomplete lists at most 8 hits with type badge and names; existing entities are marked "existing"
5. User selects an existing entity → `POST /api/v1/diagrams/{id}/shapes` with `entityId` (no creation)
6. User types a new name + OK → `POST /api/v1/entities` (creation) + `POST /api/v1/diagrams/{id}/shapes`
7. User Cancel → drop undone, no API calls

**Outputs**:

- Shape appears on the canvas at the drop position
- New entity becomes visible in the explorer tree under the corresponding type folder
- Log: "[INFO] Shape inserted: <Name> (<Type>)  ·  entity: existing|new"

## Acceptance Criteria

**AC1** (Insert existing entity):
- Given: entity "Catalog-Service" [AC] exists in the solution
- When: the user drags an [AC] shape onto the canvas, types "Catalog", sees "Catalog-Service (existing)", selects it
- Then: the shape appears on the canvas; no new object in the model; log shows "existing"

**AC2** (Create new entity):
- When: the user types "New-Reporting-Service", no autocomplete match, presses OK
- Then: a new entity "New-Reporting-Service" [AC] is created; the shape appears on the canvas; explorer tree updated

**AC3** (Autocomplete while typing):
- When: the user types "auth"
- Then: the dropdown shows at most 8 entities with "auth" in the name; response time < 300 ms with 10,000 entities

**AC4** (Cancel = no side effect):
- When: the user presses Cancel or ESC after the drop
- Then: no entity created, no shape in the diagram, drop completely undone

**AC5** (Duplicate warning):
- When: the user drags the same entity a second time into the same diagram
- Then: the system shows the warning "Entity already on canvas — insert again?" with Yes/No

## Dependencies

- Blocked by: REQ-144 (shape palette), REQ-031 (create entity)
- Related: REQ-047 (filter queries), REQ-146 (delete behavior)
- Affects: ADR-007 (canvas library — drag-and-drop API)

## Implementation Notes

- Lookup endpoint: `GET /api/v1/entities?type=ApplicationComponent&q=catalog&solutionId=…&limit=8`
- Shape insert: `POST /api/v1/diagrams/{id}/shapes` `{ entityId, x, y, w, h }`
- Entity create + shape insert is an atomic transaction (both fail or both succeed)
- The lookup dialog appears inline on the canvas (no modal overlay) so that the user sees the drop position

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft |
