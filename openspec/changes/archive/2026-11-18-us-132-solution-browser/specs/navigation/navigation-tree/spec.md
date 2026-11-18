## MODIFIED Requirements

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
