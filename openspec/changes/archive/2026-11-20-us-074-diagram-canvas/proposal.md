# Proposal

## Why

Diagrams exist as objects since S17 change 10 (`us-074-diagrams`), but their page has no canvas yet. UC-10 and US-074 need a canvas on which entities of the viewpoint are placed, arranged and saved. The layout of the business object [Diagram](../../../business-objects/diagram.md) (shapes with position and size) and the planned read `getDiagramView` are the base.

This is S17 change 11 ([sprint plan](../../../docs/sprint-plan.md)). The details panel follows in change 12, connections in change 13, "Delete from repository" with the soft delete of entities in change 14, and the change of the viewpoint in change 15.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md) |
| User Story | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) (AC1, AC2 generic part, AC8 layout) |
| Business Object | [Diagram](../../../business-objects/diagram.md) |
| ADRs | [ADR-007](../../../adrs/ADR-007-canvas-library.md) (Vue Flow), [ADR-032](../../../adrs/ADR-032-authorization-model.md), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Screen | SCR-021 "Diagram editor" |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-19 used here:**

- **Saving:** changes are saved explicitly with "Save" when working alone. Collaboration, with immediate saving, follows with UC-24.
- **Permissions:** `entity:write` edits; `entity:read` reads.
- **Sizes:** new shapes get the default size of the viewpoint (90 × 30 pt without a stored size), in pt.
- **Several shapes:** an entity may appear several times in one diagram.
- **Removing a shape:** "Remove from diagram" leaves the entity. "Delete from repository" follows in change 14.

**Decisions and assumptions of this change (open for review):**

- **Layout storage:** a table of shapes per diagram (shape ID, entity, x, y, width, height in pt, drawing order). The layout is replaced as a whole on each save.
- **Contract:**
  - `getDiagramView` is implemented (no longer planned);
  - `saveDiagramView` (`PUT /views/diagrams/{id}`, `entity:write`, `If-Match`) is new: the full list of shapes in drawing order plus the new entities, each with a client key, metatype and name;
  - `searchDiagramEntities` (`GET /views/diagrams/{id}/entities`, `entity:read`) is new: "Add existing" searches the entities the viewpoint allows by name, since no existing read searches entities by name across metatypes.
- **Package:** view, search and save live in a new package `app.diagramview`, because `app.metamodel` depends on `app.diagram` and `app.entity` on `app.metamodel` (no cycle, ADR-051).
- **New entities:**
  - they are created with name and metatype only, through the same checks and audit as an entity write (metamodel, required properties, visibility), inside the save transaction;
  - an entity of a metatype with a mandatory property without default value is created without the value and marked as incomplete until the value is written (owner decision 2026-11-20); the values are entered in the details panel of change 12 or through the batch API;
  - a failure answers 422 naming the shape, and nothing is saved;
  - their metatype must be an element metatype allowed by the viewpoint.
- **Deleted entities:** their shapes are kept but left out of the view, so they show again after a restore.
- **Limits:**
  - 2000 shapes per diagram;
  - 500 new entities per save;
  - positions within ±100 000 pt;
  - sizes 1–2000 pt, with at most two decimals;
  - a request size limit of 2 MB for the save.
- **Drawing:** every shape is drawn as a box with the name, the metatype and the visual hint of its mapping. Notation-specific shapes (BPMN) follow with change 16.
- **Canvas library:** `@vue-flow/core` and `@vue-flow/node-resizer` (MIT, ADR-007) are added. `THIRD-PARTY-LICENSES.md` is regenerated.

**Not in this change:**

- editing the entity's properties (details panel, change 12);
- connections (change 13);
- "Delete from repository" (change 14);
- changing the viewpoint (change 15);
- notation shapes, containers, auto layout, the portal view (SCR-052) and collaboration.

## Capabilities

### New Capabilities

- `diagram/diagram-canvas`: the view of a diagram, the entity search for it, saving its layout with new entities, its shapes, the audit of the layout and the canvas editor SCR-021.

### Modified Capabilities

- `diagram/diagrams`: the requirement "Diagrams page" – a diagram opens its page with the canvas instead of name, description and viewpoint only.

## Impact

- **Contract:** `getDiagramView`, `saveDiagramView` and `searchDiagramEntities`, with `DiagramView`, `DiagramShape` and `DiagramViewWrite`.
- **Backend:**
  - a migration (`V47`, `diagram_shapes`);
  - `core.diagram.DiagramShapeStore` and the entity search query in `core.entity`;
  - an entity creation in the caller's transaction in `app.entity` (the batch writes in transactions of its own);
  - `app.diagramview` (view, search, save and validation);
  - the request size limit of the save;
  - the controller.
- **Frontend:**
  - Vue Flow;
  - `DiagramCanvas.vue` with the palette, "Add existing", moving, resizing, "Remove from diagram", "Save", the unsaved-changes guard and the read-only mode;
  - stories (SCR-021) and fakes.
- **Documentation:**
  - the manual "Diagrams" (en/de);
  - SCREENS.md (SCR-021 `implemented`, the legacy SVGs deleted, SCR-067 realigned);
  - the business object Diagram (BR-DG-02 realigned) and the data model;
  - US-074 and UC-10;
  - `THIRD-PARTY-LICENSES.md`;
  - the sprint plan.
