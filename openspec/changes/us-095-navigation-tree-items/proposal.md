# Proposal

## Why

The navigation tree of S9 holds folders and catalogs, sorted by name. UC-13 asks for more:

- the repository objects themselves as entries, without changing them (US-095);
- a structure that architects arrange by drag and drop (US-096);
- the same object in several folders (US-097).

US-094 (folders) is already met by the tree of S9.

This change brings:

- entities and solutions as entries, next to catalogs;
- the marker of an entry whose entity was deleted;
- moving folders and entries;
- the manual order.

The S15 row of the sprint plan lists the stories:

- [US-095](../../../requirements/user-stories/US-095-navigation-tree-item-soft-reference.md);
- [US-096](../../../requirements/user-stories/US-096-navigation-tree-drag-drop.md);
- [US-097](../../../requirements/user-stories/US-097-navigation-tree-multiple-placement.md);
- [US-094](../../../requirements/user-stories/US-094-navigation-tree-folder-crud.md), which is only set to done.

This is S15 change 2, the last change of S15.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-13](../../../requirements/use-cases/UC-13-manage-navigation-tree.md) |
| User Story | [US-094](../../../requirements/user-stories/US-094-navigation-tree-folder-crud.md) (AC1–AC4, met by S9), [US-095](../../../requirements/user-stories/US-095-navigation-tree-item-soft-reference.md) (AC1, AC2), [US-096](../../../requirements/user-stories/US-096-navigation-tree-drag-drop.md) (AC1–AC3), [US-097](../../../requirements/user-stories/US-097-navigation-tree-multiple-placement.md) (AC1, AC2) |
| Requirements | [REQ-094](../../../requirements/req/REQ-094-navigation-tree-folder-crud.md), [REQ-095](../../../requirements/req/REQ-095-navigation-tree-item-soft-reference.md), [REQ-096](../../../requirements/req/REQ-096-navigation-tree-drag-drop.md), [REQ-097](../../../requirements/req/REQ-097-navigation-tree-multiple-placement.md) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`viewpoint:manage`), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) (SQL only in `core`) |
| Sprint / Milestone | S15 / Milestone 3 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions of 2026-11-17:**

- **Entries:** the tree holds entities, catalogs and solutions.
- **Manual order:** folders and entries keep the order set by drag and drop, per parent folder, and new ones go to the end. The current order by name becomes the starting order once.
- **Placing:** entities and solutions are placed with the action "Place in navigation" on their pages, using the same dialog as for catalogs. Dragging inside the tree moves folders and entries.

**Assumptions recorded here (minor details, open for review):**

- **Entities:**
  - "Place in navigation" is a row action of the catalog browser (SCR-020). Choosing the entry in the tree opens the change history of the entity (SCR-026), the only page of an entity so far.
  - An entry shows the current name of the entity.
  - An entity that is deleted later stays in the tree with a warning marker "Deleted" (US-095 AC2), and can be removed. A restored entity loses the marker.
  - A connection cannot be placed (422), because the catalog lists elements only.
- **Solutions:** "Place in navigation" sits on the page of the solution (SCR-061). Choosing the entry opens the page. Solutions cannot be deleted, so their entries are never marked.
- **Catalogs** behave as today: an entry of a deleted catalog, or of another person's personal catalog, is left out.
- **Moving:**
  - `moveTreeNode` takes a new parent and a position, and `moveTreeNodeItem` takes a target folder and a position.
  - Moving a folder under itself or under one of its descendants gives 422 naming `parentId` (US-096 AC1). The root cannot be moved (422).
  - A duplicate name among the new siblings gives 422 naming `name`. An entry whose target is already in the new folder gives 422 naming `referenceId`.
  - The position is 0-based and clamped to the end of the list. The siblings move up or down to keep a dense order.
- **Keyboard and narrow screens:** besides drag and drop, each folder and entry offers "Move up", "Move down" and "Move to…" (a folder choice).
- **Permissions and audit:** as today, changing the tree needs `viewpoint:manage`. Moves are audited as `tree-node.moved` and `tree-node.item-moved`, and placements as `tree-node.item-added` with the item type.

**Navigation tree (`navigation`):**

- `TreeNodeItem` gets the item types `entity` and `solution`, `entityId` for an entity, and `deleted` for an entry whose entity was deleted.
- `TreeNodeItemCreate` takes `referenceId` (catalog, solution) or `entityId` (entity).
- `sortOrder` carries the manual order. Folders and entries are returned in that order.
- `moveTreeNode` (`PUT /navigation/tree/{id}/position`) and `moveTreeNodeItem` (`PUT /navigation/tree/{id}/items/{itemId}/position`) are new.

**Frontend:**

- In the sidebar (SCR-055), folders and entries can be dragged. "Move up", "Move down" and "Move to…" are in the menu of each folder and entry. Entities and solutions have their own icon, and deleted entities have a warning marker.
- SCR-020 offers "Place in navigation" as a row action, and SCR-061 offers it in the header. Both use `PlaceInNavigation.vue`.

## Out of Scope

- Diagrams as entries: they follow with the diagrams.
- Folders as entries of other folders (`tree-node` items).
- Several trees or personal trees.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `navigation/navigation-tree`:
  - the order of folders and entries;
  - entities and solutions as entries, with the marker of a deleted entity;
  - moving folders and entries, with the cycle protection;
  - the placing actions on SCR-020 and SCR-061;
  - drag and drop in the sidebar.

## Impact

- **api/openapi.yaml:**
  - the item types and fields of `TreeNodeItem` and `TreeNodeItemCreate`;
  - `moveTreeNode`, `moveTreeNodeItem` and `TreeNodePosition`;
  - the descriptions of the order.
- **backend:**
  - migration `V40__navigation_tree_items.sql`:
    - `reference_id` nullable, plus `entity_id INTEGER REFERENCES entities (id)`;
    - the item type `solution`, with checks that pair item type and reference;
    - the starting order by name.
  - `core/navigation/NavigationTreeStore`, extended (ADR-051);
  - `NavigationTree` with placing, moving and ordering;
  - the audit events.
- **frontend:**
  - `NavigationTree.vue` and `NavigationFolder.vue` with drag and drop and the move actions;
  - `PlaceInNavigation.vue` for entities and solutions;
  - `CatalogTable.vue` and `SolutionPage.vue`;
  - `api/navigation.ts`;
  - the stories of SCR-055, SCR-020 and SCR-061.
- **Documentation:**
  - US-094 to US-097 set to done, and REQ-094 to REQ-097 and UC-13 realigned;
  - `business-objects/tree-node.md` and `docs/data-model.puml`;
  - `docs/audit-event-catalog.md`;
  - `docs/screens/SCREENS.md`;
  - the user manual "Navigation tree" in English and German;
  - the S15 row of the sprint plan.
