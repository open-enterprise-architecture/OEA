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
- `addItem` for the three kinds, appending with `max(sort_order) + 1` in the folder; `createNode` appends a folder the same way among its siblings.
- `descendants(nodeId)`, a recursive CTE for the cycle check.
- `moveNode(id, parentId, position)` and `moveItem(id, nodeId, position)`. Both renumber the siblings densely (0…n-1) in one statement over `row_number()`. The moved element takes the position, which is clamped to the end.
- **Position of an entry (decision of the architect, 2026-11-17):** the position counts the entries the caller sees, because the client sends the index it shows. The service places the entry directly before the visible entry that holds the position once the moved entry is taken out, or after the last visible entry when the position is past the end. Hidden entries (another person's personal catalogs) keep their order relative to the others. Folders are visible to every reader, so a folder position counts all siblings.
- `existsEntity` (an element entity that is not deleted) and `existsSolution`.

### D3 – Service (`NavigationTree`)

- **Placing:**
  - `addItem(principal, nodeId, Placement)`, where `Placement` is the item type with `referenceId` or `entityId` and the label;
  - checks: the kind (an element entity that is not deleted, an existing solution, a visible catalog as today) and once per folder;
  - the reference field must fit the item type: `entityId` for `entity`, `referenceId` for `catalog` and `solution`. A missing fitting field, or a field of the other kind that is set, gives 422 naming that field;
  - 422 names `entityId` for entities and `referenceId` for catalogs and solutions.
- **Moving a folder:** `moveNode(principal, id, parentId, position)`:
  - the root cannot move (422 without a field, as for its deletion);
  - an unknown parent, a parent that is the folder or a descendant, and a name taken among the new siblings give 422 on `parentId`.
- **Moving an entry:** `moveItem(principal, nodeId, itemId, targetNodeId, position)`. An unknown target folder, and a target folder that already holds the entry's target, give 422 on `nodeId`.
- **Fields named by 422 (decision of the architect, 2026-11-17):** `invalidFields` names fields of the request body. The move bodies carry only `parentId` or `nodeId` and `position`, so a taken name and a duplicate target are named by the target folder field (the proposal had `name` and `referenceId`); the detail text tells the reasons apart.
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
- **`TreeNodeMove` (new):** `parentId` (uuid) and `position` (integer, 0–10000), both required.
- **`TreeNodeItemMove` (new):** `nodeId` (uuid, the target folder) and `position` (integer, 0–10000), both required.
- **New operations (decision of the architect, 2026-11-17):** the paths follow the existing tree writes under `/tree-nodes/{nodeId}` instead of the `/navigation/tree/...` of the proposal:
  - `PUT /tree-nodes/{nodeId}/position` (`moveTreeNode`);
  - `PUT /tree-nodes/{nodeId}/items/{itemId}/position` (`moveTreeNodeItem`).

  Both need `viewpoint:manage` and `entity:read` (D7) and answer 200 with the whole tree, 400 (malformed path ID), 403, 404, 413, 422 and 429. Their controller methods carry `@ContractViolationResponse(status = 422)` like the other tree writes, so a position outside 0–10000 or a missing field answers 422 naming the field (`ApiExceptionHandler` answers 400 only for operations without the annotation).
- **Descriptions:** `TreeNode`, `TreeNodeItem` and `getExplorerView` describe the manual order instead of the order by name.

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
- **Permissions in the UI:** the sidebar actions, drag and drop and "Place in navigation" are offered only to holders of both `viewpoint:manage` and `entity:read` (D7).

### D6 – Documentation and data model

- `business-objects/tree-node.md`:
  - the item types `entity` and `solution`;
  - BR-03 (no cycles) and the manual order;
  - the marker of deleted entities.
- `docs/data-model.puml`: the columns of V40.

### D7 – Security (added by the security engineer)

- **Authorization:**
  - every write of the tree – `createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem`, `removeTreeNodeItem`, `moveTreeNode` and `moveTreeNodeItem` – declares `{"access": "permission", "permissions": ["viewpoint:manage", "entity:read"]}`. Every write answers with the whole tree including entity and solution names, which `getExplorerView` gives only with `entity:read`. Custom roles (ADR-032 §5) can hold `viewpoint:manage` alone, and the response would otherwise disclose the names of the tree, and of any entity ID such a person places. The architect extended the requirement from the three placing and moving operations to all tree writes (2026-11-17), because each of them returns the same tree; the main spec requirement "Who changes the tree" is modified accordingly.
  - The built-in roles that hold `viewpoint:manage` also hold `entity:read`, so no existing user loses a function.
  - Deleted entities stay visible by name in the tree. This discloses nothing new: their history is readable with `entity:read` (`getEntityHistory`).
- **Addressing (404 vs 422):**
  - path IDs that do not resolve (folder, entry, entry of another folder) answer 404; `moveTreeNodeItem` looks the entry up with `findItem(nodeId, itemId)` as `removeItem` does;
  - an entry on another person's personal catalog is treated as unknown (404) by move and remove, so its existence is not confirmed; the store reads it through the same visibility filter as `items(personId)`;
  - IDs in the body that do not resolve (target folder, entity, solution) answer 422 naming the field, as for catalogs today.
- **Limits on moves:** the move checks reuse the constants of `NavigationTree`:
  - `MAX_DEPTH` (10) on the depth of the target parent plus the height of the moved subtree;
  - `MAX_ITEMS_PER_FOLDER` (200) on the target folder, only when the entry changes folder;
  - the position 0–10000 from the contract, mapped to 422 naming `position` by `@ContractViolationResponse(status = 422)` on both move operations (checked in `ApiExceptionHandler.contractViolation`).

  Without the depth check a move would bypass the limit of `createTreeNode`. Recursive reads of a tree with unbounded depth would then grow without bound.
- **Concurrency:** every write takes `NavigationTreeStore.lock()` before it reads the nodes, as the existing writes do. The cycle check, the depth check and the renumbering run inside that lock, in one transaction. Without it two concurrent opposite moves both pass the cycle check and form a cycle, which the recursive CTE of `descendants` and the tree read would then follow forever. `descendants` is also bounded by `MAX_DEPTH + 1` levels as a second line of defense. The tree has no version and no `If-Match`, so the last of two serialized moves wins, which is accepted for a tree of folders.
- **Audit:** details carry IDs, the item type, folders and the position only. Display labels are free text and may hold personal data, so they are not copied into the append-only trail (ADR-024). `tree-node.item-removed` names `entityId` for an entity entry; the current code reads `referenceId()` unconditionally, which would fail for an entity entry.
- **Output encoding:** entity names are written by every holder of `entity:write` and the sidebar is shown on every page, administrators included. Names and labels are rendered with text interpolation only (no `v-html`), and links are built by the router from `itemType` and the ID.
- **Drag and drop:** the drop handler takes the dragged element from component state, not from `dataTransfer` text, so content dropped from outside the page cannot trigger a move.
- **Rate limits:** the new operations fall under the global rate limit (429) like the other tree writes; no extra limit is needed, because each write is bounded by the limits above.

## Risks / Trade-offs

- **Native drag and drop** has no touch support. The menu actions "Move up", "Move down" and "Move to…" cover touch and keyboard.
- **Dense renumbering** on every move writes all siblings. Folders hold few entries, so this is accepted.

## Migration Plan

- V40 changes columns and constraints of `tree_node_items` and sets the starting order. Existing catalog rows keep `reference_id`.

## Open Questions

- None. The assumptions of the proposal are open for review in the proposal pull request.
