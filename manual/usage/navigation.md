---
title: Navigation tree
covers:
  screens: [SCR-055]
  stories: [US-054]
---

# Navigation tree

The navigation tree arranges catalogs in folders, for example *IT Landscape > Application Inventory*, so that you find the catalog for your task without searching the list of catalogs. Everybody with the permission `entity:read` (for example the role `viewer`) sees the tree. Changing it needs the permission `viewpoint:manage` (role `enterprise-architect`).

## The sidebar

After login, the sidebar on the left of every page shows the tree. It starts at the folder *Repository*.

- **Open and close a folder:** choose the arrow or the name of the folder. The sidebar remembers which folders are open until you close the browser tab.
- **Open a catalog:** choose its entry. The catalog opens with its default view, if it has one (see [Views](./catalogs#views)).
- **Narrow screens:** the sidebar is hidden; the menu button at the left of the header opens it. Choosing a catalog closes it again.

Folders and catalogs are sorted by name, regardless of upper and lower case. An entry shows its display label if it has one, otherwise the name of the catalog. A person icon marks a personal catalog.

## Folders

With `viewpoint:manage`, the button *…* next to a folder offers:

- **New folder:** creates a folder inside it. Enter a name and choose *Create*.
- **Rename:** changes the name of the folder, the folder *Repository* included. Its entries and subfolders stay.
- **Delete:** asks first and names how many subfolders and entries the deletion removes. The folder is deleted with all its subfolders and entries. **No catalog is deleted**; the catalogs stay in the list of catalogs and in other folders. The folder *Repository* cannot be deleted.

A folder name has 1 to 255 characters and is unique within its folder, regardless of upper and lower case; otherwise the dialog says *A folder with this name already exists*. The tree has at most 10 levels below *Repository* and at most 500 folders.

## Place a catalog

1. Open the catalog and choose *Place in navigation* (needs `viewpoint:manage`).
2. Under *Folder*, choose the folder by its path, for example *Repository / IT Landscape*.
3. Optionally, enter a *Display label*. The tree then shows it instead of the name of the catalog, for example *System Inventory* for *Application Inventory*. The catalog keeps its name.
4. Choose *Place*.

A catalog can be placed in several folders, but only once per folder; a second placement in the same folder says *The catalog is already in this folder*. A folder has at most 200 entries.

## Remove a catalog from the tree

With `viewpoint:manage`, the button *×* next to an entry removes it from that folder. Only this entry is removed: the catalog itself and its entries in other folders stay.

## Personal catalogs

You can place your own personal catalog like a shared one. Only you see its entry: the tree never shows other people's personal catalogs, so it never leads to a catalog you cannot open. If you later share the catalog, everybody sees the entry.

Every change of the tree is recorded in the audit trail.

## Through the API

- `GET /views/explorer` returns the tree from the root as you see it: each folder with `id`, `name`, `parentId`, `items` and `children`; each entry with `referenceId` (the catalog ID), `displayLabel`, `name` (the catalog's current name) and `scope`.
- `POST /tree-nodes` with `name` and `parentId` creates a folder; `PATCH /tree-nodes/{nodeId}` with `name` renames it; `DELETE /tree-nodes/{nodeId}` deletes it.
- `POST /tree-nodes/{nodeId}/items` with `itemType: catalog`, `referenceId` and an optional `displayLabel` places a catalog; `DELETE /tree-nodes/{nodeId}/items/{itemId}` removes the entry.

Every change answers with the whole tree. Invalid input is rejected with 422 and names the field in `invalidFields`, for example `name` for a taken folder name or `referenceId` for a second entry in the same folder.
