# Design

## Context

**Change 16 (nesting):**

- `parentShapeId`, with containers growing with their children (`growContainers`);
- `nestShape`, `containerAt`, "New parent?";
- nesting at most 10 levels deep, and positions absolute in pt;
- the server checks that a nested shape lies completely within its container.

**Change 18 (BPMN):**

- `bpmnSymbols.ts` with the notation keys;
- `canContain` for `bpmn:Pool`, `bpmn:Lane` and `bpmn:SubProcess`;
- the flow warnings;
- the pool and lane symbols with a label band of 30 pt;
- default sizes: pool 600 × 200, lane 570 × 100, sub-process 270 × 150, task 90 × 60.

**Storage and save:**

- `diagram_shapes` (V47, V51) stores the geometry.
- `saveDiagramView` replaces the layout as a whole.

## Goals / Non-Goals

**Goals:** stacked lanes in pools and lanes; resizing, moving and reordering lanes; collapsed sub-processes stored per diagram.

**Non-Goals:**

- collapsing pools or lanes;
- server-side lane layout checks;
- automatic layout of flow nodes.

## Architecture Constraints

- **ADR-013:** contract first; the conventions check applies.
- **ADR-051:** SQL only in the `core.diagram` store.
- **ADR-007:** Vue Flow; the layout is computed in the canvas model as pure functions.

## Decisions

### D1 Lane layout (frontend, `bpmn2` only)

**Terms.**

- A *lane container* is a shape whose notation key is `bpmn:Pool` or `bpmn:Lane` and that has nested shapes with the key `bpmn:Lane`: its *lanes*. Its other nested shapes are its *direct content*.
- The *content* of a lane is what is nested in it that is not a lane, at any depth.
- A lane in a sub-process or on the canvas without a container is not stacked; a lane on the canvas with lanes is a lane container.
- A hidden shape (its entity deleted, change 16) is not part of a stack, and the other lanes close the gap.

**`stackLanes(shapes, mappings)`** is a pure function in `canvasModel.ts`.

- **When.** It runs after `growContainers` on every change of the shapes: add, remove, move, resize, nest, un-nest, "Show children" and load. Then `growContainers` runs once more for the containers that are not lane containers.
- **Order of the lanes.** On load by their `y`, then by drawing order. After a change, the order before the change is kept, except for the lane that was dragged or dropped, which goes where it was dropped (see the drop rule below).
- **Heights, bottom-up** (inner lane containers first):
  - A lane without lanes is at least 30 pt high and at least as high as the bottom of its content, relative to its top.
  - The height of a lane container is the sum of its lanes' heights. When its direct content reaches lower, its last lane grows to reach it.
  - The first lane nested in a container without lanes takes the container's height, so that the container does not shrink.
  - A lane container whose last lane is removed keeps its height.
- **Positions and widths, top-down:**
  - The container's left becomes at most the leftmost lane's `x` − 30, and its right at least the rightmost lane's right, so that a lane grown by its content (change 16) widens the container.
  - Each lane gets `x = container.x + 30` and `width = container.width - 30`, and the lanes lie from `container.y` one below the other without gaps.
  - When stacking moves a lane by `dy`, its content moves by `dy` with it (`moveTree`). Horizontally the content keeps its place, which the widening rule allows.
- **Rounding.** Every computed value is rounded with `round2`, so the edges of a lane meet those of its container exactly and the server's containment check (`OUTSIDE_PARENT`) holds.
- **Overrides of change 16.** A lane container's height is computed, so it also shrinks; `growContainers` still grows every other container.

**Drop rule for a lane** (new from the palette, dragged, or moved by "New parent?"):

- `containerAt` finds the candidate as before.
- When the candidate is a lane and the drop point lies right of its label band, the dropped lane becomes a sibling of the candidate: it is put into the candidate's container, above the candidate when the point lies in its upper half, below otherwise.
- When the point lies on the label band of a pool or lane, or in a pool without lanes, the dropped lane is nested in it, at the place of its `y` among the lanes there.
- The resulting container is the one `settle` compares: a lane dropped within its own container is reordered without "New parent?", and a lane dropped into another container follows "New parent?" as before. Both containers are restacked.
- A keyboard move of a lane within its container does not reorder it; stacking puts it back.
- Other shapes are dropped as before, so a task dropped on a lane is nested in the lane, and one dropped on the label band of a pool is direct content of the pool.

