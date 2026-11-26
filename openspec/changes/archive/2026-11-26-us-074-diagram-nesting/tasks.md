# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D2): the fields of shapes (read and write, maximum 10 000), of `DiagramEntity`, view and write, `listDiagramEntityChildren`, `getDiagramSettings` and `updateDiagramSettings` at `/admin/diagram-settings` (with 429), `askNewParent` on `ViewpointConfig`, `ViewpointWrite` and `PackageViewpoint`, and the description of `saveDiagramView` with the new checks in the order of the answers of D2. No Redocly ignore entry.

  Verify: Redocly lint, `check_openapi_authorization.py` with `test_check_openapi_authorization.py`, and `check_openapi_conventions.py` on the bundle with `test_check_openapi_conventions.py`.

## 2. Backend

- [x] 2.1 Migration `V51__diagram_nesting.sql` and stores (D1): `parent_shape_id` with `ON DELETE SET NULL`, `NUMERIC(7,2)` sizes, `diagram_hierarchy_lines`, `viewpoints.ask_new_parent`, `diagram_settings` with its row, the grants; `DiagramShapeStore`, `DiagramHierarchyLineStore`, `DiagramSettingsStore`, `ViewpointStore`.

  Verify: the migration test (Flyway on an empty and on a filled database); a stored width of 10 000.00 read back.
- [x] 2.2 Nesting in the save and the view (D2, D3).

  Verify with tests:
  - a nested shape saved and returned; the repository unchanged;
  - 422 for an unknown container, a later one, itself, a cycle, and a shape outside by 0.01 pt; exactly on the edge accepted;
  - depth 10 accepted, 11 rejected;
  - widths and heights 1 and 10 000 accepted, 0.99 and 10 000.01 rejected with 400;
  - a hidden container returns its nested shapes with `parentShapeId` null; a stored nesting that no longer contains the shape is returned with `parentShapeId` null;
  - a container removed while a request keeps its former nested shape under another container: the shape, its edges and the hidden edges stay; a hidden shape nested in a removed container stays with `parent_shape_id` null.
- [x] 2.3 Children list (D2).

  Verify: order, `allowed`, `compositionConnection` (present, deleted, type not allowed), deleted children left out, 500 children with `truncated` false and 501 with `truncated` true, 404 for each unknown case, 403 without `entity:read`.
- [x] 2.4 Hierarchy lines and parent changes in the save (D3).

  Verify:
  - a line saved and returned; 422 for a line without hierarchy; a line valid only through a parent change of the same save accepted; a line hidden after the hierarchy changes;
  - parent changes: set, cleared, the automatic composition connection created and removed, `entity.parent-changed` audited with the agent of the save, the version raised; 422 for a cycle and for depth 11 (depth 10 accepted); 409 `entity-version-conflict` and 409 `entity-blocked`; the same entity twice, an entity no shape shows, and both parent fields 422; with an entity update of the same entity (two versions); a change to the current parent writes nothing;
  - new entities with a parent (version 2, `entity.created` and `entity.parent-changed`), also with a new parent of the same save;
  - the order of the answers of D2 for each new check against its neighbors (e.g. a structural 422 and a 409 in one request give the 422; a 409 and a parent-rule 422 give the 409);
  - the hierarchy lock taken before the entity locks (a concurrent `setEntityParent` and save both complete);
  - 4000 lines and 500 parent changes accepted, 4001 and 501 rejected with 400;
  - an invalid shape with a valid parent change: 422 and the parent unchanged;
  - `diagram.layout-saved` with `parentsChanged`.
- [x] 2.5 Settings and viewpoints (D2).

  Verify: global default true; `getDiagramSettings` with `entity:read`, with `viewpoint:manage`, 403 without both; `updateDiagramSettings` with 428, 412, 403 without `viewpoint:manage`, the audit with old and new value, no audit and no new version for the same value; the effective value in the view for each combination (global true/false × viewpoint null/true/false); the viewpoint write and its audit naming `askNewParent`; an omitted value stores null; the package import sets it on a system-defined viewpoint, and a package without it stores null; the starter package unchanged.
- [x] 2.6 Cucumber: the scenarios of the three delta specs (API level). Use own metatypes, viewpoints and names with a unique prefix, never assert on global counts, and never log the same IdP user in twice.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes.
- [x] 3.2 Editor (D4) with `i18n` in en and de.

  Verify with specs: the children question and its answers, no question without children, the grid and the grown container, the notice for children not placed, hierarchy line or composition edge for a child already placed, "Show children" in the context menu, "New parent?" for moving in and out with "Yes" and "No", no question when disabled or for the current parent, the palette drop into a container (also into a new one), removing a container with the confirmation, the save body (absolute positions, `parentShapeId`, `hierarchyLines`, `parentChanges`, the parent of new entities), the read-only canvas.
- [x] 3.3 SCR-031: the global switch and the viewpoint field (D4).

  Verify: the switch only changeable with `viewpoint:manage`, 412 handled; the field in the dialog with the three choices; read-only for system-defined viewpoints.
- [x] 3.4 Stories SCR-021 (nested shapes, children question, "New parent?", hierarchy line) and SCR-031 (diagram settings).

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D5.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-074-diagram-nesting --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
