# Spec: openspec diagram/diagram-canvas (us-074-diagram-canvas)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; UC-10; owner decisions 2026-11-19 and 2026-11-20
# The editor scenarios (palette, "Add existing", moving, resizing, unsaved changes, conflict dialog, reader mode)
# are covered by the frontend specs; this feature covers the API.

@US-074 @UC-10 @diagram
Feature: Diagram canvas

  The view of a diagram shows its shapes in pt in drawing order. Holders of
  entity:write save the layout in one atomic request with the entities created
  on the canvas, holders of entity:read read the view and search the entities
  the viewpoint allows.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the diagram viewpoint "Dgc Process Model" with the notation "bpmn2" exists
    And the diagram viewpoint "Dgc Process Model" allows the metatypes "Application Component" and "Data Object"
    And the diagram viewpoint "Dgc Process Model" stores the size 120 x 40 pt for "Application Component"

  # Requirement: Read the diagram view

  @AC8
  Scenario: Saved layout
    Given the diagram "Dgc Order to Cash" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc SAP ERP" of the metatype "Application Component" exists
    And the diagram "Dgc Order to Cash" shows the entity "Dgc SAP ERP" at 100, 50 with 90 x 30 pt
    When the viewer reads the view of "Dgc Order to Cash"
    Then the canvas answer is 200
    And the view of "Dgc Order to Cash" has a shape of "Dgc SAP ERP" at 100, 50 with 90 x 30 pt
    And the view of "Dgc Order to Cash" offers the default size 120 x 40 pt for "Application Component"

  Scenario: Deleted entity
    Given the diagram "Dgc Hire to Retire" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Legacy CRM" of the metatype "Application Component" exists
    And the diagram "Dgc Hire to Retire" shows the entity "Dgc Legacy CRM" at 1, 1 with 90 x 30 pt
    And the canvas entity "Dgc Legacy CRM" is deleted
    When the viewer reads the view of "Dgc Hire to Retire"
    Then the view of "Dgc Hire to Retire" has no shape of "Dgc Legacy CRM"

  Scenario: Deleted diagram
    Given the diagram "Dgc Old Process" exists in the viewpoint "Dgc Process Model"
    And the diagram "Dgc Old Process" is deleted
    When the viewer reads the view of "Dgc Old Process"
    Then the canvas answer is 404
    When the enterprise architect saves the layout of "Dgc Old Process" with its version
    Then the canvas answer is 404
    When the viewer searches the entities of "Dgc Old Process" for "dgc"
    Then the canvas answer is 404

  # Requirement: Search entities for a diagram

  @AC1
  Scenario: Search by name
    Given the diagram "Dgc Search" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Srch erp" of the metatype "Application Component" exists
    And the canvas entity "Dgc srch BW" of the metatype "Data Object" exists
    And the canvas entity "Dgc Salesforce Srch" of the metatype "Application Component" exists
    And the canvas entity "Dgc srch Process" of the metatype "Technology Component" exists
    When the enterprise architect searches the entities of "Dgc Search" for "dgc srch"
    Then the canvas answer is 200
    And the found entities are "Dgc srch BW" and "Dgc Srch erp"

  Scenario Outline: Filter limit
    Given the diagram "Dgc Filter Limit <length>" exists in the viewpoint "Dgc Process Model"
    When the viewer searches the entities of "Dgc Filter Limit <length>" with a name part of <length> characters
    Then the canvas answer is <status>

    Examples:
      | length | status |
      | 255    | 200    |
      | 256    | 400    |

  Scenario: Metatype the viewpoint does not allow
    Given the diagram "Dgc Search Type" exists in the viewpoint "Dgc Process Model"
    When the viewer searches the entities of "Dgc Search Type" of the metatype "Technology Component"
    Then the canvas answer is 422 naming "metatypeId"

  # Requirement: Save the diagram layout

  @AC8
  Scenario: Layout saved
    Given the diagram "Dgc Layout" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Layout ERP" of the metatype "Application Component" exists
    And the diagram "Dgc Layout" shows the entity "Dgc Layout ERP" at 100, 50 with 90 x 30 pt
    When the enterprise architect moves the shape of "Dgc Layout ERP" in "Dgc Layout" to 300, 80 and resizes it to 120 x 40 pt and saves
    Then the canvas answer is 200
    And the diagram "Dgc Layout" has the canvas version 3
    And the view of "Dgc Layout" has a shape of "Dgc Layout ERP" at 300, 80 with 120 x 40 pt

  Scenario: New entity
    Given the diagram "Dgc New Entity" exists in the viewpoint "Dgc Process Model"
    When the enterprise architect places a new "Application Component" named "Dgc CRM Cloud" in "Dgc New Entity" and saves
    Then the canvas answer is 200
    And the entity "Dgc CRM Cloud" exists with the version 1
    And one "entity.created" audit event names the entity "Dgc CRM Cloud"
    And the view of "Dgc New Entity" has a shape of "Dgc CRM Cloud" at 10, 20 with 90 x 30 pt

  Scenario: Atomic
    Given the diagram "Dgc Atomic" exists in the viewpoint "Dgc Process Model"
    When the enterprise architect saves the new entities "Dgc Atomic Good" of "Application Component" and "Dgc Atomic Bad" of "Technology Component" in "Dgc Atomic"
    Then the canvas answer is 422 naming "newEntities[1]"
    And no canvas entity "Dgc Atomic Good" exists
    And no canvas entity "Dgc Atomic Bad" exists
    And the diagram "Dgc Atomic" has the canvas version 1

  Scenario: Mandatory property
    Given the metatype "Dgc Business Process" has the mandatory property "Dgc Process Owner" without default value
    And the diagram viewpoint "Dgc Process Model" allows the metatypes "Dgc Business Process"
    And the diagram "Dgc Mandatory" exists in the viewpoint "Dgc Process Model"
    When the enterprise architect places a new "Dgc Business Process" named "Dgc Invoice Approval" in "Dgc Mandatory" and saves
    Then the canvas answer is 200
    And the entity "Dgc Invoice Approval" exists with the version 1
    And the view of "Dgc Mandatory" marks the shape of "Dgc Invoice Approval" as incomplete: "Dgc Process Owner"

  Scenario: Stale version
    Given the diagram "Dgc Stale" exists in the viewpoint "Dgc Process Model"
    And the diagram "Dgc Stale" is renamed to "Dgc Stale Renamed" by another user
    When the enterprise architect saves the layout of "Dgc Stale Renamed" with the version 1
    Then the canvas answer is 412
    When the enterprise architect saves the layout of "Dgc Stale Renamed" without a version
    Then the canvas answer is 428
    And the diagram "Dgc Stale Renamed" has the canvas version 2

  Scenario: Without entity:write
    Given the diagram "Dgc Read Only" exists in the viewpoint "Dgc Process Model"
    When the viewer saves the layout of "Dgc Read Only" with its version
    Then the canvas answer is 403
    And the diagram "Dgc Read Only" has the canvas version 1

  # Requirement: Shapes of a diagram

  Scenario: Entity twice
    Given the diagram "Dgc Twice" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Twice ERP" of the metatype "Application Component" exists
    When the enterprise architect places "Dgc Twice ERP" twice in "Dgc Twice" and saves
    Then the canvas answer is 200
    And the view of "Dgc Twice" has 2 shapes of "Dgc Twice ERP"

  Scenario: Type not allowed
    Given the diagram "Dgc Not Allowed" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Not Allowed Node" of the metatype "Technology Component" exists
    When an API call saves a shape of the entity "Dgc Not Allowed Node" in "Dgc Not Allowed"
    Then the canvas answer is 422 naming "shapes[0].entityId"
    And the diagram "Dgc Not Allowed" has the canvas version 1

  Scenario: Unknown or deleted entity
    Given the diagram "Dgc Gone" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Gone ERP" of the metatype "Application Component" exists
    And the canvas entity "Dgc Gone ERP" is deleted
    When an API call saves a shape of the entity "Dgc Gone ERP" in "Dgc Gone"
    Then the canvas answer is 422 naming "shapes[0].entityId"
    When an API call saves a shape of an unknown entity in "Dgc Gone"
    Then the canvas answer is 422 naming "shapes[0].entityId"
    And the diagram "Dgc Gone" has the canvas version 1

  Scenario: Shape of another diagram
    Given the diagram "Dgc Foreign A" exists in the viewpoint "Dgc Process Model"
    And the diagram "Dgc Foreign B" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Foreign ERP" of the metatype "Application Component" exists
    And the diagram "Dgc Foreign A" shows the entity "Dgc Foreign ERP" at 1, 1 with 90 x 30 pt
    When an API call saves the shape ID of "Dgc Foreign A" in "Dgc Foreign B"
    Then the canvas answer is 422 naming "shapes[0].id"
    And the diagram "Dgc Foreign B" has the canvas version 1

  Scenario: Removed from the diagram
    Given the diagram "Dgc Removed" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Removed ERP" of the metatype "Application Component" exists
    And the diagram "Dgc Removed" shows the entity "Dgc Removed ERP" at 1, 1 with 90 x 30 pt
    When the enterprise architect removes the shape of "Dgc Removed ERP" from "Dgc Removed" and saves
    Then the canvas answer is 200
    And the view of "Dgc Removed" has no shape of "Dgc Removed ERP"
    And the entity "Dgc Removed ERP" exists with the version 1

  Scenario Outline: Limits of position and size
    Given the diagram "Dgc Limits <field> <value>" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Limits ERP" of the metatype "Application Component" exists
    When an API call saves a shape of the entity "Dgc Limits ERP" with the <field> "<value>" in "Dgc Limits <field> <value>"
    Then the canvas answer is <status>

    Examples:
      | field  | value      | status |
      | width  | 1          | 200    |
      | width  | 10000      | 200    |
      | width  | 0.99       | 400    |
      | width  | 10000.01   | 400    |
      | height | 1          | 200    |
      | height | 10000      | 200    |
      | height | 0.99       | 400    |
      | height | 10000.01   | 400    |
      | x      | 100000     | 200    |
      | x      | -100000    | 200    |
      | x      | 100000.01  | 400    |
      | x      | -100000.01 | 400    |
      | y      | 100000     | 200    |
      | y      | -100000    | 200    |
      | y      | 100000.01  | 400    |
      | y      | -100000.01 | 400    |
      | x      | 12.34      | 200    |
      | x      | 12.345     | 400    |

  Scenario Outline: Limits of the numbers
    Given the diagram "Dgc Counts <shapes> <entities>" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Counts ERP" of the metatype "Application Component" exists
    When an API call saves <shapes> shapes of "Dgc Counts ERP" and <entities> new entities in "Dgc Counts <shapes> <entities>"
    Then the canvas answer is <status>

    Examples:
      | shapes | entities | status |
      | 2000   | 0        | 200    |
      | 2001   | 0        | 400    |
      | 500    | 500      | 200    |
      | 1      | 501      | 400    |

  Scenario Outline: Limit of the name of a new entity
    Given the diagram "Dgc Name Limit <length>" exists in the viewpoint "Dgc Process Model"
    When an API call saves a new entity with a name of <length> characters in "Dgc Name Limit <length>"
    Then the canvas answer is <status>

    Examples:
      | length | status |
      | 255    | 200    |
      | 256    | 400    |

  # Requirement: Incomplete entities

  Scenario: Completed and other writes stay strict
    Given the metatype "Dgc Business Process" has the mandatory property "Dgc Process Owner" without default value
    And the diagram viewpoint "Dgc Process Model" allows the metatypes "Dgc Business Process"
    And the diagram "Dgc Strict" exists in the viewpoint "Dgc Process Model"
    And the enterprise architect has placed a new "Dgc Business Process" named "Dgc Strict Approval" in "Dgc Strict"
    When a batch changes the description of "Dgc Strict Approval" without a value of "Dgc Process Owner"
    Then the batch item is rejected with "MANDATORY_MISSING"
    When a value of "Dgc Process Owner" is written for "Dgc Strict Approval"
    And the viewer reads the view of "Dgc Strict"
    Then the view of "Dgc Strict" does not mark the shape of "Dgc Strict Approval" as incomplete

  # Requirement: Audit of the layout

  Scenario: Save audited
    Given the diagram "Dgc Audit" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Audit ERP" of the metatype "Application Component" exists
    And the canvas entity "Dgc Audit CRM" of the metatype "Application Component" exists
    And the diagram "Dgc Audit" shows the entity "Dgc Audit CRM" at 1, 1 with 90 x 30 pt
    When the enterprise architect saves the layout of "Dgc Audit" with "Dgc Audit ERP" added twice and "Dgc Audit CRM" removed
    Then the canvas answer is 200
    And "diagram.layout-saved" is audited for "Dgc Audit" with 2 added, 0 changed, 1 removed and 0 created entities

  @AC6
  Scenario: Edges audited
    Given the diagram element metatypes "Dcn Component" and "Dcn Data" exist
    And the diagram viewpoint "Dgc Process Model" allows the metatypes "Dcn Component"
    And the diagram "Dgc Edge Audit" exists in the viewpoint "Dgc Process Model"
    And the canvas entity "Dgc Edge ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dgc Edge CRM" of the metatype "Dcn Component" exists
    And the connection type "Dgc Flow" joins "Dcn Component" and "Dcn Component"
    And the diagram viewpoint "Dgc Process Model" allows the connection types "Dgc Flow"
    And the diagram "Dgc Edge Audit" shows the entity "Dgc Edge ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dgc Edge Audit" shows the entity "Dgc Edge CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dgc Edge Old" of the type "Dgc Flow" runs from "Dgc Edge CRM" to "Dgc Edge ERP"
    And the diagram "Dgc Edge Audit" shows the connection "Dgc Edge Old"
    When the enterprise architect draws a new connection of the type "Dgc Flow" from the shape of "Dgc Edge ERP" to the shape of "Dgc Edge CRM" in "Dgc Edge Audit", removes the edge of "Dgc Edge Old" and saves
    Then the connection answer is 200
    And the layout of "Dgc Edge Audit" is audited with 1 added edges, 1 removed edges and 1 created connections
    And one "entity.created" audit event names the entity "Dgc Edge ERP → Dgc Edge CRM"
