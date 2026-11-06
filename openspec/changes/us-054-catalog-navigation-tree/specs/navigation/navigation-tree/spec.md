# Delta for navigation/navigation-tree

## Purpose

Defines the navigation tree of the repository: its folders with the root, the catalog entries placed in them, how readers see the tree, who may change it, and how the web portal shows it as a sidebar.

## ADDED Requirements

### Requirement: Read the tree

Every reader with `entity:read` SHALL read the tree from the root folder "Repository", with its folders and catalog entries sorted by name ignoring case. An entry SHALL show its display label, else the current name of the catalog, and SHALL be left out when the catalog is another person's personal catalog or no longer exists.

Trace: US-054, REQ-050, REQ-095, UC-13 step 2, owner decision 2026-11-06

#### Scenario: Catalog under its folder

- **GIVEN** the catalog "Application Inventory" is placed in the folder "IT Landscape"
- **WHEN** Franz reads the tree
- **THEN** "IT Landscape" lists "Application Inventory"

#### Scenario: Another person's personal catalog

- **GIVEN** Kurt placed his personal catalog "Kurt's Candidates" in "IT Landscape"
- **WHEN** Franz reads the tree
- **THEN** "IT Landscape" does not list "Kurt's Candidates", and Kurt's tree does

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

Folders and entries SHALL be changed only with `viewpoint:manage`; any other request SHALL be rejected with 403. Every change SHALL be audited with the actor.

Trace: UC-13, ADR-032 §8, ADR-033, owner decision 2026-11-06

#### Scenario: Reader without the right

- **WHEN** Franz, holding only `viewer`, creates a folder
- **THEN** the request is rejected with 403

### Requirement: Navigation sidebar

The web portal SHALL show the tree as a sidebar on every page after login, with expandable folders; on narrow screens the sidebar SHALL open from a menu button. Choosing a catalog entry SHALL open the catalog. Readers with `viewpoint:manage` SHALL be offered "New folder", "Rename", "Delete" and "Remove from tree", and on the catalog page "Place in navigation".

Trace: US-054 AC1, AC2, AC4, UC-13 step 1–2, owner decision 2026-11-06

#### Scenario: Open a catalog from the tree

- **GIVEN** "Application Inventory" is placed in "IT Landscape" and has the default view "Compact view"
- **WHEN** Franz chooses it in the sidebar
- **THEN** the catalog opens with "Compact view"

#### Scenario: Reader without the right sees no actions

- **WHEN** Franz, holding only `viewer`, opens the sidebar
- **THEN** it offers no "New folder", "Rename", "Delete" or "Remove from tree"
