# Derived from: requirements/req/req-141-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration

@functional @should @UC-13
Feature: REQ-141 – Components can be nested hierarchically in the browser

  The system MUST make it possible to arrange ArchitectureEntity elements
  (`isConnection=false`) hierarchically beneath each other in the browser
  by assigning one element to another as its parent element
  (`parentEntityId`). The hierarchy MUST be displayed in the
  browser panel as a nested tree.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Entity A has `parentEntityId = Entity B`
    Given Entity A has `parentEntityId = Entity B`
    When  "Components" node expanded
    Then  Entity A appears indented under Entity B

  @AC2
  Scenario: AC2 – Entity C with `isStructuralGrouper=true`
    Given Entity C with `isStructuralGrouper=true`
    When  browser is displayed
    Then  Entity C appears with folder icon, no ArchiMate icon

  @AC3
  Scenario: AC3 – Entity D with `parentEntityId=null`
    Given Entity D with `parentEntityId=null`
    When  "Components" expanded
    Then  Entity D appears at the first indentation level
