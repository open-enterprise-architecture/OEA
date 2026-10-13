# Derived from: requirements/req/req-096-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-13
Feature: REQ-096 – Navigation tree drag & drop with cycle protection

  The system MUST support reordering items (`sortOrder`) and
  moving folders and items via drag & drop. When
  moving a folder, the system MUST detect cycles (BR-03:
  a folder must not be moved into its own descendant) and
  visually mark the drop target as invalid; API attempts with
  a cycle MUST be rejected with HTTP 422.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Cycle detection – folder A is dragged via drag & drop under folder B and B is a descendant...
    Given the precondition according to the requirement is met
    When  folder A is dragged via drag & drop under folder B and B is a descendant of A
    Then  the drop target is visually marked as invalid; an API attempt responds with HTTP 422

  @AC2
  Scenario: AC2 – Reordering of items – items within a folder are reordered via drag & drop
    Given the precondition according to the requirement is met
    When  items within a folder are reordered via drag & drop
    Then  `sortOrder` is updated correctly; the new order is persistent

  @AC3
  Scenario: AC3 – Move item between folders – an item is moved via drag & drop from folder A to folder B
    Given the precondition according to the requirement is met
    When  an item is moved via drag & drop from folder A to folder B
    Then  the item is present in B and no longer in A
