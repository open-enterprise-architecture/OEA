# Design

## Context

- **Data model:** `docs/data-model.puml` already has `tree_nodes` (`id`, `name`, `description`, `parent_id`, `sort_order`, `created_by`, `created_at`) and `tree_node_items` (`id`, `tree_node_id` with `ON DELETE CASCADE`, `item_type`, `reference_id`, `display_label`, `sort_order`). The business object `tree-node` defines BR-01 to BR-06. No migration creates these tables yet; the last migration is `V20__metamodel_configurations.sql`.
- **Contract:** `getExplorerView` (`GET /views/explorer`) is planned and returns the root `TreeNode` with `items` and recursive `children`. No write operations exist.
- **Permissions:** `viewpoint:manage` exists in the permission catalog (`V3`) and in the role `enterprise-architect`; no operation uses it yet. Catalogs are read through `Catalogs.get` and `CatalogStore.findVisible` (ADR-032 §8).
- **Web portal:** `App.vue` renders `AppHeader` and the `RouterView`; there is no sidebar. Pages such as login and setup run without a session.
- **Catalogs:** `CatalogPage` applies the default view when the URL holds no view state (us-053), so a link `/catalogs/{id}` from the tree opens the default view.

## Goals / Non-Goals

**Goals:**

- the tables with the root, the read operation and the folder and entry operations;
- the sidebar of the web portal with the folder actions, and "Place in navigation" on the catalog page.

**Non-Goals:**

- drag and drop, manual order and moving folders (REQ-096);
- entities and diagrams as entries;
- the client app explorer (SCR-010/011).

## Decisions

### D1 Data (`V21__navigation_tree.sql`)

