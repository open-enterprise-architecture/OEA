# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (D2, D8): the tag `Diagrams` with the five diagram operations plus `listDeletedDiagrams` and `restoreDiagram`, `Diagram`, `DiagramViewpoint`, `DiagramCreate`, `DiagramUpdate` and `DiagramPage`; `diagramCount` on `ViewpointConfig`; `diagrams` in `BrowserCurrentState` and the category parameter, the item kind `diagram` and `notation` on `BrowserItem`; the tree item type `diagram`; `diagramsDeleted` in the import result; the descriptions of `deleteViewpoint`, `importMetamodelPackage`, `listBrowserItems` and `addTreeNodeItem`.

  Verify: Redocly lint, `check_openapi_authorization.py` and its test, `check_openapi_conventions.py` (bundled as in the CI job `openapi-lint`) and its test pass without new allowlist entries.

## 2. Backend

- [x] 2.1 The migration `V46__diagrams.sql` and `core.diagram.DiagramStore` (D1).

  Verify: a migration test – the table, the name check (trimmed, 1–200), the unique index ignoring case and the grants.
- [x] 2.2 `app.diagram.Diagrams` and the controller (D3).

  Verify with tests:
  - create 201 with version 1, `ETag` and Anna as `createdBy`;
  - 403 without `entity:write`, and 403 for the local system admin account;
  - 422 `diagram-name-taken` ignoring case and blanks;
  - 422 for a blank name and for an unknown viewpoint;
  - names of 1 and 200 characters accepted, 201 and only blanks rejected with 422 naming `name`; description 2000 accepted, 2001 rejected with 422 naming `description` (parameterized);
  - a service account with `entity:write` creates, renames and deletes, and is the actor;
  - list order and name filter, with an escaped `%` and `_` matched literally;
  - page sizes 1 and 200 accepted, 0 and 201 rejected with 400; the filter of 200 accepted and 201 rejected with 400; 51 diagrams over two pages with the cursor; a cursor of another filter answered 400 naming `cursor`;
  - get 404 for an unknown diagram;
  - rename and description with `If-Match`: 200 with the next version, 412, 428; a rename to a taken name ignoring case 422 `diagram-name-taken`;
  - a `viewpointId` in the update does not change the viewpoint;
  - delete 204 and then 404, the entities unchanged;
  - 403 for reads without `entity:read`;
  - the audit events with their details (`name`, `viewpointId`, `fields`), and nothing on a rejected write (403, 412, 422, 428).
- [x] 2.2a Restorable deletion (D8): `deleted_at`/`deleted_by`, `listDeletedDiagrams`, `restoreDiagram`, audit `diagram.restored`.

  Verify with tests:
  - a deleted diagram is not listed, not counted and answers 404 on get, update and delete;
  - its name is free for a new diagram;
  - the list of deleted diagrams with page sizes 1 and 200 accepted and 0 and 201 rejected;
  - restore 200 with the next version;
  - restore 409 for a diagram that is not deleted, 422 `diagram-name-taken`, 412, 428 and 404;
  - 403 without `entity:restore` for both operations;
  - the tree entry is hidden while deleted and shown again after the restore;
  - viewpoint deletion removes deleted diagrams too;
  - nothing is audited on a rejected restore.
- [x] 2.3 Viewpoint deletion and package import (D4).

  Verify:
  - `diagramCount` 0 and 5 on get and list, and 0 on create;
  - deleting a viewpoint with 2 diagrams deletes both, each audited with `cause` `viewpoint-deleted`, and the entities stay;
  - a stale `If-Match` deletes neither the viewpoint nor its diagrams (412);
  - a system-defined viewpoint stays read-only (422) and keeps its diagrams;
  - a re-import that drops a system-defined viewpoint deletes its 2 diagrams and reports `diagramsDeleted` 2 in the result and the audit; a re-import that keeps it reports 0; a rejected re-import deletes nothing;
  - creating a diagram for a viewpoint deleted meanwhile answers 422 naming `viewpointId`;
  - `CycleArchitectureTests` and the layer and data-access rules stay green (no `app.diagram` import of `app.metamodel` or `app.history`).
- [x] 2.4 Browser and navigation tree (D5).

  Verify:
  - "Diagrams (n)" with 0 and 2;
  - the category ordered by name with the notation, page sizes 1 and 200 accepted, 0 and 201 rejected with 400, 51 diagrams over two pages;
  - the category `diagrams` with `solution` rejected with 422 naming `category`;
  - placing a diagram at the end, and a second entry in the same folder rejected with 422 naming `referenceId`;
  - an unknown diagram rejected with 422 naming `referenceId`;
  - placing with `entity:write` but without `viewpoint:manage` rejected with 403;
  - the entry of a deleted diagram left out;
  - the audit of the placement with `itemType` `diagram` and `referenceId`.
- [x] 2.5 Cucumber: `features/diagram/diagrams.feature`, and the scenarios of the delta specs for browser, tree, viewpoint deletion and package re-import in their features.
  - Reuse the shared instance and the existing sessions.
  - Never log the same IdP user in twice, and never add an IdP mapping again.
  - Close extra instances after their feature.

  Verify: the full `mvn verify` passes.

## 3. Frontend

- [x] 3.1 API client and fakes.
- [x] 3.2 Stories: SCR-067 (list, empty, reader, create dialog), the browser category and tree entry on SCR-055, and the deletion and edit warnings on SCR-031.

  Verify: the story tests with a11y pass; `python3 scripts/check_screens.py` passes.
- [x] 3.3 `DiagramsPage.vue`, `DiagramPage.vue`, routes, the header entry, the sidebar (browser category and tree entry), "Place in navigation" and the viewpoint deletion and edit warnings (D6), with `i18n` in en and de.

  Verify with specs:
  - list, filter, "Load more" and the empty state;
  - create, rename and delete with confirmation;
  - no write actions for a reader, and no "Place in navigation" without `viewpoint:manage`;
  - the deletion warning with 5 diagrams and without it at 0;
  - the edit warning "5 diagrams affected" when a type is removed from a used viewpoint, none at 0 or when only a type is added, and saving still possible;
  - the category and the tree entry open the diagram.

  Use `vi.waitFor` for asynchronous rendering.

## 4. Documentation and wrap-up

- [x] 4.1 The documentation of D7.
- [x] 4.2 Run the checks and verify they pass:
  - `python3 scripts/validate_links.py`;
  - `python3 scripts/check_screens.py`;
  - markdownlint;
  - `openspec validate us-074-diagrams --strict` and `openspec validate --specs --strict`;
  - `python3 scripts/check_manual.py`, plus the manual tests;
  - `python3 scripts/generate_traceability.py --check`;
  - `python3 scripts/sync_agent_config.py --check`.
