# Spec: openspec diagram/diagrams (us-074-diagrams)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; UC-10; owner decisions 2026-11-19

@US-074 @UC-10 @diagram
Feature: Diagrams

  A diagram is an object with a name, a description and a viewpoint. Holders
  of entity:write create, rename and delete diagrams, holders of entity:restore
  restore deleted ones, and readers with entity:read list and read them.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the diagram viewpoint "Dg Process Model" with the notation "bpmn2" exists

  # Requirement: Create a diagram

  @AC1
  Scenario: Diagram created
    When the enterprise architect creates the diagram "Dg Order to Cash" with the viewpoint "Dg Process Model"
    Then the diagram has the version 1, the viewpoint "Dg Process Model" and the creator Anna
    And one "diagram.created" audit event names the diagram "Dg Order to Cash"

  Scenario: Without entity:write
    When the viewer creates the diagram "Dg Denied" with the viewpoint "Dg Process Model"
    Then the diagram answer is 403

  Scenario: Name taken
    Given the diagram "Dg Taken Name" exists in the viewpoint "Dg Process Model"
    When the enterprise architect creates the diagram " dg taken name " with the viewpoint "Dg Process Model"
    Then the diagram answer is 422 with the problem "diagram-name-taken"

  Scenario: Unknown viewpoint
    When the enterprise architect creates a diagram with an unknown viewpoint
    Then the diagram answer is 422 naming "viewpointId"

  Scenario Outline: Limit of the name
    When the enterprise architect creates a diagram with a name of <length> characters
    Then the diagram answer is <status>

    Examples:
      | length | status |
      | 1      | 201    |
      | 200    | 201    |
      | 201    | 422    |

  Scenario: Name of only blanks
    When the enterprise architect creates a diagram with only blanks as its name
    Then the diagram answer is 422 naming "name"

  Scenario Outline: Limit of the description
    When the enterprise architect creates a diagram with a description of <length> characters
    Then the diagram answer is <status>

    Examples:
      | length | status |
      | 2000   | 201    |
      | 2001   | 422    |

  Scenario: Viewpoint fixed
    Given the diagram viewpoint "Dg Other Model" with the notation "generic" exists
    And the diagram "Dg Fixed Viewpoint" exists in the viewpoint "Dg Process Model"
    When the enterprise architect changes "Dg Fixed Viewpoint" with another viewpoint
    Then the diagram "Dg Fixed Viewpoint" keeps its viewpoint "Dg Process Model"

  # Requirement: Read and list diagrams

  Scenario: List ordered by name ignoring case
    Given the diagram "Dg lst Order to Cash" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg lst hire to retire" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg lst Procure to Pay" exists in the viewpoint "Dg Process Model"
    When the viewer lists the diagrams with the filter "Dg lst"
    Then the diagrams listed are "Dg lst hire to retire", "Dg lst Order to Cash" and "Dg lst Procure to Pay"

  Scenario: Name filter
    Given the diagram "Dg flt Procure to Pay" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg flt Order to Cash" exists in the viewpoint "Dg Process Model"
    When the viewer lists the diagrams with the filter "flt procure to p"
    Then the diagrams listed are "Dg flt Procure to Pay"

  Scenario: Wildcards matched literally
    Given the diagram "Dg wild 100% Digital" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg wild 1000 Days" exists in the viewpoint "Dg Process Model"
    When the viewer lists the diagrams with the filter "wild 100%"
    Then the diagrams listed are "Dg wild 100% Digital"

  Scenario Outline: Page limits
    When the viewer lists the diagrams with the page size <size>
    Then the diagram answer is <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 0    | 400    |
      | 201  | 400    |

  Scenario Outline: Filter limit
    When the viewer lists the diagrams with a filter of <length> characters
    Then the diagram answer is <status>

    Examples:
      | length | status |
      | 200    | 200    |
      | 201    | 400    |

  Scenario: Unknown diagram
    When the viewer reads an unknown diagram
    Then the diagram answer is 404

  # Requirement: Change and delete a diagram

  Scenario: Renamed
    Given the diagram "Dg Rename Me" exists in the viewpoint "Dg Process Model"
    When the enterprise architect renames "Dg Rename Me" to "Dg Renamed (2026)" with its version
    Then the diagram has the version 2
    And one "diagram.updated" audit event names the diagram "Dg Renamed (2026)"

  Scenario: Stale version
    Given the diagram "Dg Stale" exists in the viewpoint "Dg Process Model"
    When the enterprise architect renames "Dg Stale" to "Dg Stale 2" with its version
    And the enterprise architect renames "Dg Stale 2" to "Dg Stale 3" with the version 1
    Then the diagram answer is 412
    When the enterprise architect renames "Dg Stale 2" to "Dg Stale 3" without a version
    Then the diagram answer is 428

  Scenario: Rename to a taken name
    Given the diagram "Dg Rename Source" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg Rename Target" exists in the viewpoint "Dg Process Model"
    When the enterprise architect renames "Dg Rename Source" to "DG RENAME TARGET" with its version
    Then the diagram answer is 422 with the problem "diagram-name-taken"

  Scenario: Without entity:write
    Given the diagram "Dg Read Only" exists in the viewpoint "Dg Process Model"
    When the viewer renames "Dg Read Only" to "Dg Read Only 2" with its version
    Then the diagram answer is 403
    When the viewer deletes the diagram "Dg Read Only" with its version
    Then the diagram answer is 403

  # Requirement: Restore a diagram

  Scenario: Deleted and restored
    Given the diagram "Dg Delete Restore" exists in the viewpoint "Dg Process Model"
    When the enterprise architect deletes the diagram "Dg Delete Restore" with its version
    Then the diagram answer is 204
    And "Dg Delete Restore" is no longer listed and answers 404
    And one "diagram.deleted" audit event names the diagram "Dg Delete Restore"
    And the deleted diagrams hold "Dg Delete Restore"
    When the enterprise architect restores the diagram "Dg Delete Restore" with its version
    Then the diagram has the version 3
    And "Dg Delete Restore" is listed among the diagrams
    And one "diagram.restored" audit event names the diagram "Dg Delete Restore"

  Scenario: Name taken in the meantime
    Given the diagram "Dg Reused Name" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg Reused Name" is deleted
    And the diagram "Dg Reused Name" exists in the viewpoint "Dg Process Model"
    When the enterprise architect restores the diagram "Dg Reused Name" with the version 2
    Then the diagram answer is 422 with the problem "diagram-name-taken"

  Scenario: Restore of a diagram that is not deleted
    Given the diagram "Dg Not Deleted" exists in the viewpoint "Dg Process Model"
    When the enterprise architect restores the diagram "Dg Not Deleted" with its version
    Then the diagram answer is 409 with the problem "diagram-not-deleted"

  Scenario: Restore with a stale or missing version
    Given the diagram "Dg Restore Stale" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg Restore Stale" is deleted
    When the enterprise architect restores the diagram "Dg Restore Stale" with the version 1
    Then the diagram answer is 412
    And no "diagram.restored" audit event names the diagram "Dg Restore Stale"

  Scenario: Without entity:restore
    Given the diagram "Dg Restore Denied" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg Restore Denied" is deleted
    When the viewer restores the diagram "Dg Restore Denied" with its version
    Then the diagram answer is 403
    When the viewer lists the deleted diagrams
    Then the diagram answer is 403

  # Requirement: Diagram count of a viewpoint (metamodel/viewpoints) and package re-import

  @UC-12
  Scenario: Viewpoint deleted with its diagrams
    Given the diagram viewpoint "Dg Doomed Model" with the notation "bpmn2" exists
    And the diagram "Dg Doomed 1" exists in the viewpoint "Dg Doomed Model"
    And the diagram "Dg Doomed 2" exists in the viewpoint "Dg Doomed Model"
    When the enterprise architect deletes the viewpoint "Dg Doomed Model" with its version
    Then the diagram answer is 204
    And the diagram "Dg Doomed 1" was removed with its viewpoint, audited with the cause "viewpoint-deleted"
    And the diagram "Dg Doomed 2" was removed with its viewpoint, audited with the cause "viewpoint-deleted"

  @UC-12
  Scenario: Stale version keeps the diagrams
    Given the diagram viewpoint "Dg Kept Model" with the notation "bpmn2" exists
    And the diagram "Dg Kept 1" exists in the viewpoint "Dg Kept Model"
    When the enterprise architect deletes the viewpoint "Dg Kept Model" with the version 7
    Then the diagram answer is 412
    And the viewpoint "Dg Kept Model" and its diagram "Dg Kept 1" still exist

  # Requirement: Diagrams in the browser (navigation/solution-browser)

  @UC-13
  Scenario: Category Diagrams
    Given the diagram viewpoint "Dg Landscape" with the notation "archimate3" exists
    And the diagram "Dg brw Procure to Pay" exists in the viewpoint "Dg Process Model"
    And the diagram "Dg brw order to Cash" exists in the viewpoint "Dg Landscape"
    When the viewer reads the category diagrams of the browser
    Then the browser lists "Dg brw order to Cash" marked "archimate3" before "Dg brw Procure to Pay" marked "bpmn2"
    And the browser counts the diagrams that are not deleted

  @UC-13
  Scenario: Second page
    Given 51 diagrams named "Dg page" followed by a number exist in the viewpoint "Dg Process Model"
    When the viewer reads page 1 of the category diagrams of the browser
    Then the browser page holds the diagrams beyond the first 50

  @UC-13
  Scenario: Category of a solution
    Given the diagram solution "Dg CRM Migration" exists
    When the viewer reads the category diagrams of the solution "Dg CRM Migration"
    Then the diagram answer is 422 naming "category"

  # Requirement: Place a diagram (navigation/navigation-tree)

  @UC-13
  Scenario: Diagram placed, hidden while deleted and shown again after the restore
    Given the diagram folder "Dg Processes" exists
    And the diagram "Dg Placed" exists in the viewpoint "Dg Process Model"
    When the enterprise architect places the diagram "Dg Placed" in "Dg Processes"
    Then the diagram answer is 201
    And the tree of the viewer lists "Dg Placed" under "Dg Processes"
    And the placement is audited with the item type "diagram" and the reference of "Dg Placed"
    When the enterprise architect places the diagram "Dg Placed" in "Dg Processes"
    Then the diagram answer is 422 naming "referenceId"
    When the enterprise architect deletes the diagram "Dg Placed" with its version
    Then the tree of the viewer does not list "Dg Placed" under "Dg Processes"
    When the enterprise architect restores the diagram "Dg Placed" with its version
    Then the tree of the viewer lists "Dg Placed" under "Dg Processes"

  @UC-13
  Scenario: Unknown diagram
    Given the diagram folder "Dg Unknown Place" exists
    When the enterprise architect places an unknown diagram in "Dg Unknown Place"
    Then the diagram answer is 422 naming "referenceId"
