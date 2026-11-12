# Spec: openspec solution/solution-baseline
# Source material: requirements/user-stories/US-039-show-landscape-baseline.md; REQ-039 (realigned 2026-11-12)

@US-039 @UC-05 @solution
Feature: Baseline of a solution

  A solution shows the current IT landscape as its baseline: the entities
  of the catalog that are not deleted, searchable, filterable by metatype
  and paged. Each scenario works on its own entities.

  Background:
    Given a running repository instance with a metamodel administrator
    And the architect created a solution for the baseline

  @AC1 @REQ-039
  Scenario: Entities of the landscape
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core" and the data flow "Orders"
    When the architect loads the baseline
    Then the baseline lists exactly "CRM-Legacy, ERP-Core"

  @AC2 @REQ-039
  Scenario: Deleted entity
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core"
    And "CRM-Legacy" is deleted
    When the architect loads the baseline
    Then the baseline lists exactly "ERP-Core"

  @AC3 @REQ-039
  Scenario: Current state
    Given the landscape holds the applications "ERP-Core" and "CRM-Legacy"
    And "ERP-Core" was renamed to "ERP-Core 2" in version 2
    When the architect loads the baseline
    Then the baseline lists "ERP-Core 2" in version 2 and not "ERP-Core"

  @REQ-039
  Scenario: Unknown solution
    When the architect loads the baseline of an unknown solution
    Then the baseline answers 404

  @REQ-039
  Scenario: Without entity:read
    When a portal reader loads the baseline
    Then the baseline answers 403

  @AC5 @REQ-039
  Scenario: Filter by metatype
    Given the landscape holds 12 applications and 8 components
    When the architect loads the baseline filtered by the applications' metatype
    Then the baseline lists 12 entities, all applications

  @REQ-039
  Scenario: Search by part of the name
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core"
    When the architect searches the applications for "erp"
    Then the baseline lists exactly "ERP-Core"

  @REQ-039
  Scenario Outline: Limit of the search term
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core"
    When the architect searches the baseline with a term of <length> characters
    Then the baseline answers <status>

    Examples:
      | length | status |
      | 255    | 200    |
      | 256    | 400    |

  @REQ-071
  Scenario: Pages by cursor
    Given the landscape holds 60 applications, some with equal names
    When the architect loads every page of size 20 with the cursor of the previous one
    Then the pages hold every entity exactly once in the order of the pages by number, with the total 60
    And the last page has no next cursor

  @REQ-039
  Scenario Outline: Limits of the page size
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core"
    When the architect loads the baseline with page size <size>
    Then the baseline answers <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 0    | 400    |
      | 201  | 400    |

  @REQ-071
  Scenario: Cursor of another query
    Given the landscape holds 5 applications, some with equal names
    When the architect sends the cursor of the unfiltered baseline with a metatype filter
    Then the baseline answers 400 naming "cursor"
    When the architect sends a changed cursor
    Then the baseline answers 400 naming "cursor"

  # us-041-modified-delta – Requirement: Read one entry of the baseline

  @US-041 @REQ-039
  Scenario: Entry with its values
    Given the landscape holds the applications "ERP-Core" and "CRM-Legacy"
    And "ERP-Core" has the value "v4" of the property "Release"
    When the architect reads the entry "ERP-Core" of the baseline
    Then the entry holds the name "ERP-Core", its metatype, version 1 and "Release" "v4"

  @US-041 @REQ-160
  Scenario: Restricted value of an entry redacted
    Given the landscape holds the applications "ERP-Core" and "CRM-Legacy"
    And "ERP-Core" has the value "900" of the property "Cost"
    And the property "Cost" is readable only by the role "enterprise-architect"
    When the viewer reads the entry "ERP-Core" of the baseline
    Then "Cost" of the entry is redacted

  @US-041 @REQ-039
  Scenario: Not an entry of the baseline
    Given the landscape holds the applications "CRM-Legacy" and "ERP-Core" and the data flow "Orders"
    And "CRM-Legacy" is deleted
    When the architect reads an unknown entity ID as an entry of the baseline
    Then the baseline answers 404
    When the architect reads the entry "CRM-Legacy" of the baseline
    Then the baseline answers 404
    When the architect reads the entry "Orders" of the baseline
    Then the baseline answers 404

  @US-041 @REQ-039
  Scenario: Entry without entity:read
    Given the landscape holds the applications "ERP-Core" and "CRM-Legacy"
    When a portal reader reads the entry "ERP-Core" of the baseline
    Then the baseline answers 403
