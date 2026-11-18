# navigation/navigation-tree Specification

## Purpose

Defines the navigation tree of the repository: its folders with the root, the catalog entries placed in them, how readers see the tree, who may change it, and how the web portal shows it as a sidebar.

## Requirements

### Requirement: Read the tree

A reader with `entity:read` SHALL read the tree from the root "Repository" in its manual order (`sortOrder`). An entry SHALL show its display label, else its target's current name. Another person's personal catalog or a missing catalog SHALL be left out; a deleted entity's entry SHALL stay, marked as deleted.

Trace: US-054, US-095, US-096 AC2, REQ-050, REQ-095, REQ-096, UC-13 step 2, owner decisions 2026-11-06 and 2026-11-17

#### Scenario: Catalog under its folder

- **GIVEN** the catalog "Application Inventory" is placed in the folder "IT Landscape"
- **WHEN** Franz reads the tree
- **THEN** "IT Landscape" lists "Application Inventory"

#### Scenario: Another person's personal catalog

- **GIVEN** Kurt placed his personal catalog "Kurt's Candidates" in "IT Landscape"
- **WHEN** Franz reads the tree
- **THEN** "IT Landscape" does not list "Kurt's Candidates", and Kurt's tree does

#### Scenario: Manual order

- **GIVEN** "IT Landscape" holds "SAP ERP", then "Application Inventory" in this order
- **WHEN** Franz reads the tree
- **THEN** the entries come in this order, not by name

### Requirement: Create a folder

A folder SHALL be created under an existing folder with a name of 1–255 characters that is unique among its siblings regardless of case. A duplicate or invalid name SHALL be rejected with 422 naming `name`; an unknown parent SHALL be rejected with 422 naming `parentId`. A folder SHALL be at most 10 levels below the root, and the tree SHALL have at most 500 folders.

Trace: REQ-094 AC1, AC2, tree-node BR-01, UC-13 steps 3–6

#### Scenario: New folder

- **WHEN** Kurt creates the folder "IT Landscape" under the root
- **THEN** the tree shows "IT Landscape" under "Repository"

#### Scenario: Duplicate name

- **GIVEN** the root has the folder "IT Landscape"
- **WHEN** Kurt creates the folder "it landscape" under the root
- **THEN** the request is rejected with 422 naming `name`

### Requirement: Rename a folder

A folder, the root included, SHALL be renamable under the rules for the name of a new folder.

Trace: REQ-094, UC-13

#### Scenario: Rename

- **WHEN** Kurt renames "IT Landscape" to "Application Landscape"
- **THEN** the tree shows "Application Landscape" with the same entries

### Requirement: Delete a folder

Deleting a folder SHALL remove its subfolders and all their entries and SHALL NOT change any catalog. The root SHALL NOT be deletable; such a request SHALL be rejected with 422.

Trace: REQ-094 AC3, AC4, tree-node BR-02, BR-05, UC-13 A3

#### Scenario: Delete with subfolders

- **GIVEN** "IT Landscape" has a subfolder with the catalog "Application Inventory"
- **WHEN** Kurt deletes "IT Landscape"
- **THEN** both folders are gone and `GET /catalogs/{id}` of "Application Inventory" still answers 200

#### Scenario: Root protected

- **WHEN** Kurt deletes the root
- **THEN** the request is rejected with 422

### Requirement: Place a catalog

A catalog SHALL be placed in a folder as an entry with an optional display label of at most 255 characters. The same catalog MAY be placed in several folders but only once per folder; a second entry in the same folder SHALL be rejected with 422 naming `referenceId`. A catalog the person cannot see SHALL be rejected with 422 naming `referenceId`. A folder SHALL have at most 200 entries.

Trace: US-054 AC1, AC3, AC5, REQ-050 AC1, AC2, REQ-097, tree-node BR-04, BR-06

#### Scenario: Place under a folder

- **WHEN** Kurt places "Application Inventory" in "IT Landscape"
- **THEN** the tree lists it under "IT Landscape"

#### Scenario: Two folders, one catalog

- **WHEN** Kurt also places "Application Inventory" in "Compliance"
- **THEN** both folders list it, and the repository still has one catalog "Application Inventory"

#### Scenario: Display label

- **WHEN** Kurt places "Application Inventory" with the display label "System Inventory"
- **THEN** the tree shows "System Inventory", and the catalog is still called "Application Inventory"

