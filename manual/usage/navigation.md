---
title: Navigation tree
covers:
  screens: [SCR-055]
  stories: [US-054, US-094, US-095, US-096, US-097, US-132, US-133, US-135, US-136]
---

# Navigation tree

The navigation tree arranges catalogs, entities, solutions and diagrams in folders, for example *IT Landscape > Application Inventory*, so that you find what you need for your task without searching. Everybody with the permission `entity:read` (for example the role `viewer`) sees the tree. Changing it needs the permissions `viewpoint:manage` and `entity:read` (role `enterprise-architect`).

## The sidebar

After login, the sidebar on the left of every page has two tabs: *Browser* and *Navigation tree*. *Browser* is active when you log in; the tab you choose stays active while you move between pages (see [Browser](#browser)). *Navigation tree* shows the tree below. It starts at the folder *Repository*.

- **Open and close a folder:** choose the arrow or the name of the folder. The sidebar remembers which folders are open until you close the browser tab.
- **Open an entry:** choose it. A catalog opens with its default view, if it has one (see [Views](./catalogs#views)); an entity opens its page, a solution its solution page, a diagram its page.
- **Narrow screens:** the sidebar is hidden; the menu button at the left of the header opens it. Choosing an entry closes it again.

Folders and entries keep the order in which you arrange them (see [Reorganize the tree](#reorganize-the-tree)); new folders and entries go to the end. An entry shows its display label if it has one, otherwise the name of the catalog, entity, solution or diagram. An icon shows the kind of entry, and a person icon marks a personal catalog.

## Browser {#browser}

The tab *Browser* needs no manual work: it lists what the repository holds, ordered by solution. Everybody with the permission `entity:read` sees it. It shows, from top to bottom:

1. **Current state:** the objects of the repository as they are now, in the categories *Components* (entities that are not connections and not deleted), *Connections*, *Catalogs* (the catalogs you see: shared ones and your own personal ones) and *Diagrams* (all [diagrams](./diagrams) that are not deleted).
2. **Plateaus:** one node for each plateau in the status *transition* or *target*, ordered by the date from which it is valid, then by name. The baseline and realized plateaus have no node. Under a plateau you find the solutions that target it, sorted by name.
3. **Projects:** the solutions without plateaus, sorted by name.

Only active solutions appear: a solution with the status *implemented* or *archived* is not listed. The entities of an implemented solution count under *Current state*. A plateau or *Projects* without solutions stays visible, shown as *(0)*.

Each solution has the category *Components*: its changes to the repository, marked *New*, *Modified* or *Retiring*. Every category shows how many objects it holds. An empty category is greyed out, not hidden.

- **Expand a node:** choose the arrow or the name. A category loads its objects when you expand it first; it lists them by name, 50 at a time. Choose *Load more* for the next 50.
- **Open an object:** an entity opens its change history, a catalog its page, a diagram its page, and a change of a solution (and the link symbol next to a solution) the solution page.
- **Metatype:** a small label next to an entity shows the abbreviation of its metatype, if it has one.
- **Notation:** a small label next to a diagram shows the notation of its viewpoint, for example *bpmn2*. *Diagrams* lists them by name, regardless of upper and lower case, 50 at a time; a solution has no category *Diagrams*.

*Components* of *Current state* lists the root level: the entities without a parent. An entity with children shows an arrow and expands to them, by name, 50 at a time. See [Arrange components](#arrange-components) to place a component under another one. Components of a solution stay a flat list.

Arrange the folders of the tree in the tab *Navigation tree*. Functional and solution building blocks follow with their content.

## Arrange components {#arrange-components}

Real architectures are hierarchical: the module *Finance module* belongs to the system *SAP S/4HANA*. In *Current state > Components* you place a component under another one. This changes the entity itself in the catalog, not only the sidebar: it writes a new version of the child with the field *Parent* in its [change history](./change-history), and it needs the permission `entity:write` (roles `enterprise-architect`, `solution-architect`). Everybody else sees the hierarchy but cannot change it.

- **Drag and drop:** drag a component onto another component, or onto *Components* to place it at the root level. A target that cannot take it (the component itself, one of its children shown in the sidebar) shows *Cannot be dropped here*, and nothing is sent.
- **Without a mouse:** the button *…* next to a component offers *Move to…*. The dialog lists the components level by level; use the arrow to see the children of a component, the filter to narrow the loaded names, *Root level* for the root level, and *Move here* to choose the target.
- **Rules:** the parent is another component that is not deleted. A cycle (a component under its own child) and more than 10 levels are refused with *The component cannot be placed there.* A component with an open issue of a solution cannot be changed. If someone else changed the component meanwhile, the sidebar says so and reads the list again; try again then.

If the metamodel marks a [composition connection type](../administration/metamodel-editing#composition), placing a component under another one also creates a connection of this type from the parent to the child, when the type allows both entity types. Moving or detaching the component deletes the connection created this way; connections you wrote yourself stay. *Connections* counts one more connection.

### Structuring aids {#structuring-aids}

A structuring aid is a folder in the hierarchy without meaning: it groups components for navigation, for example *Finance landscape*, without adding a connection to the model. It has a folder symbol, no properties and no connections, and it is no part of a solution.

- **New structuring aid:** the button next to *Components* creates one at the root level; the entry *New structuring aid* of a component creates one below it. Enter a name of 1 to 255 characters and, optionally, a description of up to 2000 characters. This needs `entity:write`.
- **Delete:** the entry *Delete* of a structuring aid needs `entity:write` and `entity:delete` (role `enterprise-architect`). The dialog first says how many children move to the root level; they are not deleted. A structuring aid with a child that has an open issue cannot be deleted.

## Folders

With the permissions to change the tree, the button *…* next to a folder offers:

- **New folder:** creates a folder inside it. Enter a name and choose *Create*.
- **Rename:** changes the name of the folder, the folder *Repository* included. Its entries and subfolders stay.
- **Move up**, **Move down** and **Move to…:** see [Reorganize the tree](#reorganize-the-tree).
- **Delete:** asks first and names how many subfolders and entries the deletion removes. The folder is deleted with all its subfolders and entries. **No catalog, entity or solution is deleted**; they stay available and in other folders. The folder *Repository* cannot be deleted.

A folder name has 1 to 255 characters and is unique within its folder, regardless of upper and lower case; otherwise the dialog says *A folder with this name already exists*. The tree has at most 10 levels below *Repository* and at most 500 folders.

## Place a catalog, an entity, a solution or a diagram {#place-a-diagram}

1. Choose *Place in navigation*:
   - for a catalog, on the page of the catalog;
   - for an entity, in its row of a catalog (not offered in catalogs of connections);
   - for a solution, in the header of the solution page;
   - for a diagram, in the header of the page of the diagram.
2. Under *Folder*, choose the folder by its path, for example *Repository / IT Landscape*.
3. Optionally, enter a *Display label*. The tree then shows it instead of the name, for example *System Inventory* for *Application Inventory*. The catalog, entity or solution keeps its name.
4. Choose *Place*.

Each of them can be placed in several folders, but only once per folder. A folder has at most 200 entries. If the folder already holds it or is full, the dialog says so, for example *The catalog is already in this folder, or the folder is full*. A diagram that has been deleted cannot be placed; its entries in the tree are hidden until the diagram is [restored](./diagrams#restore-a-diagram).

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

- `GET /views/browser` returns the top level of the browser: `currentState` with the counts `components`, `connections`, `catalogs` and `diagrams`, `plateaus` (each with its `solutions`) and `projects`; a solution has `id`, `name`, `status` and the number of its changes in `components`.
- `GET /views/browser/items` returns one page of a category, ordered by name: `scope` (`current-state` or `solution`), `category` (`components`, `connections`, `catalogs` or `diagrams`; a solution has `components` only; an item of a diagram has the `notation` of its viewpoint), `solutionId` for a solution, and `page` and `pageSize` (1 to 200, default 50). A `pageSize` outside 1 to 200 is rejected with 400; a category that the scope does not have, or a `solutionId` that does not match the scope, with 422 naming `category` or `solutionId`; an unknown solution with 404.
- `GET /views/explorer` returns the tree from the root as you see it, in its manual order: each folder with `id`, `name`, `parentId`, `sortOrder`, `items` and `children`; each entry with `itemType` (`catalog`, `entity`, `solution` or `diagram`), `referenceId` (catalog, solution or diagram ID) or `entityId`, `displayLabel`, `name`, `scope` and `deleted`.
- `POST /tree-nodes` with `name` and `parentId` creates a folder; `PATCH /tree-nodes/{nodeId}` with `name` renames it; `DELETE /tree-nodes/{nodeId}` deletes it.
- `POST /tree-nodes/{nodeId}/items` with `itemType`, `referenceId` or `entityId` and an optional `displayLabel` places an entry; `DELETE /tree-nodes/{nodeId}/items/{itemId}` removes it.
- `PUT /tree-nodes/{nodeId}/position` with `parentId` and `position` moves a folder; `PUT /tree-nodes/{nodeId}/items/{itemId}/position` with `nodeId` and `position` moves an entry. The position counts the entries you see, from 0.

Every change answers with the whole tree. Invalid input is rejected with 422 and names the field in `invalidFields`, for example `name` for a taken folder name, `parentId` for a move that would create a cycle or go too deep, or `nodeId` for a target folder that already holds the entry or is full.
