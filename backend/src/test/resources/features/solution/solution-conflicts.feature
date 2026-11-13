# Spec: openspec solution/solution-conflicts
# Source material: requirements/user-stories/US-044-conflict-warning.md; REQ-042 (realigned 2026-11-13)

@US-044 @UC-05 @solution
Feature: Conflicts with parallel solutions

  A delta modified or retiring warns of every other active solution that
  changes the same entity. The conflicts are computed on each read, never
  block a write and disappear once the other solution is completed or no
  longer holds its delta. The baseline names them for metatypes that show
  parallel changes. Each scenario works on its own metatypes, properties and
  solutions; the other solutions belong to another architect.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"
    And the landscape holds the application "CRM-Legacy"

  # Requirement: Conflicts of a delta

  @AC1 @REQ-042
  Scenario: Conflict detected
    Given the other solution "Solution A" retires "CRM-Legacy"
    When the architect retires "CRM-Legacy"
    Then the capture answers 201
    And the captured delta carries the conflicts "Solution A (draft, retiring)"
    And the conflict of "CRM-Legacy" with "Solution A" names its owner

  @AC2 @REQ-042
  Scenario Outline: No conflict with a completed solution
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the other solution "Solution A" is in status "<status>"
    When the architect retires "CRM-Legacy"
    Then the captured delta carries the conflicts ""

    Examples:
      | status      |
      | implemented |
      | archived    |

  @AC1 @REQ-042
  Scenario Outline: Every active status conflicts
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the other solution "Solution A" is in status "<status>"
    And the architect retired "CRM-Legacy"
    Then the delta of "CRM-Legacy" carries the conflicts "Solution A (<status>, retiring)"

    Examples:
      | status      |
      | draft       |
      | proposed    |
      | approved    |
      | in-progress |

  @AC5 @REQ-042
  Scenario: Multiple conflicts
    Given the other solution "Solution B" retires "CRM-Legacy"
    And the other solution "Solution A" retires "CRM-Legacy"
    When the architect retires "CRM-Legacy"
    Then the captured delta carries the conflicts "Solution A (draft, retiring), Solution B (draft, retiring)"

  @REQ-042
  Scenario: New deltas never conflict
    Given the other solution "Solution A" captures the new application "Warehouse Cloud"
    When the architect captures the new application "Warehouse Cloud" with "Owner" "Logistics"
    Then the captured delta carries the conflicts ""

  @REQ-042
  Scenario: Other entity
    Given the landscape holds the application "ERP-Core"
    And the other solution "Solution A" retires "ERP-Core"
    When the architect retires "CRM-Legacy"
    Then the captured delta carries the conflicts ""

  # Requirement: Content of a conflict

  @AC1 @REQ-042
  Scenario: Owner and delta type
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the other solution "Solution A" retires "ERP-Core"
    And the other solution "Solution A" is in status "proposed"
    When the architect modifies "ERP-Core" with "Release" "v5"
    Then the captured delta carries the conflicts "Solution A (proposed, retiring)"
    And the conflict of "ERP-Core" with "Solution A" names its owner

  # Requirement: Warning never blocks

  @AC1 @REQ-042
  Scenario: Editing a conflicting delta
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the other solution "Solution A" retires "ERP-Core"
    And the architect modified "ERP-Core" with "Release" "v5"
    When the architect edits the delta of "ERP-Core" to "Release" "v6"
    Then the edit answers 200
    And the edited delta carries the conflicts "Solution A (draft, retiring)"

  # Requirement: Conflicts in the delta list and the diff

  @AC3 @REQ-042
  Scenario: Warning persists
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect reads the deltas again
    Then the delta of "CRM-Legacy" carries the conflicts "Solution A (draft, retiring)"

  @REQ-042
  Scenario: Number of conflicted deltas over all pages
    Given the solution holds 60 deltas, 3 of which conflict, one of them on the second page of size 50
    When the architect reads the first page of the deltas of size 50
    Then the deltas count 3 conflicted

  @REQ-042
  Scenario: No conflicted deltas
    Given the architect retired "CRM-Legacy"
    When the architect reads the first page of the deltas of size 50
    Then the deltas count 0 conflicted

  @REQ-042
  Scenario: Conflicts in the diff
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the architect reads the diff
    Then the diff entry "CRM-Legacy" carries the conflicts "Solution A (draft, retiring)"

  # Requirement: Warning disappears

  @AC4 @REQ-042
  Scenario: Other solution implemented
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the other solution "Solution A" is in status "implemented"
    And the architect reads the first page of the deltas of size 50
    Then the delta of "CRM-Legacy" carries the conflicts ""
    And the deltas count 0 conflicted

  @AC4 @REQ-042
  Scenario: Other delta removed
    Given the other solution "Solution A" retires "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    When the other solution "Solution A" removes its delta of "CRM-Legacy"
    Then the delta of "CRM-Legacy" carries the conflicts ""

  # Requirement: Parallel changes in the baseline

  @REQ-042
  Scenario: Setting on
    Given the applications show parallel changes in the baseline
    And the other solution "Solution A" retires "CRM-Legacy"
    When the architect reads the baseline entry "CRM-Legacy"
    Then the baseline entry names the solutions "Solution A (draft, retiring)"

  @REQ-042
  Scenario: Setting off
    Given the other solution "Solution A" retires "CRM-Legacy"
    When the architect reads the baseline entry "CRM-Legacy"
    Then the baseline entry names the solutions ""

  @REQ-042
  Scenario: Completed solution not shown
    Given the applications show parallel changes in the baseline
    And the other solution "Solution A" retires "CRM-Legacy"
    And the other solution "Solution A" is in status "archived"
    When the architect reads the baseline entry "CRM-Legacy"
    Then the baseline entry names the solutions ""

  @REQ-042
  Scenario: Own delta not shown
    Given the applications show parallel changes in the baseline
    And the architect retired "CRM-Legacy"
    When the architect reads the baseline entry "CRM-Legacy"
    Then the baseline entry names the solutions ""