### Requirement: Remove a catalog from the tree

Removing an entry SHALL remove only that entry; the catalog and its other entries SHALL stay.

Trace: US-054 AC4, REQ-050 AC3, REQ-095, tree-node BR-05

#### Scenario: Remove one placement

- **GIVEN** "Application Inventory" is placed in "IT Landscape" and "Compliance"
- **WHEN** Kurt removes it from "IT Landscape"
- **THEN** "Compliance" still lists it and `GET /catalogs/{id}` answers 200

### Requirement: Who changes the tree

Folders and entries SHALL be changed only with `viewpoint:manage` and `entity:read`, because every change answers with the tree and the names of its targets; any other request SHALL be rejected with 403, and nothing SHALL change. Every change SHALL be audited with the actor.

Trace: UC-13, ADR-032 §8, ADR-033, owner decision 2026-11-06, security review us-095-navigation-tree-items

#### Scenario: Reader without the right

- **WHEN** Franz, holding only `viewer`, creates a folder
- **THEN** the request is rejected with 403

#### Scenario: Manager without read permission

- **GIVEN** Eva holds a custom role with `viewpoint:manage` but without `entity:read`
- **WHEN** she creates a folder, places the entity "SAP ERP" in "Finance" or moves "Finance" under "Initiatives"
- **THEN** the request is rejected with 403, and the tree is unchanged

### Requirement: Navigation sidebar

The web portal SHALL show a sidebar on every page after login, with the tree of expandable folders in its tab "Navigation tree"; on narrow screens it SHALL open from a menu button. Choosing a catalog entry SHALL open the catalog. Readers with `viewpoint:manage` SHALL be offered "New folder", "Rename", "Delete" and "Remove from tree", and on the catalog page "Place in navigation".

Trace: US-054 AC1, AC2, AC4, US-132 AC1, UC-13 step 1–2, owner decisions 2026-11-06 and 2026-11-18

#### Scenario: Open a catalog from the tree

- **GIVEN** "Application Inventory" is placed in "IT Landscape" and has the default view "Compact view"
- **WHEN** Franz chooses it in the tab "Navigation tree"
- **THEN** the catalog opens with "Compact view"

#### Scenario: Reader without the right sees no actions

- **WHEN** Franz, holding only `viewer`, opens the tab "Navigation tree"
- **THEN** it offers no "New folder", "Rename", "Delete" or "Remove from tree"

### Requirement: New folders and entries go to the end

A new folder SHALL be placed after its siblings, and a new entry of any item type after the entries of its folder; the order of the others SHALL NOT change.

Trace: US-096 AC2, REQ-096, owner decision 2026-11-17

#### Scenario: New folder

- **GIVEN** the root holds "Initiatives", then "Finance" in this order
- **WHEN** Kurt creates the folder "Archive" under the root
- **THEN** the root holds "Initiatives", "Finance", "Archive"

### Requirement: Place an entity

A person with `viewpoint:manage` SHALL place an element entity in a folder as an entry with an optional display label, at the end of the folder. The entity SHALL NOT change. A connection, an unknown or a deleted entity SHALL be rejected with 422 naming `entityId`; the same entity twice in one folder SHALL be rejected with 422 naming `entityId`.

Trace: US-095, US-097 AC1, REQ-095, REQ-097, owner decision 2026-11-17

#### Scenario: Entity placed

- **WHEN** Kurt places the application "SAP ERP" in "Finance"
- **THEN** "Finance" lists "SAP ERP" as its last entry, and the entity has its version unchanged

#### Scenario: Same entity in two folders

- **GIVEN** "SAP ERP" is placed in "Finance"
- **WHEN** Kurt places it in "Cloud Migration"
- **THEN** both folders list it as independent entries

#### Scenario: Connection

- **WHEN** Kurt places the data flow "Orders" in "Finance"
- **THEN** the response is 422 naming `entityId`

### Requirement: Place a solution

A person with `viewpoint:manage` SHALL place a solution in a folder as an entry with an optional display label, at the end of the folder, once per folder; an unknown solution or a second entry in the folder SHALL be rejected with 422 naming `referenceId`.

Trace: US-095, US-097, owner decision 2026-11-17

#### Scenario: Solution placed

- **WHEN** Kurt places the solution "CRM Replacement" in "Initiatives"
- **THEN** "Initiatives" lists "CRM Replacement"

### Requirement: Deleted entity in the tree

