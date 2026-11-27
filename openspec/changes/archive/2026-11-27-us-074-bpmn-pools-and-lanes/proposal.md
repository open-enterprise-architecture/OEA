# Proposal

## Why

Change 18 brought BPMN process diagrams with pools and lanes as plain containers. On 2026-11-27 the owner decided that lanes in a pool stack automatically, as in BPMN tools, and that the collapsing of sub-processes (REQ-076 AC8) is part of the BPMN scope. Change 18 left both to this change, S17 change 18b ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| User story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) |
| Requirement | [REQ-076](../../../requirements/req/REQ-076-bpmn-process-types-metamodel.md) AC4, AC7, AC8 |
| Use case | UC-10 |
| ADRs | [ADR-007](../../../adrs/ADR-007-canvas-library.md), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screen | SCR-021 "Diagram editor" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions (2026-11-27):**

- **Lanes stack automatically.** They fill the width of their pool and lie one below the other without gaps. A lane's height can be changed, and the pool follows. Nested lanes stack the same way.
- **Collapsing sub-processes** is part of the BPMN scope (REQ-076 AC8).

**Decisions and assumptions of this change (open for review):**

- **Stacking is a canvas layout.** Lane positions and sizes stay ordinary shape geometry in pt. The editor computes them, and the server keeps its generic checks.
- **Label band.** A pool or lane with lanes keeps a label band of 30 pt on the left. Its lanes start right of the band, at the top, in the order of their `y`.
- **Heights.** A lane is at least 30 pt high and at least as high as its content. The height of a pool or lane with lanes is the sum of their heights; the first lane takes the height of its pool.
- **Moves and resizes.** A lane resizes at its bottom edge, and the lanes below shift with their content. A lane dropped on the content area of another lane becomes its sibling, above or below it; dropped on a label band it is nested there. Dropping a lane within its pool reorders it, and dropping it into another container follows "New parent?".
- **Collapsed is stored per diagram.** The flag `collapsed` is stored on the shape of a sub-process (V52).
  - A collapsed sub-process is drawn at 90 × 60 pt with the "+" marker. Its stored size stays the expanded one.
  - Its nested shapes, and the edges and hierarchy lines that touch them, are hidden.
  - The repository is unchanged.
- **Only sub-processes.** `collapsed` is accepted only on a shape whose metatype the viewpoint maps to `bpmn:SubProcess` (422 otherwise).

**Owner decisions (2026-11-27, on the cut):** in BPMN viewpoints a lane offers icons to insert a lane above or below it, left or right in a vertical pool; dragging lanes stays in addition. Vertical pools come with this change; the orientation is set per pool and stored per diagram.

**Not in this change:** collapsing pools and lanes, and data objects (change 20).

## Capabilities

### New Capabilities

- `diagram/bpmn-pools-and-lanes`: stacked lanes and collapsed sub-processes.

### Modified Capabilities

- none

## Impact

- **Contract:** `collapsed` and `orientation` on `DiagramShape` and `DiagramShapeWrite`, and the 422s of `saveDiagramView`.
- **Database:** V52, `diagram_shapes.collapsed` and `diagram_shapes.orientation`.
- **Backend:** the shape store, the view and the save check of `collapsed`.
- **Frontend:** the lane layout in the canvas model, collapse and expand on SCR-021.
- **Documentation:**
  - the manual "Diagrams" (en/de);
  - SCREENS.md;
  - the business object diagram;
  - `docs/data-model.puml`;
  - US-074, REQ-076, UC-10 and the sprint plan.
