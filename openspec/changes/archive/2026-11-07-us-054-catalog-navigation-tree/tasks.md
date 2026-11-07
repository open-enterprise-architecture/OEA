# Tasks

## 1. Contract and data

- [x] 1.1 Update `api/openapi.yaml` (design D2):
  - `getExplorerView` implemented;
  - `TreeNode` and `TreeNodeItem` as implemented;
  - `createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem` and `removeTreeNodeItem` with `x-oea-authorization` (`viewpoint:manage`), the request schemas and the responses.

  Verify with Redocly lint and `python3 scripts/check_openapi_authorization.py`.
- [x] 1.2 Flyway migration `V21__navigation_tree.sql` (D1): the tables, indexes and grants, and the root "Repository". Verify with a schema test `NavigationTreeSchemaTests`:
  - the root exists once;
  - the unique folder names per parent, regardless of case;
  - the unique entry per folder;
  - the cascade on deleting a folder.

## 2. Backend

- [x] 2.1 `NavigationTree` service, store and controller (D3): reading with the visibility filter and the order; the folder and entry operations with validation, limits, permission and audit. Verify with tests in `NavigationTreeTests`:
  - "Catalog under its folder" and "Another person's personal catalog";
  - "New folder", "Duplicate name", rename, "Delete with subfolders", "Root protected";
  - "Place under a folder", "Two folders, one catalog", "Display label", a second entry in the same folder, a catalog the person cannot see;
  - "Remove one placement";
  - 403 for `viewer`;
  - the depth and the limits;
  - the audit events.
- [x] 2.2 Cucumber: a new `features/navigation/navigation-tree.feature` with the API scenarios of `navigation/navigation-tree` (US-054 AC1, AC3–AC5, REQ-050 AC1–AC3, REQ-094 AC1–AC4). Verify with the `CucumberIT` run.

## 3. Web portal

- [x] 3.1 SCR-055 sidebar (D4):
  - API functions and types;
  - `AppSidebar.vue` and `NavigationTree.vue` in the shell, with the slideover on narrow screens;
  - catalog links, the expand state, and the folder and entry actions for `viewpoint:manage`;
  - messages in English and German.

  Verify with specs `NavigationTree.spec.ts` for "Open a catalog from the tree", "Reader without the right sees no actions", new folder, a taken name, rename, delete with confirmation and "Remove from tree". Add the story `SCR-055-navigation-sidebar.stories.ts`, add SCR-055 to `docs/screens/SCREENS.md`, and run `npm run test:stories`.
- [x] 3.2 SCR-020 "Place in navigation" (D4): the modal with the folder paths and the display label, and the 422 message. Verify with specs in `CatalogPage.spec.ts` for "Place under a folder", "Display label" and the reader without the right, and a story "Place in navigation" in `SCR-020`.

## 4. Manual and documentation

- [x] 4.1 Manual in English and German: the new page `usage/navigation.md` (the sidebar, opening a catalog, folders, placing and removing catalogs, personal catalogs, the API) with `covers` US-054 and SCR-055, linked from the manual's navigation and from "Catalogs". Verify with `python3 scripts/check_manual.py` and the manual build.
- [x] 4.2 `business-objects/tree-node.md`, `docs/data-model.puml` (`tree_nodes`, `tree_node_items` as in V21) and `docs/audit-event-catalog.md` (the `tree-node.*` events) (D5).

## 5. Wrap-up

- [x] 5.1 Realign:
  - US-054 (status, AC1, technical notes);
  - REQ-050 (inputs, permission, personal catalogs, implementation note);
  - REQ-094 (names regardless of case, the root);
  - REQ-095 and REQ-097 (implementation notes for catalogs).

  Update the sprint plan. Regenerate the traceability matrix with `python3 scripts/generate_traceability.py`, and verify with `validate_links.py` and `generate_traceability.py --check`.
- [x] 5.2 Run `./mvnw verify`, the frontend type-check, tests, build and Storybook tests, markdownlint, `openspec validate --specs --strict` and all repository checks.