An entity entry whose entity is deleted SHALL stay in every folder and SHALL be marked as deleted; removing it SHALL remove only that entry. When the entity is restored, the marker SHALL go.

Trace: US-095 AC1, AC2, US-097 AC2, REQ-095

#### Scenario: Two marked entries

- **GIVEN** "SAP ERP" is placed in "Finance" and "Cloud Migration"
- **WHEN** "SAP ERP" is deleted
- **THEN** both entries show the marker "Deleted"

#### Scenario: Remove one entry

- **GIVEN** "SAP ERP" is placed in "Finance" and "Cloud Migration"
- **WHEN** Kurt removes it from "Finance"
- **THEN** "Cloud Migration" still lists it, and the entity is unchanged

### Requirement: Move a folder

A person with `viewpoint:manage` SHALL move a folder under another folder at a position; the folder SHALL keep its subfolders and entries. An unknown parent, the folder itself or a descendant as parent, and a name taken among the new siblings SHALL be rejected with 422 naming `parentId`; the root SHALL NOT move (422). Nothing SHALL change on a rejection.

Trace: US-096 AC1, REQ-096, tree-node BR-03

#### Scenario: Folder moved

- **WHEN** Kurt moves "Cloud Migration" under "Initiatives"
- **THEN** "Initiatives" holds "Cloud Migration" with its entries

#### Scenario: Under its own descendant

- **GIVEN** "Initiatives" holds "Cloud Migration"
- **WHEN** Kurt moves "Initiatives" under "Cloud Migration"
- **THEN** the response is 422 naming `parentId`, and the tree is unchanged

### Requirement: Move an entry

A person with `viewpoint:manage` SHALL move an entry within its folder or to another folder at a position. An unknown target folder, and a target folder that already holds the entry's target, SHALL be rejected with 422 naming `nodeId`. Positions SHALL be 0-based and count the entries the caller sees; a position past the end SHALL place it last, and the siblings SHALL keep a dense order.

Trace: US-096 AC2, AC3, REQ-096

#### Scenario: Reordered

- **GIVEN** "Finance" holds "SAP ERP", "Workday" and "Salesforce"
- **WHEN** Kurt moves "Salesforce" to position 0
- **THEN** "Finance" lists "Salesforce", "SAP ERP", "Workday", also after a reload

#### Scenario: To another folder

- **WHEN** Kurt moves "Workday" from "Finance" to "HR"
- **THEN** "HR" lists "Workday", and "Finance" no longer does

#### Scenario: Position past the end

- **WHEN** Kurt moves "SAP ERP" to position 99 in "Finance"
- **THEN** it is the last entry of "Finance"

#### Scenario: Entry the caller does not see

- **GIVEN** "Finance" holds "SAP ERP", Kurt's personal catalog "Kurt's Candidates" and "Workday" in this order
- **WHEN** Maria, who does not see Kurt's catalog, moves "Workday" to position 0
- **THEN** Maria's tree lists "Workday", "SAP ERP", and Kurt's tree lists "Workday", "SAP ERP", "Kurt's Candidates"

### Requirement: Audit of moves and placements

Moving a folder SHALL be audited as `tree-node.moved`, moving an entry as `tree-node.item-moved`, each with the old and new folder and the position; `tree-node.item-added` and `tree-node.item-removed` SHALL name the item type and the target ID (`entityId` for an entity). Details SHALL hold no display label and no entity name. A rejected change or a move that changes nothing SHALL NOT be audited.

Trace: UC-13, ADR-033, security review us-095-navigation-tree-items

#### Scenario: Move audited

- **WHEN** Kurt moves "Workday" from "Finance" to "HR"
- **THEN** `tree-node.item-moved` is audited with both folders

#### Scenario: Entity entry removed

- **GIVEN** "SAP ERP" is placed in "Finance"
- **WHEN** Kurt removes the entry
- **THEN** `tree-node.item-removed` is audited with `itemType` `entity` and the `entityId` of "SAP ERP", and without its name

#### Scenario: Move to the same place

- **WHEN** Kurt moves "SAP ERP" to the position it already has in "Finance"
- **THEN** the response is 200 with the tree unchanged, and no event is audited

### Requirement: Limits of placing and moving

A folder SHALL hold at most 200 entries of all item types; placing or moving an entry into a full folder SHALL be rejected with 422 naming `referenceId`, `entityId` or `nodeId`. A moved folder whose deepest subfolder would be more than 10 levels below the root SHALL be rejected with 422 naming `parentId`. A position SHALL be 0 to 10000, else 422 naming `position`.

