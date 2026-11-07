# Proposal

## Why

Catalogs are found only through the list *Catalogs*. As soon as a repository holds many catalogs, readers need a contextual structure such as "IT Landscape > Application Inventory" to find the catalog for their task (UC-06 step 14, UC-13, US-054, REQ-050). The navigation tree is that structure. So far it exists only as a planned contract (`getExplorerView`) and in the data model; the web portal has no tree.

This is S9 change 5 of the cut of 2026-11-05 (see `us-048-catalog-joins`): US-054 with a minimal folder management.

## Traceability

| Artifact | Reference |
|---|---|
| Use Cases | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (step 14), [UC-13: Manage Navigation Tree](../../../requirements/use-cases/UC-13-manage-navigation-tree.md) (main flow, A2, A3) |
| User Stories | [US-054: Place a Catalog in the Navigation Tree](../../../requirements/user-stories/US-054-catalog-navigation-tree.md), [US-097](../../../requirements/user-stories/US-097-navigation-tree-multiple-placement.md) (for catalogs) |
| Requirements | [REQ-050: Place a Catalog in the Navigation Tree](../../../requirements/req/REQ-050-catalog-navigation-tree.md), [REQ-094: Folder CRUD](../../../requirements/req/REQ-094-navigation-tree-folder-crud.md), [REQ-095: Soft Reference](../../../requirements/req/REQ-095-navigation-tree-item-soft-reference.md), [REQ-097: Multiple Placement](../../../requirements/req/REQ-097-navigation-tree-multiple-placement.md) |
| ADRs | [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-033: Audit Trail Hardening](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | new SCR-055 Navigation sidebar (web portal); SCR-020 Catalog Browser |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-06:

- **A global sidebar in the shell of the web portal** shows the navigation tree on every page after login. On narrow screens it collapses into a menu.
- **`viewpoint:manage` governs the whole tree:** creating, renaming and deleting folders, and placing and removing catalogs. The permission catalog already describes it as "Manage viewpoints and the navigation tree (UC-12, UC-13)". Readers with `entity:read` see the tree.
- **Personal catalogs can be placed, but only their creator sees them in the tree.** The tree hides an entry on another person's personal catalog, so it never leads to a catalog the reader cannot open.

Further scope:

- **Data:** migration `V21__navigation_tree.sql` with `tree_nodes` and `tree_node_items` as in `docs/data-model.puml`, and a root folder "Repository" created by the migration.
- **Reading:** `getExplorerView` (`GET /views/explorer`, planned so far) returns the tree from the root, with folders and catalog entries. An entry carries the catalog's current name, its display label and its scope.
- **Folders (REQ-094):**
  - `createTreeNode` (`POST /tree-nodes`), `renameTreeNode` (`PATCH /tree-nodes/{nodeId}`) and `deleteTreeNode` (`DELETE /tree-nodes/{nodeId}`);
  - names are unique among siblings regardless of case (BR-01);
  - deleting removes the subfolders and entries, never the catalogs (BR-05);
  - the root cannot be deleted (BR-02).
- **Entries (REQ-050, REQ-095, REQ-097):**
  - `addTreeNodeItem` (`POST /tree-nodes/{nodeId}/items`) places a catalog with an optional display label, and `removeTreeNodeItem` (`DELETE /tree-nodes/{nodeId}/items/{itemId}`) removes it;
  - a catalog may appear in several folders, but only once per folder.
- **Audit:** every change of the tree.
- **SCR-055 Navigation sidebar:**
  - the tree with expandable folders and catalog entries; a click opens the catalog with its default view (US-054 AC2);
  - for readers with `viewpoint:manage`: "New folder", "Rename", "Delete" and "Remove from tree".
- **SCR-020:** "Place in navigation" (with `viewpoint:manage`) opens a dialog with the folder and the display label.
- **Manual:** a new page "Navigation tree" in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-050:** the request names `itemType` and `referenceId` under the folder path; personal catalogs are visible only to their creator; the permission is `viewpoint:manage`.
- **REQ-094:** folder names are unique regardless of case; the root is the folder "Repository" created by the migration.
- **US-054:**
  - AC1 through "Place in navigation" on the catalog page instead of a context menu;
  - the technical notes: the sidebar, no drag and drop.
- **`getExplorerView`:** no longer planned; `TreeNodeItem` gains the name and scope of the referenced catalog.
- **Business object `tree-node`:** names unique regardless of case; the visibility of personal catalogs.

Assumptions, open for the owner's review:

- **Order:** folders and entries are sorted by name (case-insensitive). `sortOrder` stays 0 until drag and drop (REQ-096) follows.
- **Limits:** at most 10 levels below the root, at most 500 folders, at most 200 entries per folder. Names have 1–255 characters, display labels at most 255.
- **Moving a folder** is not part of this change; a folder can be renamed but not moved.
- **Only catalogs** can be placed for now; entities and diagrams follow with UC-13 in the client app.
- **No cleanup is needed for removed catalogs yet:** catalogs cannot be deleted so far. The tree skips an entry whose catalog no longer exists.

## Out of Scope

- Drag and drop and manual ordering (REQ-096, US-097 drag parts).
- Entities and diagrams in the tree (UC-13 in the client app, SCR-010/011).
- Folder descriptions in the UI (the API keeps them).
- Personal folders.

## Capabilities

### New Capabilities

- `navigation/navigation-tree`: the folders of the navigation tree (create, rename, delete, root), the catalog entries (place, remove, multiple placement, visibility), reading the tree, the permissions and the sidebar of the web portal.

### Modified Capabilities

None. Opening a catalog from the tree uses the existing default view (`catalog/catalog-views`).

## Impact

- `api/openapi.yaml`:
  - `getExplorerView` implemented;
  - `createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem` and `removeTreeNodeItem` with their schemas;
  - `TreeNode` and `TreeNodeItem` as implemented.
- `backend/`:
  - migration `V21__navigation_tree.sql`;
  - a new package `io.oea.app.navigation` (service) with `io.oea.core.navigation` (store) and `io.oea.api.navigation` (controller);
  - audit events.
- `frontend/`:
  - the sidebar in the shell (`App.vue`), the tree component, the folder actions and "Place in navigation" on the catalog page;
  - the story for SCR-055 and messages in English and German.
- `manual/`: a new page "Navigation tree" in English and German.
- Documentation:
  - `business-objects/tree-node.md`, `docs/data-model.puml`, `docs/audit-event-catalog.md`, `docs/screens/SCREENS.md`;
  - US-054, REQ-050, REQ-094, REQ-095 and REQ-097;
  - the sprint plan.
