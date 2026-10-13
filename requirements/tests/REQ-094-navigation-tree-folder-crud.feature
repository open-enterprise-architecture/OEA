# Derived from: requirements/req/req-094-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-13
Feature: REQ-094 – Navigation tree folder CRUD

  The system MUST allow creating, renaming and deleting
  TreeNode folders in the navigation tree. Folder names MUST be
  unique among siblings (BR-01).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create folder – a new folder is created under a parent
    Given the precondition according to the requirement is met
    When  a new folder is created under a parent
    Then  the API responds with HTTP 201; the folder is visible in the tree

  @AC2
  Scenario: AC2 – Reject duplicate name – a folder is created with a name that already exists under the same parent...
    Given the precondition according to the requirement is met
    When  a folder is created with a name that already exists under the same parent
    Then  the API responds with HTTP 422 "Name already exists"

  @AC3
  Scenario: AC3 – Recursive deletion – a folder with 3 child folders and 10 items is deleted
    Given the precondition according to the requirement is met
    When  a folder with 3 child folders and 10 items is deleted
    Then  child folders and items are removed; referenced entities remain untouched

  @AC4
  Scenario: AC4 – Root node protection – a DELETE request is sent to `/tree-nodes/root`
    Given the precondition according to the requirement is met
    When  a DELETE request is sent to `/tree-nodes/root`
    Then  the API responds with HTTP 422 "Root node cannot be deleted"
