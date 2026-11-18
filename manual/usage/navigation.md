---
title: Navigation tree
covers:
  screens: [SCR-055]
  stories: [US-054, US-094, US-095, US-096, US-097]
---

# Navigation tree

The navigation tree arranges catalogs, entities and solutions in folders, for example *IT Landscape > Application Inventory*, so that you find what you need for your task without searching. Everybody with the permission `entity:read` (for example the role `viewer`) sees the tree. Changing it needs the permissions `viewpoint:manage` and `entity:read` (role `enterprise-architect`).

## The sidebar

After login, the sidebar on the left of every page shows the tree. It starts at the folder *Repository*.

- **Open and close a folder:** choose the arrow or the name of the folder. The sidebar remembers which folders are open until you close the browser tab.
- **Open an entry:** choose it. A catalog opens with its default view, if it has one (see [Views](./catalogs#views)); an entity opens its page, a solution its solution page.
- **Narrow screens:** the sidebar is hidden; the menu button at the left of the header opens it. Choosing an entry closes it again.

Folders and entries keep the order in which you arrange them (see [Reorganize the tree](#reorganize-the-tree)); new folders and entries go to the end. An entry shows its display label if it has one, otherwise the name of the catalog, entity or solution. An icon shows the kind of entry, and a person icon marks a personal catalog.

## Folders

With the permissions to change the tree, the button *…* next to a folder offers:

- **New folder:** creates a folder inside it. Enter a name and choose *Create*.
- **Rename:** changes the name of the folder, the folder *Repository* included. Its entries and subfolders stay.
- **Move up**, **Move down** and **Move to…:** see [Reorganize the tree](#reorganize-the-tree).
- **Delete:** asks first and names how many subfolders and entries the deletion removes. The folder is deleted with all its subfolders and entries. **No catalog, entity or solution is deleted**; they stay available and in other folders. The folder *Repository* cannot be deleted.

A folder name has 1 to 255 characters and is unique within its folder, regardless of upper and lower case; otherwise the dialog says *A folder with this name already exists*. The tree has at most 10 levels below *Repository* and at most 500 folders.

## Place a catalog, an entity or a solution

1. Choose *Place in navigation*:
   - for a catalog, on the page of the catalog;
   - for an entity, in its row of a catalog (not offered in catalogs of connections);
   - for a solution, in the header of the solution page.
2. Under *Folder*, choose the folder by its path, for example *Repository / IT Landscape*.
3. Optionally, enter a *Display label*. The tree then shows it instead of the name, for example *System Inventory* for *Application Inventory*. The catalog, entity or solution keeps its name.
4. Choose *Place*.

Each of them can be placed in several folders, but only once per folder. A folder has at most 200 entries. If the folder already holds it or is full, the dialog says so, for example *The catalog is already in this folder, or the folder is full*.

## Reorganize the tree {#reorganize-the-tree}

With the permissions to change the tree, you arrange folders and entries in the sidebar:

- **Drag and drop:** drag a folder or an entry to another place in the same folder or into another folder. Where it cannot go, the sidebar shows *Cannot be dropped here*, for example a folder dropped into its own subfolder.
- **Without a mouse:** the button *…* next to a folder or an entry offers *Move up*, *Move down* and *Move to…*. *Move to…* asks for the target folder and moves it to the end there.

A move keeps the rules of the tree: at most 10 levels, at most 200 entries per folder, unique folder names, and an entry only once per folder. Reordering within a full folder is possible. Otherwise the sidebar says *It cannot be moved there. The tree may have changed meanwhile.* If a folder or entry no longer exists because someone else changed the tree meanwhile, the sidebar reads the current tree again.

## Remove an entry from the tree

The button *×* next to an entry removes it from that folder. Only this entry is removed: the catalog, entity or solution itself and its entries in other folders stay.

## Deleted entities

When an entity is deleted, its entries stay in the tree and show the marker *Deleted*. Remove them with *×*, or restore the entity; the marker then disappears.

## Personal catalogs

You can place your own personal catalog like a shared one. Only you see its entry: the tree never shows other people's personal catalogs, so it never leads to a catalog you cannot open. If you later share the catalog, everybody sees the entry.

Every change of the tree, moves included, is recorded in the audit trail.

## Through the API

- `GET /views/explorer` returns the tree from the root as you see it, in its manual order: each folder with `id`, `name`, `parentId`, `sortOrder`, `items` and `children`; each entry with `itemType` (`catalog`, `entity` or `solution`), `referenceId` (catalog or solution ID) or `entityId`, `displayLabel`, `name`, `scope` and `deleted`.
- `POST /tree-nodes` with `name` and `parentId` creates a folder; `PATCH /tree-nodes/{nodeId}` with `name` renames it; `DELETE /tree-nodes/{nodeId}` deletes it.
- `POST /tree-nodes/{nodeId}/items` with `itemType`, `referenceId` or `entityId` and an optional `displayLabel` places an entry; `DELETE /tree-nodes/{nodeId}/items/{itemId}` removes it.
- `PUT /tree-nodes/{nodeId}/position` with `parentId` and `position` moves a folder; `PUT /tree-nodes/{nodeId}/items/{itemId}/position` with `nodeId` and `position` moves an entry. The position counts the entries you see, from 0.

Every change answers with the whole tree. Invalid input is rejected with 422 and names the field in `invalidFields`, for example `name` for a taken folder name, `parentId` for a move that would create a cycle or go too deep, or `nodeId` for a target folder that already holds the entry or is full.
