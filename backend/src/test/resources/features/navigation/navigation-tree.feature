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
