# Derived from: requirements/req/req-114-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-18
Feature: REQ-114 – Atomic package import with conflict handling

  The package import MUST be executed as an atomic transaction:
  either all valid building blocks are imported or none
  (rollback on error). If the package contains building blocks with an ID
  that already exists in the system, the system MUST display these conflicts as a
  list and let the user choose between "Skip" (default) and
  "Update".

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Abort on cycle – the imported package contains a cycle in the ABB refinement graph
    Given the precondition according to the requirement is met
    When  the imported package contains a cycle in the ABB refinement graph
    Then  the import aborts; no building block is imported; the error message describes the cycle

  @AC2
  Scenario: AC2 – Conflict list – the package contains 3 building blocks with already existing IDs
    Given the precondition according to the requirement is met
    When  the package contains 3 building blocks with already existing IDs
    Then  the system shows the conflict list; the default is "Skip" for all conflicts

  @AC3
  Scenario: AC3 – Rollback on DB error – a database error occurs after importing 20 of 50 building blocks
    Given the precondition according to the requirement is met
    When  a database error occurs after importing 20 of 50 building blocks
    Then  a complete rollback is executed; 0 building blocks are imported
