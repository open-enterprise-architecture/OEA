# Spec: openspec diagram/diagram-viewpoint-change, diagram/diagrams and metamodel/viewpoints (us-074-diagram-viewpoint-change)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; UC-10; UC-12; owner decisions 2026-11-19 and 2026-11-26
# The editor scenarios (the dialog "Change viewpoint" with its preview, the unsaved changes, the marks and the list of
# conflicts, "Remove from diagram", the read-only list, the warning of SCR-031) are covered by the frontend specs; this
# feature covers the API. The viewpoints, entities and diagrams carry a token of the scenario.

@US-074 @UC-10 @UC-12 @diagram
Feature: Diagram viewpoint change

  The viewpoint of a diagram can be changed and is saved at once. Shapes and
  edges whose type the viewpoint does not allow are conflicts, however they
  arose; a save is accepted only when none is left. Editing a viewpoint can
  count the diagrams it affects.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the viewpoint-change viewpoint "Application Landscape" allows "Application Component, VPC SaaS Application, Data Object" and the connections "VPC Flow, VPC Data Flow"
    And the viewpoint-change viewpoint "Integration View" allows "Application Component" and the connections "VPC Flow"
    And the viewpoint-change viewpoint "Flow Free View" allows "Application Component, VPC SaaS Application, Data Object" and the connections "VPC Flow"
    And the viewpoint-change diagram "Order to Cash" in the viewpoint "Application Landscape" shows "SAP ERP" and "CRM" of "Application Component", "Customer DB" of "Data Object" and "Salesforce" of "VPC SaaS Application" with a "VPC Flow" from "SAP ERP" to "CRM" and a "VPC Data Flow" from "SAP ERP" to "Customer DB"

  # Requirement: Change the viewpoint of a diagram

  Scenario: Viewpoint changed
    When the enterprise architect changes "Order to Cash" to "Integration View" with its current version
    Then the viewpoint-change answer is 200
    And "Order to Cash" has the viewpoint "Integration View" at the next version
    And the viewpoint-change answer reports 3 conflicts
    And "Order to Cash" still shows 4 shapes and 2 edges
    And the audit trail has one event "diagram.viewpoint-changed" for "Order to Cash" with the conflicts 3

  Scenario: Same viewpoint
    When the enterprise architect changes "Order to Cash" to "Application Landscape" with its current version
    Then the viewpoint-change answer is 200
    And "Order to Cash" is unchanged

  Scenario Outline: Viewpoint change rejected
    Given the viewpoint-change diagram "Order to Cash" is <state>
    When the <actor> changes "Order to Cash" to "<target>" <version>
    Then the viewpoint-change answer is <status>
    And "Order to Cash" is unchanged

    Examples:
      | state   | actor                | target           | version                  | status |
      | current | enterprise architect | unknown          | with its current version | 422    |
      | current | enterprise architect | Integration View | with a stale version     | 412    |
      | current | enterprise architect | Integration View | without a version        | 428    |
      | current | viewer               | Integration View | with its current version | 403    |
      | deleted | enterprise architect | Integration View | with its current version | 404    |

  Scenario: Viewpoint fixed in the update
    When the enterprise architect renames "Order to Cash" with the viewpoint "Integration View" in the request
    Then the viewpoint-change answer is 200
    And "Order to Cash" has the viewpoint "Application Landscape" at the next version

  # Requirement: Conflicts of a diagram

  Scenario: Conflict after a viewpoint change
    When the enterprise architect changes "Order to Cash" to "Integration View" with its current version
    Then the view of "Order to Cash" marks the shapes "Customer DB, Salesforce" as conflicts
    And the view of "Order to Cash" marks 1 edge as a conflict
    And the view of "Order to Cash" has the conflict count 3

  Scenario: Conflict after a viewpoint edit
    When the enterprise architect removes "Data Object" from the viewpoint "Application Landscape"
    Then the view of "Order to Cash" marks the shapes "Customer DB" as conflicts
    And the view of "Order to Cash" marks 0 edges as a conflict

  Scenario: Conflict resolved by the viewpoint
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect allows "Data Object, VPC SaaS Application" in the viewpoint "Integration View"
    Then the view of "Order to Cash" marks the shapes "" as conflicts
    And the view of "Order to Cash" marks 1 edge as a conflict

  Scenario: Edge conflict
    When the enterprise architect changes "Order to Cash" to "Flow Free View" with its current version
    Then the view of "Order to Cash" marks the shapes "" as conflicts
    And the view of "Order to Cash" marks 1 edge as a conflict
    And the view of "Order to Cash" has the conflict count 1

  Scenario: Subtype of an allowed type
    When the enterprise architect changes "Order to Cash" to "Integration View" with its current version
    Then the view of "Order to Cash" marks the shapes "Customer DB, Salesforce" as conflicts
    And the shape of "SAP ERP" in "Order to Cash" is no conflict

  Scenario: Hidden shapes are no conflicts
    Given "Order to Cash" has the viewpoint "Integration View"
    And the viewpoint-change entities "Customer DB" and "Salesforce" are deleted
    Then the view of "Order to Cash" has the conflict count 0
    When the viewpoint-change entities "Customer DB" and "Salesforce" are restored
    Then the view of "Order to Cash" has the conflict count 3

  Scenario: Conflicts readable by a reader
    Given "Order to Cash" has the viewpoint "Integration View"
    Then the viewer sees the view of "Order to Cash" with the conflict count 3

  # Requirement: Saving only without conflicts

  Scenario: Save blocked
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" moving "CRM" by 10 pt
    Then the viewpoint-change answer is 409 diagram-conflicts
    And the conflicts name the shapes "Customer DB, Salesforce" and 1 edge
    And "Order to Cash" is unchanged
    When the enterprise architect saves "Order to Cash" without any change
    Then the viewpoint-change answer is 409 diagram-conflicts
    And the conflicts name the shapes "Customer DB, Salesforce" and 1 edge
    And "Order to Cash" is unchanged

  Scenario: Layout error first
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" with a new shape of an unknown entity
    Then the viewpoint-change answer is 422

  Scenario: Conflicts removed
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" without "Customer DB, Salesforce" moving "CRM" by 10 pt
    Then the viewpoint-change answer is 200
    And the view of "Order to Cash" has the conflict count 0
    And "Order to Cash" still shows 2 shapes and 1 edges

  Scenario: One conflict left
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" without "Customer DB"
    Then the viewpoint-change answer is 409 diagram-conflicts
    And the conflicts name the shapes "Salesforce" and 0 edge
    And "Order to Cash" is unchanged

  Scenario: Conflict of an entity update
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" changing the description of "SAP ERP"
    Then the viewpoint-change answer is 409 diagram-conflicts
    And "Order to Cash" is unchanged
    And the entity "SAP ERP" has no description

  Scenario: New shape of a type not allowed
    Given "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" without "Customer DB, Salesforce" adding a shape of the new entity "Billing DB" of "Data Object"
    Then the viewpoint-change answer is 422

  Scenario: Container with a conflict
    Given the shapes "Billing" and "Reporting" of "Application Component" are nested in the shape of "Customer DB" of "Order to Cash"
    And "Order to Cash" has the viewpoint "Integration View"
    When the enterprise architect saves "Order to Cash" without "Customer DB, Salesforce" and the shapes nested in them
    Then the viewpoint-change answer is 200
    And "Order to Cash" still shows 2 shapes and 1 edges

  Scenario: Read-only reader
    Given "Order to Cash" has the viewpoint "Integration View"
    When the viewer saves "Order to Cash" without any change
    Then the viewpoint-change answer is 403

  # Requirement: Diagrams affected by a viewpoint edit

  Scenario: Count of affected diagrams
    Given the viewpoint-change viewpoint "Process Model" is used by 5 diagrams, 2 of which show "Data Object" and 1 an edge of "VPC Flow"
    When the enterprise architect asks how many diagrams of "Process Model" are affected by removing "Data Object, VPC Flow"
    Then the viewpoint-change answer is 200
    And the affected count is 3

  Scenario: Only hidden shapes
    Given the viewpoint-change viewpoint "Process Model" is used by 5 diagrams, 2 of which show "Data Object" and 1 an edge of "VPC Flow"
    And the entities of the data objects of "Process Model" are deleted
    When the enterprise architect asks how many diagrams of "Process Model" are affected by removing "Data Object"
    Then the affected count is 0

  Scenario: Nothing removed
    Given the viewpoint-change viewpoint "Process Model" is used by 5 diagrams, 2 of which show "Data Object" and 1 an edge of "VPC Flow"
    When the enterprise architect asks how many diagrams of "Process Model" are affected by removing ""
    Then the affected count is 0

  Scenario: Without viewpoint:manage
    When the viewer asks how many diagrams of "Application Landscape" are affected by removing "Data Object"
    Then the viewpoint-change answer is 403

  Scenario Outline: Limits of the proposed types
    When the enterprise architect asks how many diagrams of "Application Landscape" are affected with <types> metatype IDs and <connections> connection type IDs
    Then the viewpoint-change answer is <status>

    Examples:
      | types | connections | status |
      | 0     | 0           | 200    |
      | 500   | 500         | 200    |
      | 501   | 0           | 400    |
      | 0     | 501         | 400    |