Trace: REQ-094, REQ-096, tree-node BR-01, BR-06, security review us-095-navigation-tree-items

#### Scenario: Entries per folder

- **GIVEN** "Finance" holds 199 entries and "HR" holds 200 entries
- **WHEN** Kurt moves "Workday" from "Cloud Migration" into "Finance", then "Salesforce" into "HR"
- **THEN** the first move succeeds, and the second is rejected with 422 naming `nodeId`

#### Scenario: Reordering in a full folder

- **GIVEN** "HR" holds 200 entries
- **WHEN** Kurt moves one of them to position 0 in "HR"
- **THEN** the move succeeds

#### Scenario: Depth of a moved subtree

- **GIVEN** "Cloud Migration" has subfolders two levels deep
- **WHEN** Kurt moves it under a folder 7 levels below the root, then under a folder 8 levels below the root
- **THEN** the first move succeeds with the deepest folder 10 levels below the root, and the second is rejected with 422 naming `parentId`

#### Scenario: Position boundaries

- **WHEN** Kurt moves "SAP ERP" to the positions 0, 10000, -1 and 10001
- **THEN** 0 and 10000 are accepted, 10000 placing it last, and -1 and 10001 are rejected with 422 naming `position`

### Requirement: Consistent tree under concurrent changes

Changes of the tree SHALL be applied one after the other, each checked against the tree left by the one before, so that concurrent moves cannot form a cycle or exceed a limit. An entry SHALL be addressed by its own folder: an unknown folder or entry, an entry of another folder and an entry the caller cannot see SHALL answer 404.

Trace: tree-node BR-03, security review us-095-navigation-tree-items

#### Scenario: Two opposite moves at the same time

- **GIVEN** "Initiatives" and "Cloud Migration" are siblings
- **WHEN** Kurt moves "Initiatives" under "Cloud Migration" while Maria moves "Cloud Migration" under "Initiatives"
- **THEN** one move succeeds, the other is rejected with 422 naming `parentId`, and the tree has no cycle

#### Scenario: Entry of another folder

- **GIVEN** the entry "SAP ERP" is in "Finance"
- **WHEN** Kurt moves it with the path of the folder "HR"
- **THEN** the response is 404, and nothing changes

#### Scenario: Another person's personal catalog

- **GIVEN** Kurt placed his personal catalog "Kurt's Candidates" in "IT Landscape"
- **WHEN** Maria moves or removes that entry by its ID
- **THEN** the response is 404 like for an unknown entry, and the entry stays

### Requirement: Names in the sidebar shown as text

The sidebar SHALL show the names and display labels of folders and entries as text, never as markup; the link of an entry SHALL be built only from its item type and target ID. A drop SHALL only be accepted for a folder or entry dragged inside the tree.

Trace: US-095, security review us-095-navigation-tree-items

#### Scenario: Markup in an entity name

- **GIVEN** an entity named `<img src=x onerror=alert(1)>` is placed in "Finance"
- **WHEN** Franz opens the sidebar
- **THEN** the name is shown literally, and no script runs

### Requirement: Reorganize in the sidebar

Holders of `viewpoint:manage` SHALL move folders and entries in the sidebar by drag and drop and by "Move up", "Move down" and "Move to…"; a drop under the folder itself or a descendant SHALL be marked as invalid. Choosing an entity entry SHALL open its change history, a solution entry its page. The catalog browser and the solution page SHALL offer "Place in navigation".

Trace: US-095, US-096 AC1, UC-13, owner decision 2026-11-17

#### Scenario: Move with the keyboard

- **WHEN** Kurt chooses "Move up" on "Workday"
- **THEN** "Workday" comes before the entry above it

#### Scenario: Invalid drop target

- **WHEN** Kurt drags "Initiatives" onto its subfolder "Cloud Migration"
- **THEN** the drop target is marked as invalid, and nothing moves

#### Scenario: Place an entity from the catalog

- **WHEN** Kurt chooses "Place in navigation" on the row "SAP ERP" and picks "Finance"
- **THEN** the sidebar lists "SAP ERP" under "Finance"

#### Scenario: Reader

- **WHEN** Franz, holding only `viewer`, opens the sidebar
- **THEN** he can neither drag nor move entries
