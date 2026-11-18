# Spec: openspec repository/entity-hierarchy
# Source material: requirements/user-stories/US-135-place-component-hierarchically.md, US-136-structuring-aid-without-connection.md; REQ-141, REQ-142; UC-13; ADR-021; ADR-032

@US-135 @US-136 @UC-13 @repository
Feature: Component hierarchy

  A component is placed under another one, which writes a version of the
  child with the field "Parent". Structuring aids group components for
  navigation without a connection. The metamodel marks the connection
  metatype of the automatic composition connection.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP

  @REQ-141 @AC1
  Scenario: Place a module under its system
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    When the architect sets "SAP S/4HANA" as parent of "Finance module"
    Then "Finance module" has the parent "SAP S/4HANA" and the version 2
    And "SAP S/4HANA" keeps the version 1
    And the history of "Finance module" names the field "Parent" in version 2

  @REQ-141
  Scenario: Parent in the diff
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect reads the diff of "Finance module" from version 1 to 2
    Then the diff shows the parent changing from none to "SAP S/4HANA"

  @REQ-141 @AC3
  Scenario: Detach to the root level
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect clears the parent of "Finance module"
    Then "Finance module" has no parent and the version 3

  Scenario: Same parent again
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect sets "SAP S/4HANA" as parent of "Finance module"
    Then "Finance module" has the parent "SAP S/4HANA" and the version 2

  @REQ-141
  Scenario: Cycle
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect sets "Finance module" as parent of "SAP S/4HANA"
    Then the hierarchy answer is 422 naming "parentEntityId"
    And "SAP S/4HANA" has no parent and the version 1

  @REQ-141
  Scenario Outline: Depth at the limit
    Given a chain of <levels> application components
    And an application component "Deep leaf" without a parent
    When the architect sets the deepest of the chain as parent of "Deep leaf"
    Then the hierarchy answer is <status>

    Examples:
      | levels | status |
      | 9      | 200    |
      | 10     | 422    |

  Scenario: Connection as parent
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the data flow "Hierarchy flow" between "SAP S/4HANA" and "Finance module"
    When the architect sets the connection "Hierarchy flow" as parent of "Finance module"
    Then the hierarchy answer is 422 naming "parentEntityId"

  @REQ-142 @AC1
  Scenario: Composition created
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    When the architect sets "SAP S/4HANA" as parent of "Finance module"
    Then a composition connection from "SAP S/4HANA" to "Finance module" with autoCreated exists
    And the answer names the connection

  @REQ-142 @AC2
  Scenario: Detach removes the composition
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect clears the parent of "Finance module"
    Then the automatically created composition connection is deleted

  @REQ-142
  Scenario: Move to another parent
    Given the application components "SAP S/4HANA", "SAP Finance Suite" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect sets "SAP Finance Suite" as parent of "Finance module"
    Then the old composition connection is deleted and one from "SAP Finance Suite" to "Finance module" exists

  @US-136 @AC1
  Scenario: Under a structuring aid
    Given the application component "Finance module" without a parent
    And the structuring aid "Finance landscape"
    When the architect sets "Finance landscape" as parent of "Finance module"
    Then "Finance module" has the parent "Finance landscape" and the version 2
    And the answer gives the reason "structuring-aid" and no connection

  @US-136 @AC1
  Scenario: Create a structuring aid
    When the architect creates the structuring aid "Integration landscape"
    Then it is an entity of the metatype "Structuring aid" at the root level

  @US-136
  Scenario Outline: Name of a structuring aid at the limits
    When the architect creates a structuring aid with a name of <length> characters
    Then the creation answer is <status>

    Examples:
      | length | status |
      | 255    | 201    |
      | 256    | 422    |

  @US-136 @AC3
  Scenario: Delete a structuring aid with children
    Given the structuring aid "Doomed landscape" with the application components "Child A", "Child B" and "Child C"
    When the architect deletes the structuring aid "Doomed landscape"
    Then it is deleted and "Child A", "Child B" and "Child C" are at the root level with the version 3 and not deleted

  Scenario: Delete an entity that is no structuring aid
    Given the application component "Not an aid" without a parent
    When the architect deletes "Not an aid" as structuring aid
    Then the hierarchy answer is 422 naming "entityId"

  @REQ-141
  Scenario: Stale version and missing If-Match
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    And the architect has set "SAP S/4HANA" as parent of "Finance module"
    When the architect sets the parent of "Finance module" with the version 1 and then without If-Match
    Then the first request is answered with 412 and the second with 428

  Scenario: A reader cannot arrange components
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    When the viewer sets "SAP S/4HANA" as parent of "Finance module"
    Then the hierarchy answer is 403

  @REQ-141
  Scenario: Audit of a parent change
    Given the application components "SAP S/4HANA" and "Finance module" without a parent
    When the architect sets "SAP S/4HANA" as parent of "Finance module"
    Then the audit records entity.parent-changed for "Finance module" with the parent of "SAP S/4HANA"

  Scenario: The batch cannot create a structuring aid
    When the architect writes an entity of the metatype "Structuring aid" through the batch
    Then the batch item is rejected naming "metatypeId"
