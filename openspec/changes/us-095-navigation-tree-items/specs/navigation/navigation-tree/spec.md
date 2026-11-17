## MODIFIED Requirements

### Requirement: Read the tree

Every reader with `entity:read` SHALL read the tree from the root "Repository", with its folders and entries in their manual order (`sortOrder`). An entry SHALL show its display label, else the current name of its target. A catalog entry SHALL be left out when the catalog is another person's personal catalog or no longer exists; an entity entry SHALL stay and be marked as deleted when its entity is deleted.

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

## ADDED Requirements

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

A person with `viewpoint:manage` SHALL move a folder under another folder at a position; the folder SHALL keep its subfolders and entries. Moving it under itself or a descendant SHALL be rejected with 422 naming `parentId`; the root SHALL NOT move (422); a name taken among the new siblings SHALL be rejected with 422 naming `name`. Nothing SHALL change on a rejection.

Trace: US-096 AC1, REQ-096, tree-node BR-03

#### Scenario: Folder moved

- **WHEN** Kurt moves "Cloud Migration" under "Initiatives"
- **THEN** "Initiatives" holds "Cloud Migration" with its entries

#### Scenario: Under its own descendant

- **GIVEN** "Initiatives" holds "Cloud Migration"
- **WHEN** Kurt moves "Initiatives" under "Cloud Migration"
- **THEN** the response is 422 naming `parentId`, and the tree is unchanged

### Requirement: Move an entry

A person with `viewpoint:manage` SHALL move an entry within its folder or to another folder at a position. An entry whose target is already in the target folder SHALL be rejected with 422 naming `referenceId`. Positions SHALL be 0-based; a position past the end SHALL place it last, and the siblings SHALL keep a dense order.

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

### Requirement: Audit of moves and placements

Moving a folder SHALL be audited as `tree-node.moved`, moving an entry as `tree-node.item-moved`, each with the old and new folder and the position; `tree-node.item-added` SHALL name the item type. A rejected change SHALL NOT be audited.

Trace: UC-13, ADR-033

#### Scenario: Move audited

- **WHEN** Kurt moves "Workday" from "Finance" to "HR"
- **THEN** `tree-node.item-moved` is audited with both folders

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
