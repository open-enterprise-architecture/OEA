# Spec: openspec navigation/navigation-tree
# Source material: requirements/user-stories/US-054-catalog-navigation-tree.md; REQ-050, REQ-094, REQ-095, REQ-097; UC-13; ADR-032

@US-054 @UC-13 @navigation
Feature: Navigation tree

  The navigation tree arranges catalogs in folders under the root
  "Repository". Readers see the folders and the catalogs they can open;
  the enterprise architect manages folders and places catalogs.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP

  @REQ-094 @AC1
  Scenario: New folder
    When the enterprise architect creates the folder "Tree IT Landscape" under the root
    Then the tree of the viewer shows "Tree IT Landscape" under "Repository"
    And one "tree-node.created" audit event names "Tree IT Landscape"

  @REQ-094 @AC2
  Scenario: Duplicate name
    Given the enterprise architect has created the folder "Tree Duplicates" under the root
    When the enterprise architect creates the folder "tree duplicates" under the root
    Then the tree change is rejected with 422 naming "name"

  @AC1 @REQ-050 @AC1
  Scenario: Place under a folder
    Given the enterprise architect has created the folder "Tree Placement" under the root
    And the enterprise architect has created the catalog "Tree Inventory" with scope "instance"
    When the enterprise architect places "Tree Inventory" in "Tree Placement"
    Then the tree of the viewer lists "Tree Inventory" under "Tree Placement"

  @AC3 @REQ-050 @AC2
  Scenario: Two folders, one catalog
    Given the enterprise architect has created the folders "Tree Landscape" and "Tree Compliance" under the root
    And the enterprise architect has created the catalog "Tree Shared Inventory" with scope "instance"
    And the enterprise architect places "Tree Shared Inventory" in "Tree Landscape"
    When the enterprise architect places "Tree Shared Inventory" in "Tree Compliance"
    Then the tree of the viewer lists "Tree Shared Inventory" under "Tree Landscape" and under "Tree Compliance"
    And exactly one catalog is named "Tree Shared Inventory" ignoring case

  @AC5 @REQ-050 @AC5
  Scenario: Display label
    Given the enterprise architect has created the folder "Tree Labels" under the root
    And the enterprise architect has created the catalog "Tree Labelled Inventory" with scope "instance"
    When the enterprise architect places "Tree Labelled Inventory" in "Tree Labels" with the display label "System Inventory"
    Then the tree of the viewer lists "System Inventory" under "Tree Labels"
    And the catalog overview of the viewer lists "Tree Labelled Inventory"

  @AC4 @REQ-050 @AC3
  Scenario: Remove one placement
    Given the enterprise architect has created the folders "Tree Removal" and "Tree Kept" under the root
    And the enterprise architect has created the catalog "Tree Removed Inventory" with scope "instance"
    And the enterprise architect places "Tree Removed Inventory" in "Tree Removal"
    And the enterprise architect places "Tree Removed Inventory" in "Tree Kept"
    When the enterprise architect removes "Tree Removed Inventory" from "Tree Removal"
    Then the tree of the viewer lists "Tree Removed Inventory" under "Tree Kept" only
    And the viewer receives 200 for the catalog "Tree Removed Inventory"

  @REQ-094 @AC3
  Scenario: Delete with subfolders
    Given the enterprise architect has created the folder "Tree Deleted" under the root
    And the enterprise architect has created the folder "Tree Deleted Child" under "Tree Deleted"
    And the enterprise architect has created the catalog "Tree Deleted Inventory" with scope "instance"
    And the enterprise architect places "Tree Deleted Inventory" in "Tree Deleted Child"
    When the enterprise architect deletes the folder "Tree Deleted"
    Then the tree has no folder "Tree Deleted"
    And the tree has no folder "Tree Deleted Child"
    And the viewer receives 200 for the catalog "Tree Deleted Inventory"

  @REQ-094 @AC4
  Scenario: Root protected
    When the enterprise architect deletes the root folder
    Then the tree change is rejected with 422

  Scenario: Another person's personal catalog
    Given the enterprise architect has created the folder "Tree Personal" under the root
    And the enterprise architect has created the catalog "Tree Candidates" with scope "personal"
    When the enterprise architect places "Tree Candidates" in "Tree Personal"
    Then the tree of the viewer does not list "Tree Candidates" under "Tree Personal"
    And the tree of the enterprise architect lists "Tree Candidates" under "Tree Personal"

  Scenario: Reader without the right
    When the viewer creates the folder "Tree Viewer Folder" under the root
    Then the tree change is rejected with 403
    And the tree has no folder "Tree Viewer Folder"

  # us-095-navigation-tree-items

  @REQ-096 @US-096
  Scenario: New folders and entries go to the end
    Given the enterprise architect has created the folder "Tree Appended" under the root
    And the enterprise architect has created the folder "Tree Appended Zulu" under "Tree Appended"
    And the enterprise architect has created the folder "Tree Appended Alpha" under "Tree Appended"
    And the entity "Tree Appended SAP" exists
    And the enterprise architect has placed the entity "Tree Appended SAP" in "Tree Appended"
    And the solution "Tree Appended CRM" exists
    When the enterprise architect places the solution "Tree Appended CRM" in "Tree Appended"
    Then the folders under "Tree Appended" in the tree of the viewer are "Tree Appended Zulu, Tree Appended Alpha"
    And the entries of "Tree Appended" in the tree of the viewer are "Tree Appended SAP, Tree Appended CRM"

  @REQ-095 @REQ-097 @US-095
  Scenario: Entity placed
    Given the enterprise architect has created the folder "Tree Finance" under the root
    And the entity "Tree SAP ERP" exists
    When the enterprise architect places the entity "Tree SAP ERP" in "Tree Finance"
    Then the tree of the viewer lists "Tree SAP ERP" under "Tree Finance"
    And the entity "Tree SAP ERP" is still at version 1

  @REQ-097 @US-097
  Scenario: Same entity in two folders
    Given the enterprise architect has created the folders "Tree Two A" and "Tree Two B" under the root
    And the entity "Tree Twice ERP" exists
    And the enterprise architect has placed the entity "Tree Twice ERP" in "Tree Two A"
    When the enterprise architect places the entity "Tree Twice ERP" in "Tree Two B"
    Then the tree of the viewer lists "Tree Twice ERP" under "Tree Two A" and under "Tree Two B"
    When the enterprise architect places the entity "Tree Twice ERP" in "Tree Two A"
    Then the tree change is rejected with 422 naming "entityId"

  @REQ-095 @US-095
  Scenario: Connection
    Given the enterprise architect has created the folder "Tree Connections" under the root
    And the connection "Tree Orders" exists
    When the enterprise architect places the entity "Tree Orders" in "Tree Connections"
    Then the tree change is rejected with 422 naming "entityId"

  @REQ-095 @US-095
  Scenario: Solution placed
    Given the enterprise architect has created the folder "Tree Initiatives" under the root
    And the solution "Tree CRM Replacement" exists
    When the enterprise architect places the solution "Tree CRM Replacement" in "Tree Initiatives"
    Then the tree of the viewer lists "Tree CRM Replacement" under "Tree Initiatives"
    When the enterprise architect places the solution "Tree CRM Replacement" in "Tree Initiatives"
    Then the tree change is rejected with 422 naming "referenceId"

  @REQ-095 @US-095 @AC1
  Scenario: Two marked entries
    Given the enterprise architect has created the folders "Tree Marked A" and "Tree Marked B" under the root
    And the entity "Tree Marked ERP" exists
    And the enterprise architect has placed the entity "Tree Marked ERP" in "Tree Marked A"
    And the enterprise architect has placed the entity "Tree Marked ERP" in "Tree Marked B"
    When the entity "Tree Marked ERP" is deleted
    Then the entry "Tree Marked ERP" under "Tree Marked A" is marked as deleted in the tree of the viewer
    And the entry "Tree Marked ERP" under "Tree Marked B" is marked as deleted in the tree of the viewer
    When the entity "Tree Marked ERP" is restored
    Then the entry "Tree Marked ERP" under "Tree Marked A" is not marked as deleted in the tree of the viewer

  @REQ-095 @US-097 @AC2
  Scenario: Remove one entity entry
    Given the enterprise architect has created the folders "Tree Remove A" and "Tree Remove B" under the root
    And the entity "Tree Removed ERP" exists
    And the enterprise architect has placed the entity "Tree Removed ERP" in "Tree Remove A"
    And the enterprise architect has placed the entity "Tree Removed ERP" in "Tree Remove B"
    When the enterprise architect removes the entity "Tree Removed ERP" from "Tree Remove A"
    Then the tree of the viewer lists "Tree Removed ERP" under "Tree Remove B" only
    And the entity "Tree Removed ERP" is still at version 1
    And one "tree-node.item-removed" audit event names "Tree Removed ERP" as its entity and no name

  @REQ-096 @US-096 @AC1
  Scenario: Folder moved
    Given the enterprise architect has created the folders "Tree Move Initiatives" and "Tree Move Cloud" under the root
    And the enterprise architect has created the folder "Tree Move Child" under "Tree Move Cloud"
    When the enterprise architect moves the folder "Tree Move Cloud" under "Tree Move Initiatives" at position 0
    Then the folders under "Tree Move Initiatives" in the tree of the viewer are "Tree Move Cloud"
    And the folders under "Tree Move Cloud" in the tree of the viewer are "Tree Move Child"

  @REQ-096 @US-096 @AC1
  Scenario: Under its own descendant
    Given the enterprise architect has created the folder "Tree Cycle Top" under the root
    And the enterprise architect has created the folder "Tree Cycle Sub" under "Tree Cycle Top"
    When the enterprise architect moves the folder "Tree Cycle Top" under "Tree Cycle Sub" at position 0
    Then the tree change is rejected with 422 naming "parentId"
    And the folders under "Tree Cycle Top" in the tree of the viewer are "Tree Cycle Sub"

  @REQ-096 @US-096 @AC2
  Scenario: Reordered
    Given the enterprise architect has created the folder "Tree Reorder" under the root
    And the entities "Tree R SAP", "Tree R Workday" and "Tree R Salesforce" are placed in "Tree Reorder"
    When the enterprise architect moves the entry "Tree R Salesforce" of "Tree Reorder" to "Tree Reorder" at position 0
    Then the entries of "Tree Reorder" in the tree of the viewer are "Tree R Salesforce, Tree R SAP, Tree R Workday"
    When the enterprise architect moves the entry "Tree R Salesforce" of "Tree Reorder" to "Tree Reorder" at position 99
    Then the entries of "Tree Reorder" in the tree of the viewer are "Tree R SAP, Tree R Workday, Tree R Salesforce"

  @REQ-096 @US-096 @AC3
  Scenario: To another folder
    Given the enterprise architect has created the folders "Tree Source" and "Tree Target" under the root
    And the entities "Tree T SAP" and "Tree T Workday" are placed in "Tree Source"
    When the enterprise architect moves the entry "Tree T Workday" of "Tree Source" to "Tree Target" at position 0
    Then the entries of "Tree Target" in the tree of the viewer are "Tree T Workday"
    And the entries of "Tree Source" in the tree of the viewer are "Tree T SAP"
    And one "tree-node.item-moved" audit event moves from "Tree Source" to "Tree Target"

  @REQ-096 @US-096
  Scenario: Move to the same place
    Given the enterprise architect has created the folder "Tree Same Place" under the root
    And the entities "Tree S SAP" and "Tree S Workday" are placed in "Tree Same Place"
    When the enterprise architect moves the entry "Tree S SAP" of "Tree Same Place" to "Tree Same Place" at position 0
    Then the tree change is accepted
    And no "tree-node.item-moved" audit event exists for "Tree Same Place"

  @REQ-096 @US-096
  Scenario Outline: Position boundaries
    Given the enterprise architect has created the folders "Tree Bounds <name>" and "Tree Bounds Target <name>" under the root
    And the entities "Tree Bounds SAP <name>" are placed in "Tree Bounds <name>"
    When the enterprise architect moves the entry "Tree Bounds SAP <name>" of "Tree Bounds <name>" to "Tree Bounds Target <name>" at position <position>
    Then the tree change is <outcome>

    Examples:
      | name  | position | outcome                              |
      | low   | 0        | accepted                             |
      | high  | 10000    | accepted                             |
      | under | -1       | rejected with 422 naming "position"  |
      | over  | 10001    | rejected with 422 naming "position"  |

  @REQ-096 @US-096
  Scenario: Reader cannot move
    Given the enterprise architect has created the folders "Tree Reader A" and "Tree Reader B" under the root
    When the viewer moves the folder "Tree Reader B" under "Tree Reader A" at position 0
    Then the tree change is rejected with 403
