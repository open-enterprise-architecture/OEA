# Spec: openspec diagram/bpmn-assignments, metamodel/organization-model (us-075-lane-assignments)
# Source material: requirements/user-stories/US-075-assign-lane-orgunit-and-role.md, US-076-assign-task-to-person.md; REQ-077 AC1-AC8; UC-10
# The scenarios share one instance of their own (the imports change its metamodel) and run in file order.
# The editor scenarios (context menu, search, badges, chips, avatar, fields, role lanes on the canvas) are covered by
# the frontend specs and stories; this feature covers the API.

@US-075 @US-076 @REQ-077 @UC-10 @diagram @bpmn
Feature: Assignments of lanes, pools and tasks

  A lane or a pool belongs to an organizational unit and performs roles, a task needs
  a role and a user task has a responsible person. Each assignment is a connection
  without an edge, saved with the layout of the diagram.

  Background:
    Given an instance of its own with the BPMN and the organization package

  @AC1 @REQ-077 @AC1
  Scenario: Assign an organizational unit to a lane
    Given a process diagram "Order handling" with the lane "Purchasing"
    And the organizational unit "Purchasing Department"
    When the enterprise architect assigns the organizational unit "Purchasing Department" to "Purchasing" and saves
    Then the save is accepted
    And "Purchasing" belongs to "Purchasing Department" and the creation is audited
    And the view shows no edge for it

  @AC2 @REQ-077 @AC2
  Scenario: Two roles on a lane
    Given a process diagram "Order handling" with the lane "Purchasing"
    And the roles "Approver" and "Reviewer"
    When the enterprise architect assigns the role "Approver" to "Purchasing" and saves
    And the enterprise architect assigns the role "Reviewer" to "Purchasing" and saves
    Then the lane "Purchasing" shows the roles "Approver" and "Reviewer"

  @AC1 @REQ-077 @AC1
  Scenario: Participant of a pool
    Given a process diagram "Order handling" with the pool "Customer"
    And the organizational unit "Sales Division"
    When the enterprise architect assigns the organizational unit "Sales Division" to "Customer" and saves
    Then the pool "Customer" shows the organizational unit "Sales Division"

  @AC3 @REQ-077 @AC3
  Scenario: Responsible person of a user task
    Given a process diagram "Order handling" with the user task "Approve order"
    And the person "Maria Müller"
    When the enterprise architect assigns the person "Maria Müller" to "Approve order" and saves
    Then the task "Approve order" shows the person "Maria Müller"

  @AC4 @REQ-077 @AC4
  Scenario: Required role of a plain task
    Given a process diagram "Order handling" with the task "Check order"
    And the roles "Approver" and "Reviewer"
    When the enterprise architect assigns the role "Approver" to "Check order" and saves
    Then the task "Check order" shows the role "Approver"

  @AC5 @REQ-077 @AC5
  Scenario: Remove the organizational unit of a lane
    Given a process diagram "Order handling" with the lane "Purchasing"
    And the organizational unit "Purchasing Department"
    And the enterprise architect assigned "Purchasing Department" to "Purchasing"
    When the enterprise architect removes the assignment of "Purchasing" and saves
    Then the save is accepted
    And the lane "Purchasing" shows no organizational unit
    And the removed connection is restorable and audited as deleted

  @AC8 @REQ-077 @AC8
  Scenario: Create the organizational unit in the same save
    Given a process diagram "Order handling" with the lane "Purchasing"
    When the enterprise architect assigns the new organizational unit "Logistics" to "Purchasing" and saves
    Then the organizational unit "Logistics" exists and the lane "Purchasing" belongs to it
    And the diagram has no shape for "Logistics"

  @AC6 @AC7 @REQ-077 @AC6 @AC7
  Scenario: Catalog of user tasks with the responsible person
    Given a process diagram "Invoicing" with the user tasks "Approve invoice" and "Ship invoice"
    And the person "Maria Müller"
    And the enterprise architect assigned "Maria Müller" to "Approve invoice"
    And a catalog "Tasks and people" of user tasks with the join "Responsible" to the persons
    Then "Approve invoice" shows "Maria Müller" in the join column
    And "Ship invoice" shows an empty cell in the join column

  @AC6 @REQ-077 @AC6
  Scenario: Catalog of lanes filtered by the organizational unit
    Given a process diagram "Procurement" with the lanes "Buying" and "Selling"
    And the organizational units "Buying Department" and "Selling Division"
    And the enterprise architect assigned "Buying Department" to "Buying"
    And the enterprise architect assigned "Selling Division" to "Selling"
    And a catalog "Lanes and units" of lanes with the join "Belongs To" to the organizational units
    When the join column of "Lanes and units" is filtered by "Buying Department"
    Then the filtered catalog shows only the lane "Buying"

  @AC1 @AC2 @REQ-077 @AC1 @AC2
  Scenario: Task saved in a role lane
    Given a diagram "Order handling" of the viewpoint that maps "Role" to a lane and "Organizational Unit" to a pool
    And the pool "Order handling" holds the role lane "Approver"
    When the enterprise architect drops the new task "Approve order" into "Approver" and saves with the assignment
    Then the task "Approve order" performs the role "Approver" and its parent is unchanged
