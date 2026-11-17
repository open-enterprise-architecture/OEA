# Spec: openspec metamodel/viewpoints (us-090-viewpoints)
# Source material: requirements/user-stories/US-090-viewpoint-crud.md, US-093; UC-12 (owner decisions 2026-11-17)

@US-090 @UC-12 @metamodel
Feature: Viewpoints

  A viewpoint names the element metatypes and connection types of an
  organization-specific view. It is generic: notations follow with the
  diagrams. Holders of viewpoint:manage create, edit and delete viewpoints;
  readers with entity:read see them.

  Background:
    Given a running repository instance with a metamodel administrator
    And the instance has the metatypes "VP Application" and "VP Data Object" and the connection type "VP Data Flow"

  # Requirement: Create a viewpoint

  @AC1 @REQ-090
  Scenario: Viewpoint created
    When the architect creates the viewpoint "Application Landscape" with "VP Application" and "VP Data Object" and the connection type "VP Data Flow"
    Then the viewpoint allows "VP Application" and "VP Data Object" and the connection type "VP Data Flow"

  # Requirement: Valid viewpoint

  @REQ-090
  Scenario Outline: Limit of the name
    When the architect creates a viewpoint with a name of <length> characters
    Then the viewpoint answer is <status>

    Examples:
      | length | status |
      | 255    | 201    |
      | 256    | 400    |

  @REQ-090
  Scenario: Connection type as metatype
    When the architect creates a viewpoint "Wrong Kind" with the connection type "VP Data Flow" as metatype
    Then the viewpoint answer is 422 naming "metatypeIds"

  # Requirement: Read viewpoints

  @REQ-090
  Scenario: Reader lists the viewpoints
    Given the viewpoint "Integration View" allows "VP Application"
    When the viewer lists the viewpoints
    Then the viewpoint list holds "Integration View"

  # Requirement: Edit a viewpoint

  @REQ-090
  Scenario: Edited with its version
    Given the viewpoint "Integration View" allows "VP Application"
    When the architect changes the description of "Integration View" with its version
    Then the viewpoint has the version 2

  @REQ-090
  Scenario: Stale version
    Given the viewpoint "Integration View" allows "VP Application"
    When the architect changes the description of "Integration View" with the version 7
    Then the viewpoint answer is 412

  # Requirement: Delete a viewpoint

  @AC3 @US-093 @AC2
  Scenario: Viewpoint deleted
    Given the viewpoint "Old View" allows "VP Application"
    When the architect deletes "Old View" with its version
    Then the viewpoint answer is 204
    And "Old View" is no longer listed

  # Requirement: Permissions for viewpoints

  @REQ-090
  Scenario: Without viewpoint:manage
    When the viewer creates the viewpoint "Forbidden View" with "VP Application"
    Then the viewpoint answer is 403
