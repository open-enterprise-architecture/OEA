# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D1–D4): `changeDiagramViewpoint` (`PUT /diagrams/{id}/viewpoint`), `countViewpointAffectedDiagrams` (`POST /admin/viewpoints/{id}/affected-diagrams/query`, both lists `maxItems: 500`), `conflict` and `conflictCount`, 409 `diagram-conflicts` with its problem schema next to `DiagramEntityConflictProblem` in the 409 response, the updated description and answer order of `saveDiagramView` (the 409 after the 422 of the layout and the edges, before the 403 of the visibility), and the descriptions of `updateDiagram` and `DiagramUpdate` (the viewpoint changes only through `changeDiagramViewpoint`). No Redocly ignore entry.

  Verify: Redocly lint, `check_openapi_authorization.py` and `check_openapi_conventions.py` with their tests.

## 2. Backend

- [x] 2.1 `changeDiagramViewpoint` (D1).

  Verify: changed with version and audit; same viewpoint without version and audit; 422 unknown viewpoint, 412, 428, 403 without `entity:write`, 403 for the local system admin account, 404 deleted diagram; shapes, edges and lines kept.
- [x] 2.2 Conflicts in the view (D2).

  Verify: shape and edge conflicts after a viewpoint change, after a viewpoint edit and after a package re-import that drops a type from a package viewpoint; a shape of a subtype of an allowed base type is a conflict (exact metatype, as in the save); resolved when the type is allowed again; hidden shapes of deleted entities no conflict, and a conflict again after the restore; `conflictCount`.
- [x] 2.3 Blocked save (D3).

  Verify: 409 naming every conflict and nothing changed, no audit; accepted when all conflicts are removed in the save; 409 with one left; 409 for a save without any change, for a save of entity updates only and for a save of parent changes only; a new shape of a type not allowed still 422 `METATYPE_NOT_ALLOWED`; a conflicting container removed with its nested shapes accepted; the answer order (400, the 403 of a scoping connection type, 412 and 428 before the 409; a 422 of the layout before the 409; the 409 before the 403 of the visibility and before 409 `entity-version-conflict`).
- [x] 2.4 `countViewpointAffectedDiagrams` (D4).

  Verify: the count for removed metatypes and connection types; hidden shapes and edges not counted; deleted diagrams not counted; 0 without removal; unknown IDs ignored; 0 and 500 IDs accepted and 501 rejected with 400 for each list; 403 without `viewpoint:manage`; 404 unknown viewpoint.
- [x] 2.5 Cucumber: the scenarios of the three delta specs (API level), with own metatypes, viewpoints and names with a unique prefix, never asserting on global counts.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes.
- [x] 3.2 "Change viewpoint", the conflict marks and the panel (D5), with `i18n` in en and de.

  Verify with specs: the dialog with preview and "No conflicts", disabled with unsaved changes, 412; the marks with text; "Remove from diagram" and "Remove all conflicts"; the 409 marks; read-only for readers.
- [x] 3.3 SCR-031: the warning with the count (D4).

  Verify: the warning with N > 0, none with N = 0, none without diagrams.
- [x] 3.4 Stories SCR-021 (conflicts, change viewpoint dialog) and SCR-031 (warning).

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes, except for the images CI renders.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D6.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - markdownlint;
  - `openspec validate us-074-diagram-viewpoint-change --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
