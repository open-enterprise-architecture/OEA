# Spec: openspec lineage/lineage-query, lineage/lineage-model, metamodel/starter-packages, repository/connection-write
# (us-065-lineage-model-and-query)
# Source material: requirements/user-stories/US-063 to US-065 and US-068 (AC1, AC2, empty result of AC4),
# REQ-060 to REQ-062, REQ-064, REQ-081 AC6; UC-08 AC3 to AC6
# The scenarios share one instance of their own (the imports change its metamodel). Every scenario builds its own
# systems, so that the scenarios do not depend on each other.

@US-065 @US-064 @US-068 @REQ-062 @UC-08 @lineage
Feature: Data lineage over data flows

  A data flow connects two systems and carries data objects through "Carries Data" connections. The lineage query
  follows the flows from a data object or from a system and returns the systems, the flows and the paths.

  Background:
    Given an instance of its own with the TOGAF and the BPMN package

  @US-065 @AC1 @REQ-062 @UC-08 @AC3
  Scenario: Downstream end to end
    Given the data object "Customer Master" and the systems "SAP ERP", "DWH" and "BI Tool"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    And a data flow from "DWH" to "BI Tool" carrying "Customer Master"
    When the viewer queries the lineage of "Customer Master" downstream
    Then the lineage nodes are
      | name    | depth | roles        |
      | SAP ERP | 0     | origin       |
      | DWH     | 1     | intermediate |
      | BI Tool | 2     | sink         |
    And the lineage path is "SAP ERP > DWH > BI Tool"
    And the lineage has no cycle

  @US-065 @AC2 @REQ-062 @UC-08 @AC4
  Scenario: Upstream
    Given the data object "Customer Master" and the systems "SAP ERP", "DWH" and "BI Tool"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    And a data flow from "DWH" to "BI Tool" carrying "Customer Master"
    When the viewer queries the lineage of "Customer Master" upstream
    Then the lineage nodes are
      | name    | depth | roles        |
      | BI Tool | 0     | sink         |
      | DWH     | 1     | intermediate |
      | SAP ERP | 2     | origin       |
    And the lineage path is "BI Tool > DWH > SAP ERP"

  @US-065 @AC3
  Scenario: Impact
    Given the data object "Customer Master" and the systems "SAP ERP", "DWH" and "BI Tool"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    And a data flow from "DWH" to "BI Tool" carrying "Customer Master"
    When the viewer queries the lineage of "Customer Master" with impact
    Then the lineage has 3 nodes, all with impact

  @US-065 @AC4 @REQ-062 @AC3 @UC-08 @AC5
  Scenario: Cycle
    Given the data object "Customer Master" and the systems "A", "B" and "C"
    And a data flow from "A" to "B" carrying "Customer Master"
    And a data flow from "B" to "C" carrying "Customer Master"
    And a data flow from "C" to "A" carrying "Customer Master"
    When the viewer queries the lineage of "Customer Master" downstream
    Then the lineage has a cycle
    And the lineage has 3 edges

  @US-065 @AC5 @REQ-062 @AC5
  Scenario: Data object that no flow carries
    Given the data object "Lonely"
    When the viewer queries the lineage of "Lonely" downstream
    Then the lineage is empty

  @REQ-062 @AC4
  Scenario: Metatype filter
    Given the data object "Customer Master" and the systems "SAP ERP", "DWH" and "BI Tool"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    And a data flow from "DWH" to "BI Tool" carrying "Customer Master"
    When the viewer queries the lineage of "Customer Master" downstream for the metatype "Application Component"
    Then the lineage has the nodes "SAP ERP" and "DWH" and 2 edges

  @REQ-081 @AC6
  Scenario: Tasks that read and enrich a data object
    Given the data object "Order" and the task "Review order"
    And "Review order" reads "Order" and enriches it
    When the viewer queries the lineage of "Order" with both directions
    Then the lineage has the task "Review order" as reader and writer
    And the output edge has the transformation "enrich"

  @US-064 @AC1
  Scenario: Create carries-data
    Given the data object "Customer Master" and the systems "SAP ERP" and "DWH"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    Then the carries-data connection has its own ID, the data flow as source and "Customer Master" as target

  @US-064 @AC2
  Scenario: Carries-data from a system
    Given the data object "Customer Master" and the systems "SAP ERP"
    When the architect connects "SAP ERP" to "Customer Master" by "Carries Data"
    Then the connection write is rejected with 422 "SOURCE_NOT_ALLOWED"

  @US-064 @AC3
  Scenario: Carries-data to a data flow
    Given the systems "SAP ERP" and "DWH"
    And a data flow from "SAP ERP" to "DWH" carrying nothing
    And a data flow from "DWH" to "SAP ERP" carrying nothing
    When the architect connects the first data flow to the second by "Carries Data"
    Then the connection write is rejected with 422 "TARGET_NOT_ALLOWED"

  @US-064 @AC4
  Scenario: Connection depth
    Given the data object "Customer Master" and the systems "SAP ERP" and "DWH"
    And a data flow from "SAP ERP" to "DWH" carrying "Customer Master"
    When the architect connects the last carries-data connection to "SAP ERP" by "Realized By"
    Then the connection write is rejected with 422 "CONNECTION_DEPTH_EXCEEDED"

  @US-068 @AC4
  Scenario: No flow with personal data
    Given the systems "SAP ERP" and "DWH"
    And a data flow from "SAP ERP" to "DWH" with the protocol "JDBC" and no personal data categories
    When the viewer filters a catalog of data flows by personal data categories that are not empty
    Then the catalog shows 0 data flows

  @US-068 @AC1 @AC2 @UC-08 @AC6
  Scenario: Data flows with personal data
    Given the systems "SAP ERP", "DWH" and "BI Tool"
    And a data flow from "SAP ERP" to "DWH" with the protocol "JDBC" and the personal data categories "Name, Address"
    And a data flow from "DWH" to "BI Tool" with the protocol "REST" and the personal data categories "Name, Address"
    And a data flow from "BI Tool" to "SAP ERP" with the protocol "JDBC" and no personal data categories
    When the viewer filters a catalog of data flows by personal data categories that are not empty and the protocol "JDBC"
    Then the catalog shows 1 data flow
