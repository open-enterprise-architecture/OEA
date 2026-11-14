# Spec: openspec repository/entity-history
# Source material: requirements/user-stories/US-098-version-history-timeline.md, REQ-098, REQ-160

@US-098 @UC-14 @repository
Feature: Change history of an entity

  Architects and auditors trace who changed an entity when. The history lists
  the versions newest first with the fields they changed, without values, and
  never reveals the value of a field the reader may not read.

  Background:
    Given a running repository instance with a metamodel administrator
    And the history persons "Michael" and "Kurt"

  # Requirement: Version history of an entity

  @AC1 @REQ-098
  Scenario: Timeline with versions
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect reads the history of "CRM System"
    Then the history lists the versions "5, 4, 3, 2, 1"

  @AC1 @REQ-098
  Scenario: Changed fields of a version
    Given the history entity "CRM System" with 5 versions by "Kurt"
    And the public property "lifecycleStatus"
    And version 3 of "CRM System" changed the name and "lifecycleStatus"
    When the enterprise architect reads the history of "CRM System"
    Then version 3 lists "name, lifecycleStatus"

  # Requirement: Creation and errors of the history read

  @AC3 @REQ-098
  Scenario: Entity without changes
    Given the history entity "Billing" with 1 versions by "Kurt"
    When the enterprise architect reads the history of "Billing"
    Then the history lists the versions "1"
    And version 1 lists ""
    And the creation names "Kurt"

  @REQ-098
  Scenario: Unknown entity
    When the enterprise architect reads the history of an entity that does not exist
    Then the history request is answered with 404

  @REQ-098
  Scenario: Without permission
    Given the history entity "CRM System" with 2 versions by "Kurt"
    When the portal reader reads the history of "CRM System"
    Then the history request is answered with 403

  # Requirement: Filters of the history; Actors of the history

  @AC2 @REQ-098
  Scenario: Filter by actor
    Given the history entity "CRM System" with 3 versions by "Michael" and 2 by "Kurt"
    When the enterprise architect reads the history of "CRM System" filtered by "Michael"
    Then the history lists the versions "3, 2, 1"

  @AC2 @REQ-098
  Scenario: Actors with a filter
    Given the history entity "CRM System" with 3 versions by "Michael" and 2 by "Kurt"
    When the enterprise architect reads the history of "CRM System" filtered by "Michael"
    Then the actors of the history are "Kurt, Michael"

  @AC2 @REQ-098
  Scenario: Period boundaries
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect reads the history of "CRM System" from version 2 to version 4 shifted by 0 ms
    Then the history lists the versions "4, 3, 2"

  @AC2 @REQ-098
  Scenario: Just outside the period
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect reads the history of "CRM System" from version 2 to version 4 shifted by 1 ms
    Then the history lists the versions "3"

  @AC2 @REQ-098
  Scenario: Reversed period
    Given the history entity "CRM System" with 5 versions by "Kurt"
    When the enterprise architect reads the history of "CRM System" from version 4 to version 2 shifted by 0 ms
    Then the history request is answered with 400 naming "from"

  # Requirement: Paging of the history

  @REQ-098 @REQ-071
  Scenario: Exactly one page
    Given the history entity "Large System" with 50 versions by "Kurt"
    When the enterprise architect reads the history of "Large System"
    Then the history lists 50 versions and has no next page

  @REQ-098 @REQ-071
  Scenario: One version more
    Given the history entity "Large System" with 51 versions by "Kurt"
    When the enterprise architect reads the history of "Large System"
    Then the history lists 50 versions from 51 to 2
    And the next page of the history lists the versions "1"

  @REQ-098 @REQ-071
  Scenario Outline: Page size limits
    Given the history entity "CRM System" with 2 versions by "Kurt"
    When the enterprise architect reads the history of "CRM System" with the page size <size>
    Then the history request is answered with <status>

    Examples:
      | size | status |
      | 200  | 200    |
      | 201  | 400    |
      | 0    | 400    |

  # Requirement: Restricted fields in the history; Hiding restricted fields

  @REQ-098 @REQ-160
  Scenario: Restricted field shown as restricted
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed the name and "investmentCost"
    When the viewer reads the history of "CRM System"
    Then version 2 lists "name, investmentCost (restricted)"

  @REQ-098
  Scenario: Restricted field hidden
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed the name and "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the viewer reads the history of "CRM System"
    Then version 2 lists "name"

  @REQ-098
  Scenario: Readable for a read role
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed the name and "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the enterprise architect reads the history of "CRM System"
    Then version 2 lists "name, investmentCost"

  @REQ-098
  Scenario: Version with only hidden changes
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed "investmentCost"
    And the history visibility of "viewer" for "Application Component" is "hide"
    When the viewer reads the history of "CRM System"
    Then the history lists the versions "3, 2, 1"
    And version 2 lists ""

  @REQ-098 @REQ-127
  Scenario: Connection-scoped outside the scope
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the connection-scoped property "securityClassification" with the viewer linked to an element outside its scope
    And version 2 of "CRM System" changed "securityClassification"
    When the viewer reads the history of "CRM System"
    Then version 2 lists "securityClassification (restricted)"

  @REQ-098 @REQ-128
  Scenario: Override role
    Given the history entity "CRM System" with 3 versions by "Kurt"
    And the property "investmentCost" readable only by "enterprise-architect"
    And version 2 of "CRM System" changed "investmentCost"
    And the role "viewer" is on the override role list
    When the viewer reads the history of "CRM System"
    Then version 2 lists "investmentCost"
