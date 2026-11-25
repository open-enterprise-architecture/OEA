# Spec: openspec repository/deleted-entities (List deleted entities)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; REQ-159; UC-10; ADR-019; ADR-032

@US-074 @UC-10 @repository
Feature: List deleted entities

  A holder of entity:restore lists the deleted entities and connections,
  newest deletion first, with the actor and time of the deletion and the
  entity whose deletion took them along, to restore them.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the listed elements "ERP", "ERP Finance" and "CRM" where "ERP Finance" is a child of "ERP"
    And the listed connection "ERP to CRM" from "ERP" to "CRM"

  @REQ-159
  Scenario: Deleted with a cascade
    Given the enterprise architect deleted the listed "ERP"
    When the enterprise architect lists the deleted entities
    Then "ERP" and "ERP Finance" are listed with the enterprise architect and the deletion time
    And "ERP Finance" names "ERP" as the entity it was deleted with
    And the connection "ERP to CRM" is listed with the endpoints "ERP" and "CRM"
    And "CRM" is not listed

  @REQ-159
  Scenario: Filters
    Given the enterprise architect deleted the listed "ERP"
    When the enterprise architect lists the deleted entities with the name part "fin" and the metatype of the elements
    Then only "ERP Finance" is listed

  @REQ-159
  Scenario: Empty list
    When the enterprise architect lists the deleted entities
    Then no entity is listed

  @REQ-159
  Scenario Outline: Limits
    When the enterprise architect lists the deleted entities with the page size <size>
    Then the list answer is <status>

    Examples:
      | size | status |
      | 1    | 200    |
      | 200  | 200    |
      | 0    | 400    |
      | 201  | 400    |

  @REQ-159
  Scenario Outline: Limit of the name part
    When the enterprise architect lists the deleted entities with a name part of <length> characters
    Then the list answer is <status>

    Examples:
      | length | status |
      | 255    | 200    |
      | 256    | 400    |

  @REQ-159
  Scenario: Cursor of another query
    Given the enterprise architect deleted the listed "ERP"
    When the enterprise architect lists the deleted entities with the page size 1 and then asks for the next page with the name part "fin"
    Then the list answer is 400 naming "cursor"

  @REQ-159
  Scenario: Without entity:restore
    Given the enterprise architect deleted the listed "ERP"
    When the viewer lists the deleted entities
    Then the list answer is 403
