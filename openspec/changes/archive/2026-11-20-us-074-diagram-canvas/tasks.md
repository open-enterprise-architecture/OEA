# Tasks

## 1. Contract and dependency

- [x] 1.1 `api/openapi.yaml` (D2): `getDiagramView` implemented, `saveDiagramView` and `searchDiagramEntities`, with `DiagramView`, `DiagramShape`, `DiagramShapeWrite`, `DiagramNewEntity`, `DiagramViewWrite`, the 422 problem with `invalidFields` and `errors`, and the entity search page; the descriptions of `Diagram` and of the tag updated; `getPortalDiagramView` keeps its reference.

  Verify: Redocly lint, `check_openapi_authorization.py` with its test and `check_openapi_conventions.py` with its test.
- [x] 1.2 Add `@vue-flow/core` and `@vue-flow/node-resizer` (MIT), then regenerate `THIRD-PARTY-LICENSES.md` (`npm run build`, `python3 scripts/generate_third_party_licenses.py`).

  Verify: `python3 scripts/generate_third_party_licenses.py --check` passes.

## 2. Backend

- [x] 2.1 `V47__diagram_shapes.sql` with the grants, and `core.diagram.DiagramShapeStore` (D1); the entity search query in `core.entity` (D3).
- [x] 2.2 The entity creation in the caller's transaction in `app.entity` (D3), built from the steps of the atomic batch create.

  Verify with tests:
  - it fails without a surrounding transaction;
  - a created entity has the same version, history and `entity.created` event (with batch ID and agent headers) as one from `batchWriteEntities`;
  - a blank name and a restricted property give the issues of the batch; a metatype with a mandatory property without default creates the entity with the warning `MANDATORY_MISSING` (incomplete), while the batch path keeps rejecting it;
  - a rollback of the caller removes the created entities.
- [x] 2.3 The package `app.diagramview` with view, search and save (D3, D4), and the controller.

  Verify with tests:
  - `CycleArchitectureTests` and the other ArchUnit rules pass;
  - the view with shapes in pt in drawing order; shapes of a deleted entity (deleted by the store, as by a go-live) left out, kept in the table on a save, and shown again after `deleted_at` is cleared by the test (the restore operation follows in change 14);
  - 404 for an unknown or deleted diagram (view, search and save); 403 without `entity:read`;
  - search: ordered by name ignoring case, filtered by name part with `%` and `_` matched literally, only allowed element metatypes, no deleted entities, `metatypeId` of a metatype not allowed gives 422; `q` of 255 characters accepted and 256 rejected with 400; page size 1 and 200 accepted, 0 and 201 rejected;
  - save:
    - 200 with the next version and `ETag`, a moved and resized shape, a changed drawing order, and two shapes of one entity;
    - a new entity created and audited like a batch create;
    - an atomic rollback with 422 naming the item when a new entity fails (blank name, metatype not allowed), when a shape references a disallowed, connection, unknown or deleted entity, when a key is unknown, duplicate or unreferenced, or when a shape has both or neither of `entityId` and `newEntityKey`;
    - a stored shape whose metatype the viewpoint no longer allows is accepted when it keeps its entity;
    - 412 (also after a rename), 428, and 403 without `entity:write`;
  - limits:
    - width and height 1 and 2000 accepted, 0.99 and 2000.01 rejected with 400;
    - position −100 000 and 100 000 accepted, −100 000.01 and 100 000.01 rejected with 400;
    - a value with three decimals rejected with 400;
    - 2000 shapes accepted, 2001 rejected with 400; 500 new entities accepted, 501 rejected with 400;
    - a new entity name of 255 characters accepted and 256 rejected with 400;
  - a shape ID of another diagram, and a duplicate shape ID, rejected with 422;
  - the audit `diagram.layout-saved` with the counts, and nothing on a rejected save.
- [x] 2.4 The request size limit of D5 (`oea.web.request-size.diagram-view`, 2 MB, matched by path pattern).

  Verify: a save with 2000 shapes and 500 new entities with names of 255 escaped characters is accepted; a body above 2 MB is answered with 413; the other paths keep their limits.
- [x] 2.5 Cucumber: `features/diagram/diagram-canvas.feature` with the scenarios of the delta spec at API level (the editor scenarios are covered by the frontend specs).
  - Reuse the shared instance and the existing sessions, steps and viewpoints of `diagrams.feature`.
  - Never log the same IdP user in twice.
  - Use unique names for diagrams, viewpoints and entities.
  - Scenarios with a deleted entity stay in the integration tests of 2.3 if no step can delete an entity through the API.
  - The limits as Scenario Outlines with both sides of each boundary.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes (view, search, save).
- [x] 3.2 `DiagramCanvas.vue`, `EntityShape.vue`, the palette, "Add existing", moving and resizing (mouse and keyboard), "Remove from diagram", "Save" with the 412 and 422 handling, the unsaved-changes guard and the read-only mode (D6), with `i18n` in en and de.

  Verify with specs:
  - default sizes from the viewpoint (stored and 90 × 30);
  - pt ↔ px conversion rounded to two decimals;
  - a new entity with an inline name, and one removed when the name stays empty;
  - "Add existing" searches and places a shape;
  - moving with arrow keys by 1 and 10 pt;
  - the save request carries shapes in drawing order and new entities;
  - the 412 dialog; the 422 marking of the named shape with its reason;
  - the leave guard;
  - no palette, "Add existing" or "Save" for a reader.
- [x] 3.3 Story SCR-021 (empty, with shapes, unsaved, rejected shape, read-only).

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D7.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-074-diagram-canvas --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`;
  - the license check.
