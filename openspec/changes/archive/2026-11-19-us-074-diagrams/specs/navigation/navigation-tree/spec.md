## MODIFIED Requirements

### Requirement: Read the tree

A reader with `entity:read` SHALL read the tree from the root "Repository" in its manual order (`sortOrder`). An entry SHALL show its display label, else its target's current name. Another person's personal catalog, a missing catalog or a missing or deleted diagram SHALL be left out; a deleted entity's entry SHALL stay, marked as deleted.

Trace: US-054, US-095, US-096 AC2, REQ-050, REQ-095, REQ-096, UC-13 step 2, owner decisions 2026-11-06, 2026-11-17 and 2026-11-19

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

#### Scenario: Deleted diagram

- **GIVEN** the diagram "Order to Cash" is placed in "Processes"
- **WHEN** the diagram is deleted and Franz reads the tree
- **THEN** "Processes" no longer lists the entry

## ADDED Requirements

### Requirement: Place a diagram

A person with `viewpoint:manage` and `entity:read` SHALL place a diagram in a folder as an entry with an optional display label, at the end of the folder, once per folder; an unknown diagram or a second entry in the folder SHALL be rejected with 422 naming `referenceId`. The diagram SHALL NOT change. The diagram page SHALL offer "Place in navigation" to these persons, and choosing the entry SHALL open the diagram.

Trace: US-095, US-097, UC-13, owner decision 2026-11-19

#### Scenario: Diagram placed

- **WHEN** Kurt places the diagram "Order to Cash" in "Processes"
- **THEN** "Processes" lists "Order to Cash" as its last entry, `tree-node.item-added` is audited with `itemType` `diagram` and its `referenceId`, and choosing the entry opens the diagram

#### Scenario: Second entry

- **WHEN** Kurt places "Order to Cash" in "Processes" again
- **THEN** the response is 422 naming `referenceId`

#### Scenario: Unknown diagram

- **WHEN** Kurt places a diagram ID that does not exist
- **THEN** the response is 422 naming `referenceId`, and the tree is unchanged

#### Scenario: Writer without viewpoint:manage

- **WHEN** Anna, holding `entity:write` but not `viewpoint:manage`, places "Order to Cash" in "Processes"
- **THEN** the response is 403, and the diagram page offers her no "Place in navigation"
