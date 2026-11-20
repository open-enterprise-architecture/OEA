# Spec: openspec diagram/diagram-connections (us-074-diagram-connections)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md AC6; UC-10 step 7; owner decisions 2026-11-19
# The editor scenarios (no allowed type, the offer, the connected element, edge selected) are covered by the
# frontend specs; this feature covers the API. The limits with thousands of items are covered by
# DiagramConnectionTests.

@US-074 @UC-10 @diagram
Feature: Diagram connections

  The view of a diagram shows its edges: connections drawn between shapes, with bend points. Holders of
  entity:write draw new connections, which are created with the save, and show existing ones; holders of
  entity:read read the edges and ask which connections to offer when an entity is placed.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the diagram element metatypes "Dcn Component" and "Dcn Data" exist
    And the diagram viewpoint "Dcn Process Model" with the notation "bpmn2" exists
    And the diagram viewpoint "Dcn Process Model" allows the metatypes "Dcn Component" and "Dcn Data"
    And the connection type "Dcn Flow" joins "Dcn Component" and "Dcn Component"
    And the connection type "Dcn Foreign Flow" joins "Dcn Component" and "Dcn Component"
    And the connection type "Dcn Narrow Flow" joins "Dcn Component" and "Dcn Component"
    And the diagram viewpoint "Dcn Process Model" allows the connection types "Dcn Flow" and "Dcn Narrow Flow"

  # Requirement: Edges of a diagram

  @AC6
  Scenario: Saved edge
    Given the diagram "Dcn Order to Cash" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn SAP ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn CRM Cloud" of the metatype "Dcn Component" exists
    And the diagram "Dcn Order to Cash" shows the entity "Dcn SAP ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Order to Cash" shows the entity "Dcn CRM Cloud" at 300, 1 with 90 x 30 pt
    And the connection "Dcn SAP ERP to CRM" of the type "Dcn Flow" runs from "Dcn SAP ERP" to "Dcn CRM Cloud"
    And the diagram "Dcn Order to Cash" shows the connection "Dcn SAP ERP to CRM"
    When the viewer reads the view of "Dcn Order to Cash"
    Then the view of "Dcn Order to Cash" has an edge of "Dcn SAP ERP to CRM" from the shape of "Dcn SAP ERP" to the shape of "Dcn CRM Cloud"

  Scenario: Connection twice
    Given the diagram "Dcn Twice" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Twice ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Twice CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Twice" shows the entity "Dcn Twice ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Twice" shows the entity "Dcn Twice ERP" at 1, 100 with 90 x 30 pt
    And the diagram "Dcn Twice" shows the entity "Dcn Twice CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Twice ERP to CRM" of the type "Dcn Flow" runs from "Dcn Twice ERP" to "Dcn Twice CRM"
    When the enterprise architect draws an edge of "Dcn Twice ERP to CRM" from shape 1 of "Dcn Twice ERP" to shape 1 of "Dcn Twice CRM" in "Dcn Twice" and saves
    And the enterprise architect draws an edge of "Dcn Twice ERP to CRM" from shape 2 of "Dcn Twice ERP" to shape 1 of "Dcn Twice CRM" in "Dcn Twice" and saves
    Then the connection answer is 200
    And the view of "Dcn Twice" has 2 edges of "Dcn Twice ERP to CRM"

  Scenario: Wrong shapes
    Given the diagram "Dcn Wrong Shapes" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Wrong ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Wrong CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Wrong Shapes" shows the entity "Dcn Wrong ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Wrong Shapes" shows the entity "Dcn Wrong CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Wrong ERP to CRM" of the type "Dcn Flow" runs from "Dcn Wrong ERP" to "Dcn Wrong CRM"
    When an API call saves an edge of "Dcn Wrong ERP to CRM" from the shape of "Dcn Wrong CRM" to the shape of "Dcn Wrong ERP" in "Dcn Wrong Shapes"
    Then the connection answer is 422 naming "edges[0].sourceShapeId"
    And the view of "Dcn Wrong Shapes" has no edge of "Dcn Wrong ERP to CRM"
    When an API call saves an edge of "Dcn Wrong ERP to CRM" between unknown shapes in "Dcn Wrong Shapes"
    Then the connection answer is 422 naming "edges[0].sourceShapeId"

  Scenario: Type not allowed
    Given the diagram "Dcn Type Not Allowed" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Type ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Type CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Type Not Allowed" shows the entity "Dcn Type ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Type Not Allowed" shows the entity "Dcn Type CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Type Foreign" of the type "Dcn Foreign Flow" runs from "Dcn Type ERP" to "Dcn Type CRM"
    When an API call saves an edge of "Dcn Type Foreign" from the shape of "Dcn Type ERP" to the shape of "Dcn Type CRM" in "Dcn Type Not Allowed"
    Then the connection answer is 422 naming "edges[0].connectionId"
    And the view of "Dcn Type Not Allowed" has no edge of "Dcn Type Foreign"

  Scenario: Deleted connection
    Given the diagram "Dcn Deleted Connection" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Del ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Del CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Deleted Connection" shows the entity "Dcn Del ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Deleted Connection" shows the entity "Dcn Del CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Del ERP to CRM" of the type "Dcn Flow" runs from "Dcn Del ERP" to "Dcn Del CRM"
    And the diagram "Dcn Deleted Connection" shows the connection "Dcn Del ERP to CRM"
    When the connection "Dcn Del ERP to CRM" is deleted
    And the viewer reads the view of "Dcn Deleted Connection"
    Then the view of "Dcn Deleted Connection" has no edge of "Dcn Del ERP to CRM"
    When the enterprise architect saves the layout of "Dcn Deleted Connection" keeping its edges
    And the connection "Dcn Del ERP to CRM" is restored
    And the viewer reads the view of "Dcn Deleted Connection"
    Then the view of "Dcn Deleted Connection" has 1 edges of "Dcn Del ERP to CRM"

  Scenario: Edge of another diagram
    Given the diagram "Dcn Edge Owner" exists in the viewpoint "Dcn Process Model"
    And the diagram "Dcn Edge Thief" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Own ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Own CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Edge Owner" shows the entity "Dcn Own ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Edge Owner" shows the entity "Dcn Own CRM" at 300, 1 with 90 x 30 pt
    And the diagram "Dcn Edge Thief" shows the entity "Dcn Own ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Edge Thief" shows the entity "Dcn Own CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Own ERP to CRM" of the type "Dcn Flow" runs from "Dcn Own ERP" to "Dcn Own CRM"
    And the diagram "Dcn Edge Owner" shows the connection "Dcn Own ERP to CRM"
    When an API call saves an edge in "Dcn Edge Thief" with the ID of an edge of "Dcn Edge Owner"
    Then the connection answer is 422 naming "edges[0].id"
    When an API call saves two edges of "Dcn Own ERP to CRM" with the same ID in "Dcn Edge Thief"
    Then the connection answer is 422 naming "edges[1].id"

  # Requirement: Draw a new connection

  @AC6 @AC7
  Scenario: Connection drawn
    Given the diagram "Dcn Drawn" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Drawn ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Drawn CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Drawn" shows the entity "Dcn Drawn ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Drawn" shows the entity "Dcn Drawn CRM" at 300, 1 with 90 x 30 pt
    When the enterprise architect draws a new connection of the type "Dcn Flow" from the shape of "Dcn Drawn ERP" to the shape of "Dcn Drawn CRM" in "Dcn Drawn" and saves
    Then the connection answer is 200
    And the connection "Dcn Drawn ERP → Dcn Drawn CRM" exists with the type "Dcn Flow" from "Dcn Drawn ERP" to "Dcn Drawn CRM"
    And the creation of the connection "Dcn Drawn ERP → Dcn Drawn CRM" is audited
    And the view of "Dcn Drawn" has an edge of "Dcn Drawn ERP → Dcn Drawn CRM" from the shape of "Dcn Drawn ERP" to the shape of "Dcn Drawn CRM"
    And the layout of "Dcn Drawn" is audited with 1 added edges, 0 removed edges and 1 created connections

  Scenario: Endpoint not allowed
    Given the diagram "Dcn Endpoint" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Endpoint ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Endpoint Data" of the metatype "Dcn Data" exists
    And the diagram "Dcn Endpoint" shows the entity "Dcn Endpoint ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Endpoint" shows the entity "Dcn Endpoint Data" at 300, 1 with 90 x 30 pt
    When an API call saves a new connection of the type "Dcn Narrow Flow" from the shape of "Dcn Endpoint ERP" to the shape of "Dcn Endpoint Data" in "Dcn Endpoint"
    Then the connection answer is 422 naming "newConnections[0]"
    And the connection answer names the code "TARGET_NOT_ALLOWED"
    And no connection "Dcn Endpoint ERP → Dcn Endpoint Data" exists

  Scenario: Between new entities
    Given the diagram "Dcn Between New" exists in the viewpoint "Dcn Process Model"
    When the enterprise architect places two new "Dcn Component" entities "Dcn New Billing" and "Dcn New Dunning" connected with "Dcn Flow" in "Dcn Between New" and saves
    Then the connection answer is 200
    And the connection "Dcn New Billing → Dcn New Dunning" exists with the type "Dcn Flow" from "Dcn New Billing" to "Dcn New Dunning"
    And the view of "Dcn Between New" has an edge of "Dcn New Billing → Dcn New Dunning" from the shape of "Dcn New Billing" to the shape of "Dcn New Dunning"

  Scenario: New connection without an edge
    Given the diagram "Dcn Unused" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Unused ERP" of the metatype "Dcn Component" exists
    And the diagram "Dcn Unused" shows the entity "Dcn Unused ERP" at 1, 1 with 90 x 30 pt
    When an API call saves a new connection of the type "Dcn Flow" without an edge in "Dcn Unused"
    Then the connection answer is 422 naming "newConnections[0]"
    And the connection answer names the code "NEW_CONNECTION_UNUSED"

  Scenario: Scoping connection type
    Given the diagram "Dcn Scoping" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Scoping ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Scoping CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Scoping" shows the entity "Dcn Scoping ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Scoping" shows the entity "Dcn Scoping CRM" at 300, 1 with 90 x 30 pt
    And the connection type "Dcn Owned by" joins "Dcn Component" and "Dcn Component"
    And the diagram viewpoint "Dcn Process Model" allows the connection types "Dcn Owned by"
    And the connection type "Dcn Owned by" is the scoping type of the property "Dcn Classification"
    When an API call saves a new connection of the type "Dcn Owned by" from the shape of "Dcn Scoping ERP" to the shape of "Dcn Scoping CRM" in "Dcn Scoping"
    Then the connection answer is 403
    And no connection "Dcn Scoping ERP → Dcn Scoping CRM" exists

  Scenario: Created incomplete
    Given the diagram "Dcn Incomplete" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Incomplete ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Incomplete CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Incomplete" shows the entity "Dcn Incomplete ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Incomplete" shows the entity "Dcn Incomplete CRM" at 300, 1 with 90 x 30 pt
    And the connection type "Dcn Incomplete Flow" joins "Dcn Component" and "Dcn Component"
    And the diagram viewpoint "Dcn Process Model" allows the connection types "Dcn Incomplete Flow"
    And the metatype "Dcn Incomplete Flow" has the mandatory property "Dcn Protocol" without default value
    When the enterprise architect draws a new connection of the type "Dcn Incomplete Flow" from the shape of "Dcn Incomplete ERP" to the shape of "Dcn Incomplete CRM" in "Dcn Incomplete" and saves
    Then the connection answer is 200
    And the view of "Dcn Incomplete" marks the edge of "Dcn Incomplete ERP → Dcn Incomplete CRM" as incomplete: "Dcn Protocol"

  # Requirement: Show existing connections on request

  Scenario: Offered when the second end is placed
    Given the diagram "Dcn Offer" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Offer ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Offer CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Offer" shows the entity "Dcn Offer ERP" at 1, 1 with 90 x 30 pt
    And the connection "Dcn Offer ERP to CRM" of the type "Dcn Flow" runs from "Dcn Offer ERP" to "Dcn Offer CRM"
    And the connection "Dcn Offer Foreign" of the type "Dcn Foreign Flow" runs from "Dcn Offer ERP" to "Dcn Offer CRM"
    When the viewer asks "Dcn Offer" for the connections of "Dcn Offer CRM" among "Dcn Offer ERP"
    Then the connection answer is 200
    And the offered connections are "Dcn Offer ERP to CRM"

  Scenario: Nothing to offer
    Given the diagram "Dcn Nothing" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Nothing ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Nothing CRM" of the metatype "Dcn Component" exists
    When the viewer asks "Dcn Nothing" for the connections of "Dcn Nothing CRM" among "Dcn Nothing ERP"
    Then the connection answer is 200
    And no connection is offered

  # Requirement: Remove an edge and limits of edges

  Scenario: Edge removed
    Given the diagram "Dcn Edge Removed" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Rem ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Rem CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Edge Removed" shows the entity "Dcn Rem ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Edge Removed" shows the entity "Dcn Rem CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Rem ERP to CRM" of the type "Dcn Flow" runs from "Dcn Rem ERP" to "Dcn Rem CRM"
    And the diagram "Dcn Edge Removed" shows the connection "Dcn Rem ERP to CRM"
    When the enterprise architect removes the edges from "Dcn Edge Removed" and saves
    Then the connection answer is 200
    And the view of "Dcn Edge Removed" has no edge of "Dcn Rem ERP to CRM"
    And the connection "Dcn Rem ERP to CRM" exists with the type "Dcn Flow" from "Dcn Rem ERP" to "Dcn Rem CRM"

  Scenario: Shape removed with its edges
    Given the diagram "Dcn Shape Removed" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Shp ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Shp CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Shape Removed" shows the entity "Dcn Shp ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Shape Removed" shows the entity "Dcn Shp CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Shp ERP to CRM" of the type "Dcn Flow" runs from "Dcn Shp ERP" to "Dcn Shp CRM"
    And the diagram "Dcn Shape Removed" shows the connection "Dcn Shp ERP to CRM"
    When the enterprise architect removes the shape of "Dcn Shp CRM" from "Dcn Shape Removed" and its edges and saves
    Then the connection answer is 200
    And the view of "Dcn Shape Removed" has no edge of "Dcn Shp ERP to CRM"
    And the connection "Dcn Shp ERP to CRM" exists with the type "Dcn Flow" from "Dcn Shp ERP" to "Dcn Shp CRM"

  # Details and changes of a connection shown by an edge (MODIFIED requirements of diagram-entity-details)

  Scenario: Connection shown by an edge
    Given the diagram "Dcn Details" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Det ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Det CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Details" shows the entity "Dcn Det ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Details" shows the entity "Dcn Det CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Det ERP to CRM" of the type "Dcn Flow" runs from "Dcn Det ERP" to "Dcn Det CRM"
    And the connection "Dcn Det Unshown" of the type "Dcn Flow" runs from "Dcn Det ERP" to "Dcn Det CRM"
    And the diagram "Dcn Details" shows the connection "Dcn Det ERP to CRM"
    When the viewer reads the details of "Dcn Det ERP to CRM" in "Dcn Details"
    Then the details answer is 200
    And the details have the name "Dcn Det ERP to CRM" and the entity version 1
    When the viewer reads the details of "Dcn Det Unshown" in "Dcn Details"
    Then the details answer is 404

  Scenario: Connection changed
    Given the diagram "Dcn Changed" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Chg ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Chg CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Changed" shows the entity "Dcn Chg ERP" at 1, 1 with 90 x 30 pt
    And the diagram "Dcn Changed" shows the entity "Dcn Chg CRM" at 300, 1 with 90 x 30 pt
    And the connection "Dcn Chg ERP to CRM" of the type "Dcn Flow" runs from "Dcn Chg ERP" to "Dcn Chg CRM"
    And the diagram "Dcn Changed" shows the connection "Dcn Chg ERP to CRM"
    When the enterprise architect saves "Dcn Changed" with the description of the connection "Dcn Chg ERP to CRM" changed to "Orders to the CRM"
    Then the connection answer is 200
    And the connection "Dcn Chg ERP to CRM" has the description "Orders to the CRM" and the version 2

  Scenario: Connection without an edge cannot be changed
    Given the diagram "Dcn Unchanged" exists in the viewpoint "Dcn Process Model"
    And the canvas entity "Dcn Unc ERP" of the metatype "Dcn Component" exists
    And the canvas entity "Dcn Unc CRM" of the metatype "Dcn Component" exists
    And the diagram "Dcn Unchanged" shows the entity "Dcn Unc ERP" at 1, 1 with 90 x 30 pt
    And the connection "Dcn Unc ERP to CRM" of the type "Dcn Flow" runs from "Dcn Unc ERP" to "Dcn Unc CRM"
    When the enterprise architect saves "Dcn Unchanged" with the description of the connection "Dcn Unc ERP to CRM" changed to "x"
    Then the connection answer is 422 naming "entityUpdates[0].entityId"
    And the connection "Dcn Unc ERP to CRM" has the description "" and the version 1
