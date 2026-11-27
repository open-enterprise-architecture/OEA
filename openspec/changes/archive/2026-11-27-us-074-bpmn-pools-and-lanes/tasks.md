# Tasks

## 1. Contract and database

- [x] 1.1 `api/openapi.yaml` (D2): `collapsed` on `DiagramShape` (required) and `DiagramShapeWrite` (optional, default false), the 422 in the description of `saveDiagramView`.

  Verify: Redocly lint, `check_openapi_authorization.py` and `check_openapi_conventions.py` with their tests.
- [x] 1.2 `V52__diagram_shape_collapsed.sql` (D2).

## 2. Backend

- [x] 2.1 Store, view and save of `collapsed` (D2).

  Verify:
  - a sub-process shape saved collapsed is returned collapsed with its expanded size, and expanded again after a save with false;
  - collapsed on a task shape, on a new task of `newEntities`, and on a sub-process in a viewpoint without the `bpmn:SubProcess` mapping, is 422 naming `shapes[i].collapsed`, and nothing changes; collapsed on a new sub-process is accepted;
  - a save that only collapses a shape is audited as `diagram.layout-saved` with `changed` 1, and one that sends the stored flag unchanged with `changed` 0;
  - after `changeDiagramViewpoint` to a viewpoint without the mapping, the view returns false;
  - a shape without the field is saved as false;
  - the nested shapes and the parents of the entities are unchanged.
- [x] 2.2 Cucumber: the collapse scenarios at API level (save, reload, 422, two diagrams), in the BPMN feature's own instance.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes.
- [x] 3.2 `stackLanes` and the lane interactions (D1).

  Verify with specs, `it.each` where values repeat:
  - adding a lane stacks it, and the pool grows; the first lane takes the pool's height;
  - a lane dropped on the upper or lower half of a lane becomes its sibling above or below; dropped on a label band it is nested there;
  - nested lanes stack inside their lane;
  - a wider pool makes every lane wider;
  - a higher lane shifts the lanes below and their content, and the pool follows;
  - minimum height: 30 is kept and 29 is clamped to 30;
  - content: a lane exactly as high as its content is accepted, and 1 pt less is clamped;
  - reordering by drag keeps the content with each lane and asks no "New parent?";
  - a task moved below, above or left of its lane grows the lane or the container, and the stacked geometry still passes the containment of `lies` at every level;
  - a task directly in a pool with lanes keeps its place, and the last lane grows to keep it in the pool;
  - a collapsed sub-process in a lane keeps the lane at the height of its stored size;
  - a removed lane closes the gap; a container whose last lane is removed keeps its height;
  - loading a diagram with unstacked lanes stacks them without an unsaved change;
  - dragging a lane out restacks both containers;
  - resize limits: an empty pool with one level of lanes keeps 31 pt and is clamped from 30 to 31; one with lanes nested 10 levels deep (pool and 9 lane levels) keeps 271 pt and is clamped from 270 to 271; a lane height that would make the pool 10 000.01 pt high is clamped to 10 000;
  - `archimate3` is unaffected;
  - the save sends the stacked geometry.
- [x] 3.3 Collapse and expand (D3), with `i18n` in en and de.

  Verify:
  - collapsed is drawn at 90 × 60 with "+", and the stored size is kept in the save;
  - the nested shapes, edges and hierarchy lines are hidden, and shown again after expanding;
  - a flow from outside to inside is hidden and kept;
  - a collapsed sub-process is no drop container;
  - selecting a hidden conflict expands it;
  - the toggle counts as an unsaved change, and the save sends `collapsed`;
  - a collapsed sub-process dropped into a lane is nested by the center of its drawn box, and the lane grows to its stored size;
  - readers have no toggle;
  - the flow warnings ignore hidden edges.
- [x] 3.4 Story SCR-021: a pool with stacked and nested lanes and a collapsed sub-process.

  Verify: the story tests with a11y pass, and `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 5. Vertical pools and insert icons (owner decisions 2026-11-27, added after the cut)

- [x] 5.1 Contract and V52 (D5): `orientation` on `DiagramShape` (required) and `DiagramShapeWrite` (optional, default `horizontal`), the 422 `ORIENTATION_NOT_POOL`; the column `diagram_shapes.orientation` in V52 (not yet released, so extend V52 rather than adding V53).

  Verify: Redocly lint and the openapi checks with their tests.
- [x] 5.2 Backend (D5): store, view and save of `orientation`.

  Verify: a pool saved vertical is returned vertical and horizontal again after a save with `horizontal`; `vertical` on a lane or task shape and on a new non-pool entity 422 naming `shapes[i].orientation`, nothing changes; an unknown value 400; after a viewpoint change without the `bpmn:Pool` mapping the view returns `horizontal`; the audit counts a changed orientation as changed 1, an unchanged one 0; Cucumber scenarios in the BPMN feature's instance.
- [x] 5.3 Frontend layout (D5): the transposed stacking, the switch "Vertical" / "Horizontal", the drop rule of a vertical pool.

  Verify with specs (`it.each` over both orientations where the rule is the same): side by side, widths sum, band at the top; minimum width 30 kept and 29 clamped; a lane as wide as its content accepted and 1 pt less clamped; switching keeps order, extents and content; switching back restores the horizontal layout; nested lanes follow the pool; the save sends `orientation`.
- [x] 5.4 Insert icons (D6), with `i18n` in en and de.

  Verify: above/below in a horizontal pool, left/right in a vertical one, for the first and the last lane and in a nested lane; the new lane's metatype, parent, default extent and name editing; restacked; an unsaved change; no icons for readers and outside `bpmn2`; tooltips and `aria-label`.
- [x] 5.5 Story SCR-021: a vertical pool with lanes and the insert icons; the manual "Diagrams" (en, de), SCREENS.md and the business object diagram and `docs/data-model.puml` name `orientation`.

## 4. Documentation and wrap-up

- [x] 4.1 The manual "Diagrams" in English and German (D4).
- [x] 4.2 SCREENS.md, the business object diagram, `docs/data-model.puml`.
- [x] 4.3 US-074, REQ-076, UC-10, and the sprint plan status of change 18b.
- [x] 4.4 All checks of AGENTS.md "Before Pushing", the frontend unit tests and `vue-tsc`, and the full backend `verify`.
