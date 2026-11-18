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

  # us-091-viewpoint-notations

  # Requirement: Notation of a viewpoint

  @US-090 @REQ-090
  Scenario: ArchiMate viewpoint
    When the architect creates the viewpoint "Cloud Security View" with the notation "archimate3" and "VP Application"
    Then the viewpoint answer is 201
    And the viewpoint has the notation "archimate3"

  @US-090 @REQ-090
  Scenario: Existing viewpoint
    Given the viewpoint "Integration View" allows "VP Application"
    Then the viewpoint "Integration View" has the notation "generic" and is user-defined

  # Requirement: Notation mapping

  @AC2 @US-091 @REQ-091
  Scenario: Element of the notation
    When the architect maps "VP Application" to "archimate:ApplicationComponent" in the new "archimate3" viewpoint "Mapped View"
    Then the viewpoint answer is 201
    And "VP Application" is mapped to "archimate:ApplicationComponent"

  @AC2 @US-091 @REQ-091
  Scenario: Element of another notation
    When the architect maps "VP Application" to "bpmn:Task" in the new "archimate3" viewpoint "Foreign Element View"
    Then the viewpoint answer is 422 naming "mappings"

  @US-091 @REQ-091
  Scenario: Generic box in any notation
    When the architect maps "VP Data Object" to "generic:Box" in the new "bpmn2" viewpoint "Generic Box View"
    Then the viewpoint answer is 201

  @AC2 @US-091 @REQ-091
  Scenario Outline: Size boundaries
    When the architect maps "VP Application" to "archimate:ApplicationComponent" with the width <width> and height <height> in the new "archimate3" viewpoint "<name>"
    Then the viewpoint answer is <status>

    Examples:
      | name       | width | height | status |
      | Size Low  | 1     | 1      | 201    |
      | Size High  | 2000  | 2000   | 201    |
      | Size Zero  | 0     | 80     | 400    |
      | Size Over  | 2001  | 80     | 400    |

  # Requirement: Fallback rendering

  @US-091 @REQ-091
  Scenario: Unmapped entity type
    When the architect creates the viewpoint "Fallback View" with the notation "archimate3" and "VP Application"
    Then the effective element of "VP Application" is "generic:Box" as a fallback

  # Requirement: Element catalog

  @AC1 @US-091 @REQ-091
  Scenario: ArchiMate elements
    When the architect reads the element catalog
    Then the catalog holds "archimate:ApplicationComponent" as an element of "archimate3"
    And the catalog holds "archimate:Flow" as a relationship of "archimate3"

  # Requirement: System-defined viewpoints are read-only

  @AC1 @US-092 @REQ-092
  Scenario: Delete a system-defined viewpoint
    Given the system-defined viewpoint "VP Installed Landscape" of the package "vp-installed"
    When the architect deletes "VP Installed Landscape" with its version
    Then the viewpoint answer is 422 as read-only
    And "VP Installed Landscape" is still listed

  @AC1 @US-092 @REQ-092
  Scenario: Update with the current version
    Given the system-defined viewpoint "VP Updated Landscape" of the package "vp-installed"
    When the architect changes the description of "VP Updated Landscape" with its version
    Then the viewpoint answer is 422 as read-only

  # Requirement: Origin of a viewpoint set only by the import

  @US-092 @REQ-092
  Scenario: Forged origin
    When the architect creates the viewpoint "Forged View" with a forged system-defined origin
    Then the viewpoint answer is 201
    And the viewpoint is user-defined without a package
