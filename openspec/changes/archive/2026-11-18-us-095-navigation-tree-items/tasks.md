# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml` (design D4): the item types and fields of `TreeNodeItem` and `TreeNodeItemCreate`, `TreeNodeMove` and `TreeNodeItemMove`, `moveTreeNode` (`PUT /tree-nodes/{nodeId}/position`) and `moveTreeNodeItem` (`PUT /tree-nodes/{nodeId}/items/{itemId}/position`), and the descriptions of the order.

  Security (D7): every tree write declares `viewpoint:manage` and `entity:read`; `position` has `minimum: 0` and `maximum: 10000`; the new operations list 400, 401, 403, 404, 413, 422 and 429.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [x] 2.1 `V40__navigation_tree_items.sql` and `NavigationTreeStore` (D1, D2).

  Verify with a migration test:
  - the checks of item type and reference, for both kinds;
  - one entity once per folder;
  - the starting order by name for folders and entries;
  - the existing catalog entries are unchanged.
- [x] 2.2 `NavigationTree`: placing, moving and order (D3), and the controller. `moveTreeNode` and `moveTreeNodeItem` carry `@ContractViolationResponse(status = 422)` like the other tree writes.

  Verify with `NavigationTreeTests`:
  - a new folder and a new entry of each item type go to the end, and the order of the others is unchanged;
  - an entity is placed at the end and its version is unchanged; the same entity goes in two folders, and a second time in one folder gives 422;
  - a connection, an unknown entity and a deleted entity give 422; an item type without its fitting field (`entityId` for `entity`, `referenceId` otherwise), or with the field of the other kind, gives 422 naming that field;
  - a solution is placed, an unknown solution gives 422, and a solution twice in one folder gives 422;
  - a deleted entity is marked, a restored one is no longer marked, and removing one entry keeps the other;
  - a folder moves with its content; an unknown parent, a move under itself, under a child or under a grandchild, and a name taken among the new siblings give 422 naming `parentId`; moving the root gives 422;
  - an entry is reordered: position 0 gives the first place, the position of the last entry gives the last place, and position 10000 is clamped to the end;
  - the position counts the entries the caller sees: with another person's personal catalog in the folder, the entry lands before the visible entry at that position, and the hidden entry keeps its order relative to the others;
  - an entry moves to another folder; an unknown target folder and a target already in that folder give 422 naming `nodeId`;
  - the order is dense after every move;
  - the permissions: 403 without `viewpoint:manage`, and 403 without `entity:read` for every tree write (create, rename, delete, place, remove, both moves);
  - the audit of the moves and of `itemType`, and no event on a rejection.
- [x] 2.3 Security of placing and moving (D7).

  Verify with `NavigationTreeTests` and controller tests:
  - every tree write (`createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem` for each item type, `removeTreeNodeItem`, `moveTreeNode`, `moveTreeNodeItem`) with `viewpoint:manage` but without `entity:read` gives 403, and the tree is unchanged;
  - an unknown folder or entry in the path, and an entry addressed with another folder, give 404;
  - moving or removing the entry of another person's personal catalog gives 404, and the entry stays;
  - entries per folder: a move into a folder with 199 entries succeeds, into one with 200 gives 422 naming `nodeId`, and reordering within a folder of 200 succeeds; placing an entity or a solution into a folder of 200 gives 422;
  - depth: a subtree whose deepest folder ends exactly 10 levels below the root is accepted, one ending 11 levels below is rejected with 422 naming `parentId`;
  - position (controller test through `ApiExceptionHandler`, parameterized, for both moves): 0 and 10000 accepted, -1 and 10001 give 422 naming `position`; a missing `position` gives 422 naming it;
  - two opposite folder moves run concurrently in two threads: exactly one succeeds, and `descendants` of each folder terminates without a cycle;
  - audit details carry no display label and no entity name; `tree-node.item-removed` of an entity entry names `entityId`; a move to the same place writes no event.
- [x] 2.4 Cucumber: extend `features/navigation/navigation-tree.feature` with the scenarios of the delta spec.

  Verify that it passes, together with the navigation scenarios.

## 3. Frontend

- [x] 3.1 Stories first:
  - SCR-055 with entities and solutions, a deleted entity, drag and drop and the move menu;
  - SCR-020 with "Place in navigation" on a row;
  - SCR-061 with "Place in navigation".

  Verify that the stories render without a11y violations.
- [x] 3.2 `NavigationFolder.vue` and `NavigationTree.vue` with drag and drop and the move actions; `api/navigation.ts` (D5). Texts go in `i18n` en/de.

  Verify with the specs:
  - a drop sends the move with the position;
  - an invalid target refuses the drop;
  - "Move up" is not offered on the first entry, and "Move down" not on the last;
  - "Move to…" offers no folder the element cannot move to;
  - entity and solution entries link correctly, and a deleted entity is marked;
  - a reader, and a holder of `viewpoint:manage` without `entity:read`, can neither drag nor move;
  - an entity named `<img src=x onerror=alert(1)>` and a display label with markup are shown literally (D7);
  - a drop whose source is not an element of the tree (e.g. text dragged from another window) sends no request (D7).
- [x] 3.3 `PlaceInNavigation.vue` for entities and solutions in `CatalogTable.vue` and `SolutionPage.vue` (D5).

  Verify with the specs:
  - the action appears only with `viewpoint:manage` and `entity:read`, and only on element rows;
  - the request carries `entityId` or `referenceId`.
- [x] 3.4 `docs/screens/SCREENS.md`: SCR-055, SCR-020 and SCR-061 name the additions; the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [x] 4.1 Update the documentation:
  - `business-objects/tree-node.md` and `docs/data-model.puml` (D6);
  - `docs/audit-event-catalog.md`: `tree-node.moved` and `tree-node.item-moved` with `fromNodeId`, `toNodeId` and `position`, the item types `entity` and `solution` with `entityId` or `referenceId`, and the note that display labels are not recorded (D7);
  - UC-13 and REQ-094 to REQ-097, realigned;
  - the user manual "Navigation tree" in English and German.
- [x] 4.2 Set US-094 to US-097 to done. Update the S15 row, and regenerate `requirements/traceability.md`.
- [x] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.
