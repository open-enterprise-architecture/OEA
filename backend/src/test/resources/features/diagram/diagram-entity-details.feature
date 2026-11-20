# Spec: openspec diagram/diagram-entity-details (us-074-diagram-entity-details)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; UC-10 step 6; issue #69; owner decisions 2026-11-19 and 2026-11-20
# The details panel (open on selection, inputs only where writable, marking of missing values, conflict dialog,
# reader mode) is covered by the frontend specs; this feature covers the API. The limits are covered by
# DiagramEntityDetailsTests.

@US-074 @UC-10 @diagram
Feature: Diagram entity details

  The details of an entity shown in a diagram, the reference targets of a reference property, and the
  changes of shown entities that are saved together with the layout, guarded by the entity version.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the diagram viewpoint "Ded Process Model" with the notation "bpmn2" exists
    And the details metatype "Ded Component" exists with the optional property "Ded Status"
    And the details metatype "Ded Component" has the role-restricted property "Ded Investment" that "enterprise-architect" reads and "enterprise-architect" writes
    And the details metatype "Ded Component" has the role-restricted property "Ded Budget" that "enterprise-architect" reads and "security-admin" writes
    And the details metatype "Ded Component" has the reference property "Ded Hosted on" to "Ded Component"
    And the metatype "Ded Business Process" has the mandatory property "Ded Process Owner" without default value
    And the diagram viewpoint "Ded Process Model" allows the metatypes "Ded Component" and "Ded Business Process"

  # Requirement: Read the details of a shown entity

  @AC8
  Scenario: Details of a shown entity
    Given the diagram "Ded Order to Cash" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded SAP ERP" of the metatype "Ded Component" exists
    And the entity "Ded SAP ERP" was given the value "active" for "Ded Status"
    And the diagram "Ded Order to Cash" shows the entity "Ded SAP ERP" at 1, 1 with 90 x 30 pt
    When the viewer reads the details of "Ded SAP ERP" in "Ded Order to Cash"
    Then the details answer is 200
    And the details have the name "Ded SAP ERP" and the entity version 1
    And the details show the value "active" for "Ded Status"
    And the details mark "Ded Status" as not changeable

  Scenario: Restricted value
    Given the diagram "Ded Redaction" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Redacted ERP" of the metatype "Ded Component" exists
    And the entity "Ded Redacted ERP" was given the value "5000000" for "Ded Investment"
    And the diagram "Ded Redaction" shows the entity "Ded Redacted ERP" at 1, 1 with 90 x 30 pt
    When the viewer reads the details of "Ded Redacted ERP" in "Ded Redaction"
    Then the details redact "Ded Investment"
    When the enterprise architect reads the details of "Ded Redacted ERP" in "Ded Redaction"
    Then the details show the value "5000000" for "Ded Investment"
    And the details mark "Ded Investment" as changeable

  Scenario: Entity not shown
    Given the diagram "Ded Not Shown" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Hidden ERP" of the metatype "Ded Component" exists
    When the viewer reads the details of "Ded Hidden ERP" in "Ded Not Shown"
    Then the details answer is 404

  # Requirement: Reference targets for the details

  Scenario: Reference targets
    Given the diagram "Ded Targets" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Linux Cluster" of the metatype "Ded Component" exists
    And the canvas entity "Ded Mainframe" of the metatype "Ded Component" exists
    And the canvas entity "Ded Linux Process" of the metatype "Ded Business Process" exists
    When the enterprise architect searches the reference targets of "Ded Hosted on" in "Ded Targets" for "ded lin"
    Then the details answer is 200
    And the reference targets are "Ded Linux Cluster"

  Scenario: Not a reference property
    Given the diagram "Ded Targets Invalid" exists in the viewpoint "Ded Process Model"
    When the enterprise architect searches the reference targets of "Ded Status" in "Ded Targets Invalid" for "ded"
    Then the details answer is 422 naming "propertyDefId"

  # Requirement: Change entities with the diagram

  @AC8
  Scenario: Value completed
    Given the diagram "Ded Invoices" exists in the viewpoint "Ded Process Model"
    And the enterprise architect has placed a new "Ded Business Process" named "Ded Invoice Approval" in "Ded Invoices"
    When the enterprise architect saves "Ded Invoices" with the value of "Ded Process Owner" of "Ded Invoice Approval" set to "Anna"
    Then the details answer is 200
    And the entity "Ded Invoice Approval" has the value "Anna" for "Ded Process Owner"
    And the entity "Ded Invoice Approval" exists with the version 2
    And one "entity.updated" audit event names the entity "Ded Invoice Approval"
    And the view of "Ded Invoices" does not mark the shape of "Ded Invoice Approval" as incomplete

  Scenario: Mandatory value still missing
    Given the diagram "Ded Incomplete" exists in the viewpoint "Ded Process Model"
    And the enterprise architect has placed a new "Ded Business Process" named "Ded Open Approval" in "Ded Incomplete"
    When the enterprise architect saves "Ded Incomplete" with the description of "Ded Open Approval" changed to "A new description"
    Then the details answer is 422 naming "entityUpdates[0].properties"
    And the entity "Ded Open Approval" exists with the version 1
    And the diagram "Ded Incomplete" has the canvas version 2

  Scenario: New entity with values
    Given the diagram "Ded New Values" exists in the viewpoint "Ded Process Model"
    When the enterprise architect places a new "Ded Business Process" named "Ded Filled Approval" with the value "Anna" for "Ded Process Owner" in "Ded New Values" and saves
    Then the details answer is 200
    And the entity "Ded Filled Approval" has the value "Anna" for "Ded Process Owner"
    And the view of "Ded New Values" does not mark the shape of "Ded Filled Approval" as incomplete

  Scenario: Restricted property
    Given the diagram "Ded Restricted" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Restricted ERP" of the metatype "Ded Component" exists
    And the diagram "Ded Restricted" shows the entity "Ded Restricted ERP" at 1, 1 with 90 x 30 pt
    When the enterprise architect saves "Ded Restricted" with the value of "Ded Budget" of "Ded Restricted ERP" set to "100"
    Then the details answer is 403
    And the entity "Ded Restricted ERP" exists with the version 1
    When the enterprise architect saves "Ded Restricted" with the value of "Ded Investment" of "Ded Restricted ERP" set to "100"
    Then the details answer is 200
    And the entity "Ded Restricted ERP" has the value "100" for "Ded Investment"

  Scenario: Entity with an open issue
    Given the diagram "Ded Blocked" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Blocked ERP" of the metatype "Ded Component" exists
    And the diagram "Ded Blocked" shows the entity "Ded Blocked ERP" at 1, 1 with 90 x 30 pt
    And the entity "Ded Blocked ERP" has an open issue of a solution
    When the enterprise architect saves "Ded Blocked" with the description of "Ded Blocked ERP" changed to "Blocked"
    Then the details answer is 409 with the problem "entity-blocked" naming "Ded Blocked ERP"
    And the entity "Ded Blocked ERP" exists with the version 1

  Scenario: Entity not shown
    Given the diagram "Ded Unshown" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Unshown ERP" of the metatype "Ded Component" exists
    When an API call changes "Ded Unshown ERP" without a shape in "Ded Unshown"
    Then the details answer is 422 naming "entityUpdates[0].entityId"
    And the entity "Ded Unshown ERP" exists with the version 1

  # Requirement: Entity version check for interactive edits

  @AC8
  Scenario: Changed meanwhile
    Given the diagram "Ded Conflict" exists in the viewpoint "Ded Process Model"
    And the canvas entity "Ded Conflict ERP" of the metatype "Ded Component" exists
    And the diagram "Ded Conflict" shows the entity "Ded Conflict ERP" at 1, 1 with 90 x 30 pt
    And a batch has changed the description of "Ded Conflict ERP" to "From the batch"
    When the enterprise architect saves "Ded Conflict" with the description of "Ded Conflict ERP" changed to "Mine" read at the version 1
    Then the details answer is 409 with the problem "entity-version-conflict" naming "Ded Conflict ERP" with the current version 2
    And the entity "Ded Conflict ERP" has the description "From the batch"

  Scenario: Batch unchanged
    Given the canvas entity "Ded Batch ERP" of the metatype "Ded Component" exists
    And a batch has changed the description of "Ded Batch ERP" to "First"
    When a batch changes the description of "Ded Batch ERP" to "Second" without a version
    Then the entity "Ded Batch ERP" has the description "Second"
    And the entity "Ded Batch ERP" exists with the version 3
