# Spec: openspec catalog/catalog-query (Show deleted rows)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; REQ-159, REQ-046; UC-06, UC-10; ADR-019; ADR-032

@US-074 @UC-06 @catalog
Feature: Show deleted rows in a catalog

  A holder of entity:restore queries a catalog with deleted rows, marked with
  their deletion time, with the same columns, filters, sorting and paging.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And a catalog "Application Inventory" of 10 active deleted-rows applications and the deleted application "Legacy CRM"

  @REQ-159
  Scenario: Deleted rows shown
    When the enterprise architect queries the deleted-rows catalog with "includeDeleted=true"
    Then the deleted-rows catalog has 11 rows
    And "Legacy CRM" is marked as deleted with its deletion time and version
    And the other rows carry no deletion members

  @REQ-159
  Scenario: Without entity:restore
    When the viewer queries the deleted-rows catalog with "includeDeleted=true"
    Then the deleted-rows answer is 403

  @REQ-159
  Scenario: Default unchanged
    When the enterprise architect queries the deleted-rows catalog with ""
    Then the deleted-rows catalog has 10 rows
    And "Legacy CRM" is not a row of the deleted-rows catalog

  @REQ-159 @REQ-071
  Scenario Outline: Cursor of the other mode
    When the enterprise architect queries the deleted-rows catalog with "<first>pageSize=1"
    And the enterprise architect sends its next cursor with "<second>pageSize=1&page=1"
    Then the deleted-rows answer is 400 naming "cursor"

    Examples:
      | first                | second               |
      |                      | includeDeleted=true& |
      | includeDeleted=true& |                      |

  @REQ-159
  Scenario Outline: Page sizes with the flag
    When the enterprise architect queries the deleted-rows catalog with "includeDeleted=true&pageSize=<size>"
    Then the deleted-rows answer is <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 201  | 400    |

  @REQ-159
  Scenario: Restore, then an active row
    Given "Legacy CRM" is restored
    When the enterprise architect queries the deleted-rows catalog with "includeDeleted=true"
    Then the deleted-rows catalog has 11 rows
    And the other rows carry no deletion members

  @REQ-159 @REQ-065
  Scenario: Deleted connection in a connection catalog
    Given a connection catalog of "serves" connections, and "ERP serves Billing" deleted with "ERP"
    When the enterprise architect queries the deleted-rows catalog with "includeDeleted=true"
    Then the connection "ERP serves Billing" is a row marked as deleted whose source column shows "ERP"

  @REQ-159 @REQ-048
  Scenario: Deleted row without join results
    Given the application "Active App" is connected to the interface "SFTP", and "Legacy CRM" was connected to it
    When the enterprise architect queries the deleted-rows catalog with a join in mode "rows" and "includeDeleted=true"
    Then "Legacy CRM" is one row without join results