**Resizing:**

- A lane resizes only at its bottom edge. Its height changes, the lanes below and their content shift, and its containers are recomputed.
- A top-level lane container (a pool, or a lane container on the canvas) also resizes at its left and right edges. The width goes to all its lanes, recursively. Its bottom edge changes its last lane, recursively.
- In a resize the content keeps its place. A resize is clamped so that every lane at every depth stays at least 30 pt high, at least 1 pt wide and holds its content, and every shape stays at most 10 000 pt. For example, an empty pool with one level of lanes is at least 31 pt wide, and one with lanes nested to the depth limit of 10 levels (the pool and 9 lane levels, 9 label bands) at least 271 pt.
- A height change that would make the outermost lane container higher than 10 000 pt is clamped as well.

**Direct content of a lane container** (for example a task directly in a pool that also has lanes) keeps its position. It lies over the lanes and is not part of any lane; the last lane grows to keep it within the container.

**Saving.** The positions are ordinary shape geometry and are saved as usual; the server checks containment as before and has no lane rule. Stacking on load does not count as an unsaved change: the restacked shapes are the baseline, so a diagram saved before this change opens stacked and is stored stacked with the next save.

**Other notations** are unaffected.

### D2 `collapsed` (contract, database, backend)

- **Contract:**
  - `DiagramShape.collapsed` is a boolean and required.
  - `DiagramShapeWrite.collapsed` is a boolean, optional, default false.
  - The description of `saveDiagramView` names the 422.
- **Migration** `V52__diagram_shape_collapsed.sql`: `ALTER TABLE diagram_shapes ADD COLUMN collapsed BOOLEAN NOT NULL DEFAULT false`.
- **Save.** `collapsed` true is accepted only when the metatype of the shape's entity (an existing one, or a new one of `newEntities`) has the effective mapping `bpmn:SubProcess` in the diagram's viewpoint.
  - Otherwise it is a 422 `COLLAPSED_NOT_SUB_PROCESS` naming `shapes[i].collapsed`, checked in `validate` with the other layout errors.
  - The order of `save` stays: the 400 of the decimals, the 428 and 412 of the version, then the 422 of the layout, then the conflicts, then the entity writes.
  - The width and height sent are the expanded size, and the containment check uses them.
- **Store.** `StoredShape`, `ShownShape` and `ShapeWrite` of `DiagramShapeStore` carry the flag; the hidden shapes of deleted entities keep theirs.
- **View.** It returns `collapsed` true only while the mapping is still `bpmn:SubProcess`. After a viewpoint change, the stored flag reads as false, and the next save clears it.
- **Audit.** Today `diagram.layout-saved` carries `name`, `version`, `added`, `changed`, `removed`, `entitiesCreated`, `edgesAdded`, `edgesChanged`, `edgesRemoved`, `connectionsCreated` and `parentsChanged`. `changed` counts the kept shapes whose entity, position, size, container or relative drawing order changed (`DiagramViews.count`).
  - The event and its fields stay. `count` also compares the stored flag with the written one, so a collapsed or expanded shape counts in `changed`.
  - A stored true that the view reads as false after a viewpoint change is written as false by the next save and counts as changed as well.
- **The repository** is not touched.

### D3 Collapse on the canvas

- **Toggle.** A "−" or "+" control on a sub-process shape, and "Collapse" or "Expand" in the context menu, for holders of `entity:write`. The toggle is an unsaved change. Readers see the stored state without the toggle.
- **Drawing.** Collapsed, the sub-process is drawn at its `x` and `y` with 90 × 60 pt and the "+" marker. Its stored `width` and `height` are kept for expanding and for the save.
- **Unsaved changes.** `collapsed` is part of the fingerprint of a shape (`hasUnsavedChanges`, `isUnsaved`), and `writeRequest` sends it.
- **Hidden while collapsed:**
  - its nested shapes, recursively;
  - edges with an end on a hidden shape;
  - hierarchy lines touching a hidden shape.
