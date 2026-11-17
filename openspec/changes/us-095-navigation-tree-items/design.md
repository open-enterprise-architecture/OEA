# Design

## Context

- **Schema (V21):**
  - `tree_nodes` holds `parent_id`, `sort_order` (default 0) and a single root. Sibling names are unique regardless of case.
  - `tree_node_items` holds `item_type` (allowed: `tree-node`, `entity`, `diagram`, `catalog`), `reference_id UUID NOT NULL` without a foreign key, `display_label`, `sort_order` and the index "once per folder".
  - Entities have `INTEGER` IDs, so `reference_id` cannot hold them.
- **Code:**
  - `core/navigation/NavigationTreeStore` reads nodes and visible catalog items, and creates, renames and deletes nodes and items. The store already follows ADR-051.
  - `app/navigation/NavigationTree` sorts by name and audits `tree-node.*`.
- **Contract:** `getNavigationTree`, `createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem` and `removeTreeNodeItem`. `sortOrder` is "0 until manual ordering follows (REQ-096)".
- **Frontend:**
  - `NavigationTree.vue` and `NavigationFolder.vue` form the sidebar (SCR-055).
  - `PlaceInNavigation.vue` places a catalog from the catalog page.

## Goals / Non-Goals

**Goals:**

- Entities and solutions as entries, and the marker of a deleted entity.
- The manual order.
- Moving with cycle protection.
- Drag and drop with a keyboard alternative.
- The placing actions on SCR-020 and SCR-061.

**Non-Goals:** diagrams as entries, folders as entries, several trees.

## Decisions

### D1 – Schema (`V40__navigation_tree_items.sql`)

- **Columns:**
  - `tree_node_items.reference_id` becomes nullable;
  - `entity_id INTEGER REFERENCES entities (id)` is new. Entities are only soft-deleted, so the key never blocks.
- **Checks:**
  - the item type allows `solution` in addition;
  - `item_type = 'entity'` holds exactly when `entity_id` is set and `reference_id` is null; every other type has `reference_id` and no `entity_id`.
- **Indexes:** a unique index `(tree_node_id, entity_id) WHERE entity_id IS NOT NULL` next to the existing one, and an index on `entity_id`.
- **Starting order:** an update numbers the folders per parent and the entries per folder by `lower(name)`. The entries use the current catalog name, so the visible order stays as it is.

### D2 – Store

`NavigationTreeStore` gets:

- `items(personId)`, which reads catalog, entity and solution entries with the name of their target. An entity entry carries `deleted = entities.deleted_at IS NOT NULL`. A catalog entry is filtered as today.
- `addItem` for the three kinds, appending with `max(sort_order) + 1` in the folder.
- `descendants(nodeId)`, a recursive CTE for the cycle check.
- `moveNode(id, parentId, position)` and `moveItem(id, nodeId, position)`. Both renumber the siblings densely (0…n-1) in one statement over `row_number()`. The moved element takes the position, which is clamped to the end.
- `existsEntity` (an element entity that is not deleted) and `existsSolution`.

### D3 – Service (`NavigationTree`)

- **Placing:**
  - `addItem(principal, nodeId, Placement)`, where `Placement` is the item type with `referenceId` or `entityId` and the label;
  - checks: the kind (an element entity that is not deleted, an existing solution, a visible catalog as today) and once per folder;
  - 422 names `entityId` for entities and `referenceId` for catalogs and solutions.
- **Moving a folder:** `moveNode(principal, id, parentId, position)`:
  - the root cannot move (422);
  - a parent that is the folder or a descendant gives 422 on `parentId`;
  - a name taken among the new siblings gives 422 on `name`.
- **Moving an entry:** `moveItem(principal, nodeId, itemId, targetNodeId, position)`. A target that is already in the target folder gives 422 on `referenceId` or `entityId`.
- **Audit:** `tree-node.moved` and `tree-node.item-moved`, each with `fromNodeId`, `toNodeId` and `position`. `tree-node.item-added` gets `itemType`.
- **Reading:** folders and items are ordered by `sort_order`, then by name as a tie-breaker.

### D4 – Contract (delta to `api/openapi.yaml`)

- **`TreeNodeItem`:**
  - `itemType` gains `entity` and `solution`;
  - `referenceId` becomes nullable, and `entityId` (integer or null) is new;
  - `deleted` (boolean) is new;
  - `scope` becomes nullable, for catalogs only;
  - `sortOrder` holds the manual order.
- **`TreeNodeItemCreate`:**
  - `itemType` is one of `catalog`, `entity` or `solution`;
  - `referenceId` and `entityId` are both optional, and the server requires the one that fits the item type (422).
- **`TreeNodePosition` (new):** `parentId` (uuid) for a folder or `nodeId` for an entry, and `position` (integer, 0–10000).
- **New operations:**
  - `PUT /navigation/tree/{id}/position` (`moveTreeNode`);
  - `PUT /navigation/tree/{id}/items/{itemId}/position` (`moveTreeNodeItem`).

  Both need `viewpoint:manage` and answer 200 with the whole tree, 404 and 422.

### D5 – Frontend

- **`NavigationFolder.vue`:**
  - It uses native HTML5 drag and drop (`draggable`, `dragover`, `drop`), without a new library.
  - A folder accepts dropped folders and entries. A drop on a folder appends; a drop on an entry inserts before it.
  - Invalid targets (the dragged folder itself and its descendants) do not accept the drop and show a marker.
- **Menu of each folder and entry:** "Move up", "Move down" and "Move to…". "Move to…" opens a dialog with the folder list.
- **Entries:**
  - an entity entry has the icon `i-lucide-box` and links to `/entities/{id}/history`, with the badge "Deleted" when deleted;
  - a solution entry has `i-lucide-milestone` and links to `/solutions/{id}`.
- **`PlaceInNavigation.vue`:** generalized to `{ itemType, referenceId | entityId, name }`. It is used in the catalog row menu (`CatalogTable.vue`, element rows only) and in the header of `SolutionPage.vue`.
- **`api/navigation.ts`:** `moveTreeNode`, `moveTreeNodeItem`, and the extended `addTreeNodeItem`.

### D6 – Documentation and data model

- `business-objects/tree-node.md`:
  - the item types `entity` and `solution`;
  - BR-03 (no cycles) and the manual order;
  - the marker of deleted entities.
- `docs/data-model.puml`: the columns of V40.

## Risks / Trade-offs

- **Native drag and drop** has no touch support. The menu actions "Move up", "Move down" and "Move to…" cover touch and keyboard.
- **Dense renumbering** on every move writes all siblings. Folders hold few entries, so this is accepted.

## Migration Plan

- V40 changes columns and constraints of `tree_node_items` and sets the starting order. Existing catalog rows keep `reference_id`.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.
