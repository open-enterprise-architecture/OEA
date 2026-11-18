# Spec: openspec navigation/solution-browser
# Source material: requirements/user-stories/US-132-solution-browser-as-default-tab.md, US-133-solution-base-structure-explorer.md; REQ-138 to REQ-140; UC-13; ADR-020; ADR-032

@US-132 @US-133 @UC-13 @navigation
Feature: Solution browser

  The browser of the sidebar shows "Current state" first, then the
  plateaus and the projects with their active solutions, each with a
  fixed base structure and the number of its objects.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the browser has a baseline plateau

  @REQ-138 @REQ-140 @AC1
  Scenario: Plateaus after the current state
    Given the transition plateau "Browser 2027" is valid from "2027-01-01"
    And the target plateau "Browser TO-BE 2030" is valid from "2030-01-01"
    When the viewer reads the browser
    Then the plateaus of the browser include "Browser 2027" before "Browser TO-BE 2030"
    And the plateaus of the browser do not include the baseline

  @REQ-138 @AC1
  Scenario: Empty plateau
    Given the target plateau "Browser Empty Target" is valid from "2031-01-01"
    When the viewer reads the browser
    Then the plateau "Browser Empty Target" has 0 solutions

  @REQ-140 @AC2
  Scenario: Plateau and project solutions
    Given the target plateau "Browser Plateau CRM" is valid from "2032-01-01"
    And the browser solution "Browser CRM Migration" targets "Browser Plateau CRM"
    And the browser project solution "Browser ERP Upgrade" exists
    When the viewer reads the browser
    Then "Browser CRM Migration" is listed under the plateau "Browser Plateau CRM"
    And "Browser ERP Upgrade" is listed under the projects

  @REQ-140 @AC3
  Scenario Outline: Finished solutions leave the browser
    Given the browser project solution "Browser Finished <status>" has the status "<status>"
    When the viewer reads the browser
    Then "Browser Finished <status>" is not listed under the projects

    Examples:
      | status      |
      | implemented |
      | archived    |

  @REQ-139 @AC1 @AC2
  Scenario: Categories of the current state
    Given the browser notes the counts of the current state
    And 4 element entities, 2 connections and 1 deleted element entity were added
    When the viewer reads the browser
    Then the current state counts 4 more components, 2 more connections and 0 more catalogs

  @REQ-139 @AC2
  Scenario: Components of a solution
    Given the browser project solution "Browser Components" exists
    And "Browser Components" has the delta "new" "Browser CRM Cloud"
    And "Browser Components" has the delta "modified" for the entity "Browser SAP ERP"
    And "Browser Components" has the delta "retiring" for the entity "Browser Legacy CRM"
    And the browser project solution "Browser No Deltas" exists
    When the viewer reads the browser
    Then "Browser Components" shows 3 components and "Browser No Deltas" shows 0

  @REQ-139 @AC1
  Scenario: Delta types in a solution
    Given the browser project solution "Browser Delta Types" exists
    And "Browser Delta Types" has the delta "new" "Browser CRM Cloud B"
    And "Browser Delta Types" has the delta "modified" for the entity "Browser SAP ERP B"
    And "Browser Delta Types" has the delta "retiring" for the entity "Browser Legacy CRM B"
    When the viewer reads the components of "Browser Delta Types"
    Then the components are listed as "Browser CRM Cloud B" new, "Browser Legacy CRM B" retiring, "Browser SAP ERP B" modified

  @REQ-139 @AC1
  Scenario: Second page
    Given 51 element entities were added
    When the viewer reads the components of the current state in pages of 50
    Then the last page holds the rest of the components and the first page holds 50

  @REQ-138
  Scenario: Another person's personal catalog
    Given the browser notes the counts of the current state
    And the enterprise architect has the personal catalog "Browser Kurt's Candidates" and the instance catalog "Browser Shared"
    When the viewer reads the browser
    Then the current state counts 0 more components, 0 more connections and 1 more catalogs
    And the viewer does not see "Browser Kurt's Candidates" among the catalogs but the architect does

  @REQ-138
  Scenario: Reader without entity:read
    When a reader without entity:read reads the browser
    Then the browser answer is 403

  @REQ-139
  Scenario Outline: Page size at the limits
    When the viewer reads the components of the current state with the page size <size>
    Then the browser answer is <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 0    | 400    |
      | 201  | 400    |

  @REQ-139
  Scenario: Category a solution does not have
    Given the browser project solution "Browser Only Components" exists
    When the viewer reads the category "connections" of "Browser Only Components"
    Then the browser answer is 422 naming "category"

  @REQ-139
  Scenario: Unknown solution
    When the viewer reads the components of an unknown solution
    Then the browser answer is 404

  @US-135 @REQ-141
  Scenario: Nested components
    Given the browser component "Browser S4" with the child "Browser Finance module" and the structuring aid "Browser Finance landscape" holding "Browser Payroll"
    When the viewer reads the root level of the components
    Then the root level lists "Browser S4" with 1 child and "Browser Finance landscape" as a structuring aid but not "Browser Finance module"
    And the children of "Browser S4" are "Browser Finance module"
    And the children of "Browser Finance landscape" are "Browser Payroll"

  @US-135 @REQ-141
  Scenario: Child of a deleted parent
    Given the browser component "Browser Orphan Parent" with the child "Browser Orphan" whose parent was deleted
    When the viewer reads the root level of the components
    Then the root level lists "Browser Orphan"

  @REQ-141
  Scenario Outline: A parent only for the components of the current state
    When the viewer reads the category "<category>" of the current state with a parent
    Then the browser answer is 422 naming "parentEntityId"

    Examples:
      | category    |
      | connections |
      | catalogs    |

  @REQ-141
  Scenario Outline: Boundaries of the parent
    When the viewer reads the children of the entity <parent>
    Then the browser answer is <status>

    Examples:
      | parent     | status |
      | 0          | 400    |
      | 2000000000 | 404    |

  @US-135 @REQ-141
  Scenario: Arrange components needs entity:write
    Given the browser component "Browser Fixed" without a parent
    When the viewer tries to place "Browser Fixed" under itself
    Then the browser answer is 403