- **Flow warnings** ignore the hidden edges.
- **A collapsed sub-process is not a container** for drops.
- **Moving a collapsed sub-process** moves its hidden content with it, as for every container. `settle` takes the center of the drawn 90 × 60 box as the drop point; the container then grows to hold the stored size.
- **Selecting a hidden shape** expands its collapsed ancestors first, for example from the conflict list.
- **Growth and stacking.** A collapsed shape keeps its stored size for `growContainers`, containment and `stackLanes`: a collapsed sub-process in a lane keeps the lane as high as its expanded size, because the server checks containment with the stored size.
- **Viewpoint change.** A sub-process whose type is a conflict after a viewpoint change has no `bpmn:SubProcess` mapping, so the view returns it expanded and its content is shown. Hidden conflicts in a collapsed sub-process that stays allowed are reached by selecting them (see above).

### D5 Vertical pools (owner decision 2026-11-27)

- **Orientation per pool, per diagram:** `orientation` (`horizontal` default, `vertical`) on the shape of a pool, stored in `diagram_shapes.orientation` (V52, `TEXT NOT NULL DEFAULT 'horizontal'`, check of both values). The lanes of a pool, nested ones included, follow its orientation and store none.
- **Contract:** `DiagramShape.orientation` required, `DiagramShapeWrite.orientation` optional, default `horizontal`. `vertical` is accepted only on a shape whose metatype the viewpoint maps to `bpmn:Pool` (also for new entities), otherwise 422 naming `shapes[i].orientation` (code `ORIENTATION_NOT_POOL`) among the layout errors as in D2; the view returns `horizontal` when the mapping is no longer `bpmn:Pool`. The audit counts a changed orientation as a changed shape, as for `collapsed`.
- **Vertical layout:** D1 transposed. The label band of 30 pt lies at the top of the pool or lane container, the name horizontal; its lanes lie from its left one beside the other without gaps, fill its height below the band, and its width is the sum of their widths. A lane is at least 30 pt wide and as wide as its content; it resizes at its right edge; the shapes to its right shift. Every boundary of D1 applies with width and height exchanged.
- **Switching:** "Vertical" / "Horizontal" in the context menu of a pool for `entity:write` (an unsaved change). The lanes keep their order; each lane's extent across the flow is kept (a horizontal lane of 100 pt height becomes a vertical lane of 100 pt width); the content of a lane keeps its offset to the lane's top left corner with x and y exchanged; then D1 or D5 restacks, and `growContainers` runs.
- **Drop rule:** in a vertical pool the left or right half of a lane's content area decides left or right of it; the label band at its top nests.

### D6 Insert lanes with icons (owner decision 2026-11-27)

- In a `bpmn2` diagram, a selected lane shows to holders of `entity:write` two icon buttons at its edges across the flow: "Insert lane above" and "Insert lane below" in a horizontal pool, "Insert lane left" and "Insert lane right" in a vertical one, with tooltip and `aria-label`.
- Choosing one creates a new entity of the lane's metatype as a sibling directly before or after the lane (the parent is the lane's container entity, as for a new element dropped into a container), with the default extent of the viewpoint's lane size (its height, used as width in a vertical pool), starts the inline name editing, and restacks. It is an unsaved change saved with the layout.
- Dragging a lane from the palette and reordering by drag stay as in D1.

### D4 Documentation

- the manual "Diagrams" (en, de): lanes and collapsing;
- SCREENS.md (SCR-021);
- the business object diagram (attribute `collapsed` of a shape) and `docs/data-model.puml`;
- US-074: "lanes can be nested" and the remaining parts of AC3, with AC2 and AC3 still open for data;
- REQ-076 AC8;
- UC-10;
- the sprint plan status of change 18b.
