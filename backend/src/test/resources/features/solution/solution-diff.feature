# Spec: openspec solution/solution-diff
# Source material: requirements/user-stories/US-043-diff-view.md; REQ-041 (realigned 2026-11-13)

@US-043 @UC-05 @solution
Feature: Diff of a solution

  The diff shows, per delta, the current state of the entity in the catalog
  next to its target state after the solution. It is computed on each read,
  paged like the delta list, and redacted like it. Each scenario works on its
  own metatypes, properties and solutions.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the deltas
    And the applications have the mandatory property "Owner" and the integer property "Users"

  # Requirement: Read the diff

  @AC5 @REQ-041
  Scenario: No deltas in the diff
    When the architect reads the diff
    Then the diff lists exactly ""

  @REQ-041
  Scenario: Unknown solution or missing permission
    When the architect reads the diff of an unknown solution
    Then the read answers 404
    When a portal reader reads the diff
    Then the read answers 403

  # Requirement: Only affected entities

  @AC4 @REQ-041
  Scenario: Only affected entities
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the landscape holds the application "CRM-Legacy"
    And the landscape holds the application "HR-Suite"
    And the architect retired "CRM-Legacy"
    And the architect modified "ERP-Core" with "Release" "v5"
    And the architect captured the new application "Salesforce" with "Owner" "Sales"
    When the architect reads the diff
    Then the diff lists exactly "new Salesforce, modified ERP-Core, retiring CRM-Legacy"

  # Requirement: Paging of the diff

  @REQ-041
  Scenario: Pages of the diff by cursor
    Given the solution holds 50 new, 40 modified and 30 retiring deltas
    When the architect loads every page of the diff of size 50 by cursor
    Then the diff pages hold every entry exactly once in the order of the delta list
    And each page of the diff carries the counts 50 new, 40 modified and 30 retiring
    And the last page of the diff has no next cursor

  @REQ-041
  Scenario Outline: Limits of the page size of the diff
    When the architect reads the diff with page size <size>
    Then the read answers <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 0    | 400    |
      | 201  | 400    |

  # Requirement: Entry of a retiring, a new and a modified entity

  @AC1 @REQ-041
  Scenario: Retiring entity
    Given the landscape holds "CRM-Legacy" with "Release" "v2" and "Users" "40"
    And the architect retired "CRM-Legacy"
    When the architect reads the diff
    Then the diff entry "CRM-Legacy" has a current state and no target state
    And the diff field "Release" of "CRM-Legacy" is "v2" now

  @AC2 @REQ-041
  Scenario: New entity
    Given the architect captured the new application "Salesforce" with "Owner" "Sales"
    When the architect reads the diff
    Then the diff entry "Salesforce" has no current state
    And the diff field "Owner" of "Salesforce" changes from no value to "Sales"

  @AC3 @REQ-041
  Scenario: Modified entity
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    When the architect reads the diff
    Then the diff field "Release" of "ERP-Core" changes from "v4" to "v5"
    And the diff field "Owner" of "ERP-Core" stays "IT"

  @AC3 @REQ-041
  Scenario: Removed value
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modifies "ERP-Core" with an empty "Users"
    When the architect reads the diff
    Then the diff field "Users" of "ERP-Core" changes from "250" to no value

  @REQ-041
  Scenario: Reference value
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And "ERP-Core" is hosted on the component "DC-Zurich"
    And the architect modified "ERP-Core" with "Release" "v5"
    When the architect reads the diff
    Then the diff field "Hosted on" of "ERP-Core" refers to "DC-Zurich" on both sides

  # Requirement: Drift since capture

  @REQ-041
  Scenario: Catalog changed since capture
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    And the catalog value of "Release" of "ERP-Core" changes to "v4.1"
    When the architect reads the diff
    Then the diff field "Release" of "ERP-Core" changes from "v4.1" to "v5"
    And the diff field "Release" of "ERP-Core" is marked as changed in the catalog since capture

  @REQ-041
  Scenario: Catalog unchanged
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the architect modified "ERP-Core" with "Release" "v5"
    When the architect reads the diff
    Then the diff field "Release" of "ERP-Core" is not marked as changed in the catalog since capture

  # Requirement: Entity no longer in the catalog

  @REQ-041
  Scenario: Entity deleted after capture
    Given the landscape holds the application "CRM-Legacy"
    And the architect retired "CRM-Legacy"
    And "CRM-Legacy" is deleted from the catalog
    When the architect reads the diff
    Then the diff entry "CRM-Legacy" is no longer in the catalog

  # Requirement: Visibility in the diff

  @REQ-160 @REQ-041
  Scenario: Restricted value redacted
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Cost" of the applications is role-restricted to the role "enterprise-architect"
    And the architect modified "ERP-Core" with "Cost" "100"
    When the viewer reads the diff
    Then the diff field "Cost" of "ERP-Core" is hidden

  @REQ-160 @REQ-041
  Scenario: Restricted value readable
    Given the landscape holds "ERP-Core" with "Release" "v4" and "Users" "250"
    And the property "Cost" of the applications is role-restricted to the role "enterprise-architect"
    And the architect modified "ERP-Core" with "Cost" "100"
    When the architect reads the diff
    Then the diff field "Cost" of "ERP-Core" changes from no value to "100"
