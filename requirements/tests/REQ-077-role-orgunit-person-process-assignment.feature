# Derived from: requirements/req/req-077-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (integration test + Playwright E2E)

@functional @must @UC-10
Feature: REQ-077 – Assign roles, organizational units, and persons to process elements

  The system MUST make it possible, directly in the process diagram:
  1. to assign lanes an **organizational unit**
  (`bpmn-lane-belongs-to-org-unit`) and any number of **roles**
  (`bpmn-lane-performs-role`) 2.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – OrgUnit → lane – Anna right-clicks on a lane and selects "Assign orga...
    Given the precondition according to the requirement is met
    When  Anna right-clicks on a lane and selects "Assign organizational unit"; searches "Procurement"; selects the OrgUnit
    Then  `bpmn-lane-belongs-to-org-unit` connection saved; lane shows OrgUnit badge

  @AC2
  Scenario: AC2 – Multiple roles → lane – Anna assigns the roles "Approver" and "Reviewer" to a lane
    Given the precondition according to the requirement is met
    When  Anna assigns the roles "Approver" and "Reviewer" to a lane
    Then  two `bpmn-lane-performs-role` connections saved; both roles visible as chips in the lane

  @AC3
  Scenario: AC3 – OrgUnit → pool – Anna links the pool with the OrgUnit "Sales Division"
    Given the precondition according to the requirement is met
    When  Anna links the pool with the OrgUnit "Sales Division"
    Then  `bpmn-pool-represents-org-unit` connection saved; pool header shows OrgUnit name

  @AC4
  Scenario: AC4 – Person → UserTask – Anna searches for and assigns the person "Maria M...
    Given the precondition according to the requirement is met
    When  Anna searches for and assigns the person "Maria Müller" in the detail panel of a UserTask
    Then  `bpmn-task-assigned-to` connection saved; task shape shows initials "MM" as avatar icon

  @AC5
  Scenario: AC5 – Remove assignment – Anna removes an existing assignment (e.g. OrgUnit of a lane)
    Given the precondition according to the requirement is met
    When  Anna removes an existing assignment (e.g. OrgUnit of a lane)
    Then  connection is deleted from the repository; badge/chip disappears immediately from the canvas

  @AC6
  Scenario: AC6 – Catalog join OrgUnit – multiple lanes have OrgUnit assignments
    Given multiple lanes have OrgUnit assignments
    When  catalog for `bpmn-lane` with join column `bpmn-lane-belongs-to-org-unit.name` opened
    Then  OrgUnit name visible as column; filtering by OrgUnit possible

  @AC7
  Scenario: AC7 – Catalog join person – multiple UserTasks have person assignments
    Given multiple UserTasks have person assignments
    When  catalog for `bpmn-user-task` with join column `bpmn-task-assigned-to.name` opened
    Then  person name as column; tasks without assignment show an empty cell

  @AC8
  Scenario: AC8 – Quick-create OrgUnit – Anna searches in the assignment dialog for an...
    Given the precondition according to the requirement is met
    When  Anna searches in the assignment dialog for an OrgUnit that does not exist and selects "Create new"
    Then  mini wizard opens (name, select type); after confirmation the new OrgUnit is assigned immediately

  @AC9
  Scenario: AC9 – Web portal read-only – viewer user opens the process diagram in the web portal
    Given the precondition according to the requirement is met
    When  viewer user opens the process diagram in the web portal
    Then  OrgUnit badges and person avatars visible; no edit actions available; tooltips with full name