- **`tree_nodes`:**
  - `id UUID PK DEFAULT gen_random_uuid()`, `name TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 255)`, `description TEXT CHECK (length(description) <= 500)`;
  - `parent_id UUID REFERENCES tree_nodes (id) ON DELETE CASCADE`, `sort_order INTEGER NOT NULL DEFAULT 0`;
  - `created_by BIGINT REFERENCES persons (id)`, nullable only for the root; `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
  - Indexes: unique `(coalesce(parent_id, '00000000-0000-0000-0000-000000000000'), lower(name))` for BR-01 (names are stored trimmed); unique on `((parent_id IS NULL)) WHERE parent_id IS NULL` for exactly one root.
- **`tree_node_items`:**
  - `id UUID PK`, `tree_node_id UUID NOT NULL REFERENCES tree_nodes (id) ON DELETE CASCADE`;
  - `item_type TEXT NOT NULL CHECK (item_type IN ('tree-node', 'entity', 'diagram', 'catalog'))`, `reference_id UUID NOT NULL`;
  - `display_label TEXT CHECK (length(display_label) <= 255)`, `sort_order INTEGER NOT NULL DEFAULT 0`;
  - `created_by BIGINT NOT NULL REFERENCES persons (id)`, `created_at`.
  - Unique `(tree_node_id, item_type, reference_id)`: once per folder.
- **The root:** the migration inserts the folder "Repository" without a creator.
- **Deletion:** the cascade on `parent_id` and `tree_node_id` deletes a folder's subtree with its entries (BR-05). `reference_id` is a soft reference without a foreign key, so no catalog is touched.
- **The root is protected** in the database as well: a trigger rejects deleting the folder without parent, and a check requires a creator for every other folder.
- **Grants:** `oea_app` gets `SELECT`, `INSERT`, `UPDATE` and `DELETE` on both tables.

### D2 Contract (delta to `api/openapi.yaml`)

- **`getExplorerView`:** `x-oea-status: planned` is removed; the `501` response goes. It returns the root `TreeNode` (200).
- **`TreeNode`:** `id`, `name`, `parentId` (null for the root), `sortOrder`, `items`, `children`.
- **`TreeNodeItem`:**
  - `id`, `itemType` (`catalog` for now), `referenceId` (UUID), `displayLabel` (nullable), `sortOrder`;
  - `name`: the current name of the referenced catalog;
  - `scope`: `instance` or `personal`, for the catalog icon.
  - `metatype` and `isDeleted` are dropped; they were placeholders for entities.
- **Folder operations** (`viewpoint:manage`, tag "Views – Navigation"):
  - `createTreeNode` `POST /tree-nodes` with `{name, parentId}`: 201 with the tree; 403, 422 (`name`, `parentId`);
  - `renameTreeNode` `PATCH /tree-nodes/{nodeId}` with `{name}`: 200 with the tree; 403, 404, 422;
  - `deleteTreeNode` `DELETE /tree-nodes/{nodeId}`: 200 with the tree; 403, 404, 422 for the root.
- **Entry operations:**
  - `addTreeNodeItem` `POST /tree-nodes/{nodeId}/items` with `{itemType: catalog, referenceId, displayLabel}`: 201 with the tree; 403, 404, 422 (`itemType`, `referenceId`, `displayLabel`);
  - `removeTreeNodeItem` `DELETE /tree-nodes/{nodeId}/items/{itemId}`: 200 with the tree; 403, 404.
- Every write answers with the whole tree as the caller sees it, so the sidebar redraws from one response.
- The tag "Views – Navigation" describes the web portal sidebar too.

### D3 Service (`io.oea.app.navigation.NavigationTree`)

- **Reading:**
  - The service loads every folder and every catalog entry. Catalog entries join `catalogs` for name and scope, and drop entries on another person's personal catalog and entries without a catalog.
  - It builds the tree in memory and sorts folders and entries by `lower(name)`, the entries by their shown label.
  - The tree is small (at most 500 folders), so one statement per table is enough.
- **Writing:**
  - All writes check `viewpoint:manage` through `PermissionResolver`; a non-person principal is rejected with 403, as for catalogs.
  - The service validates the name (trimmed, 1–255 characters, no control characters), the parent, the depth (at most 10 below the root) and the limits (500 folders, 200 entries per folder).
  - A duplicate name surfaces from the unique index as 422 `name`, also under concurrent requests.
  - **Placing a catalog:** the catalog must be visible to the person (`CatalogStore.findVisible`); otherwise 422 `referenceId`, so the existence of another person's personal catalog is not revealed. A second entry in the same folder fails on the unique index (422 `referenceId`).
  - **Deleting the root:** 422 with the problem detail "The root folder cannot be deleted".
- **Audit (ADR-033):** each change writes one event with the actor and the target `tree-node` and the folder ID:
  - `tree-node.created`, `tree-node.renamed` and `tree-node.deleted` (with `name`, and for a deletion the number of removed folders and entries);
  - `tree-node.item-added` and `tree-node.item-removed` (with `itemType` and `referenceId`).

### D4 Web portal

- **Shell:** `App.vue` gains `AppSidebar.vue` beside the `RouterView`.
  - It is shown when the session holds `entity:read`, so not on login, setup or password pages.
  - It is about 18 rem wide and fixed on wide screens (`lg`).
  - On narrow screens a menu button in `AppHeader` opens it as a `USlideover`.
- **`NavigationTree.vue`:**
  - a recursive list of folders, each with an expand toggle; the root starts expanded and the state is kept per session in `sessionStorage`;
  - catalog entries as router links to `/catalogs/{id}`, with an icon by scope;
  - the tree is loaded once per session and reloaded after each write.
- **Actions with `viewpoint:manage`:**
  - a `UDropdownMenu` per folder with "New folder", "Rename" and "Delete". New folder and Rename use a small modal with the name; Delete asks for confirmation and names the number of subfolders and entries;
  - per entry "Remove from tree".
  - 422 `name` shows "A folder with this name already exists".
- **SCR-020:** "Place in navigation" (with `viewpoint:manage`) opens a modal with a `USelect` of the folders as paths ("Repository / IT Landscape") and the display label. 422 `referenceId` shows "The catalog is already in this folder".
- **Screen inventory:** the sidebar is the new screen SCR-055 "Navigation sidebar" (platform WP, UC-06 and UC-13), with a story `SCR-055-navigation-sidebar.stories.ts` (reader, manager with actions, narrow screen).

### D5 Documentation

- **`business-objects/tree-node.md`:**
  - BR-01 regardless of case;
  - the root "Repository" created by the migration;
  - the visibility of entries on personal catalogs;
  - the limits;
  - the implemented `itemType` `catalog`.
- **`docs/data-model.puml`:** `tree_nodes` and `tree_node_items` as created by `V21`, with the unique indexes.
- **`docs/audit-event-catalog.md`:** the five events.
- **Manual:** a new page `usage/navigation.md` and `de/usage/navigation.md` with `covers` US-054 and SCR-055.

## Risks / Trade-offs

- **[The tree is loaded whole]** → fine up to the limits above; a lazy tree would follow with entities in the tree.
- **[An entry may point to a personal catalog that its creator later shares or removes]** → the reading filter decides each time, so the tree follows the catalog's current scope.
- **[A sidebar on every page changes the layout of all pages]** → the pages keep their own width inside the content area; the stories of the existing screens run in their own layout without the shell, so their images do not change.

## Migration Plan

`V21__navigation_tree.sql` only adds tables and the root; a rollback drops both tables. No existing data changes.
