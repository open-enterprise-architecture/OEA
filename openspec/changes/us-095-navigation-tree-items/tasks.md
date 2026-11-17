# Tasks

## 1. Contract

- [ ] 1.1 `api/openapi.yaml` (design D4): the item types and fields of `TreeNodeItem` and `TreeNodeItemCreate`, `TreeNodePosition`, `moveTreeNode` and `moveTreeNodeItem`, and the descriptions of the order.

  Verify: Redocly lint and both authorization scripts pass.

## 2. Backend

- [ ] 2.1 `V40__navigation_tree_items.sql` and `NavigationTreeStore` (D1, D2).

  Verify with a migration test:
  - the checks of item type and reference, for both kinds;
  - one entity once per folder;
  - the starting order by name for folders and entries;
  - the existing catalog entries are unchanged.
- [ ] 2.2 `NavigationTree`: placing, moving and order (D3), and the controller.

  Verify with `NavigationTreeTests`:
  - an entity is placed at the end and its version is unchanged; the same entity goes in two folders, and a second time in one folder gives 422;
  - a connection, an unknown entity and a deleted entity give 422;
  - a solution is placed, an unknown solution gives 422, and a solution twice in one folder gives 422;
  - a deleted entity is marked, a restored one is no longer marked, and removing one entry keeps the other;
  - a folder moves with its content; a move under itself, under a child or under a grandchild gives 422; moving the root gives 422; a name taken among the new siblings gives 422;
  - an entry is reordered: position 0 gives the first place, the position of the last entry gives the last place, and position 10000 is clamped to the end;
  - an entry moves to another folder, and a target already in that folder gives 422;
  - the order is dense after every move;
  - the permissions: 403 without `viewpoint:manage`;
  - the audit of the moves and of `itemType`, and no event on a rejection.
- [ ] 2.3 Cucumber: extend `features/navigation/navigation-tree.feature` with the scenarios of the delta spec.

  Verify that it passes, together with the navigation scenarios.

## 3. Frontend

- [ ] 3.1 Stories first:
  - SCR-055 with entities and solutions, a deleted entity, drag and drop and the move menu;
  - SCR-020 with "Place in navigation" on a row;
  - SCR-061 with "Place in navigation".

  Verify that the stories render without a11y violations.
- [ ] 3.2 `NavigationFolder.vue` and `NavigationTree.vue` with drag and drop and the move actions; `api/navigation.ts` (D5). Texts go in `i18n` en/de.

  Verify with the specs:
  - a drop sends the move with the position;
  - an invalid target refuses the drop;
  - "Move up" is not offered on the first entry, and "Move down" not on the last;
  - "Move to…" offers no folder the element cannot move to;
  - entity and solution entries link correctly, and a deleted entity is marked;
  - a reader can neither drag nor move.
- [ ] 3.3 `PlaceInNavigation.vue` for entities and solutions in `CatalogTable.vue` and `SolutionPage.vue` (D5).

  Verify with the specs:
  - the action appears only with `viewpoint:manage`, and only on element rows;
  - the request carries `entityId` or `referenceId`.
- [ ] 3.4 `docs/screens/SCREENS.md`: SCR-055, SCR-020 and SCR-061 name the additions; the screen images.

  Verify with `python3 scripts/check_screens.py`.

## 4. Documentation and wrap-up

- [ ] 4.1 Update the documentation:
  - `business-objects/tree-node.md` and `docs/data-model.puml` (D6);
  - `docs/audit-event-catalog.md`;
  - UC-13 and REQ-094 to REQ-097, realigned;
  - the user manual "Navigation tree" in English and German.
- [ ] 4.2 Set US-094 to US-097 to done. Update the S15 row, and regenerate `requirements/traceability.md`.
- [ ] 4.3 Run `python3 scripts/validate_links.py`, markdownlint, `openspec validate --strict` and `check_manual.py`, and verify they pass.
