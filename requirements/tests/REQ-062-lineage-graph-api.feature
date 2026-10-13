# Derived from: requirements/req/req-062-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-08
Feature: REQ-062 – Lineage graph API (traversable paths, upstream/downstream/impact)

  The system MUST provide a dedicated lineage endpoint (`GET
  /api/v1/lineage`) that, for a given ArchitectureEntity ID, computes
  all upstream and/or downstream connected entities by traversing the
  ArchitectureEntity graph via `carries-data` connections and adjacent
  `data-flow` connections; the traversal MUST detect cycles (max. 50
  hops) and return a complete path as a structured JSON graph.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Downstream end-to-end – SAP ERP(1) →[data-flow:5, carries-data:103→D...
    Given SAP ERP(1) →[data-flow:5, carries-data:103→DataObject:42]→ DWH(2) →[data-flow:6, carries-data:104→DataObject:42]→ BI Tool(14)
    When  `GET /api/v1/lineage?entityId=42&direction=downstream`
    Then  `nodes` contains id=1 (SAP ERP), id=2 (DWH), id=14 (BI Tool); `path=[1,2,14]`; `cycleDetected=false`

  @AC2
  Scenario: AC2 – Upstream – same configuration as AC1
    Given same configuration as AC1
    When  `GET /api/v1/lineage?entityId=42&direction=upstream`
    Then  `nodes` contains id=1 (SAP ERP); DWH only as intermediate node; path backwards

  @AC3
  Scenario: AC3 – Cycle detection – cycle A→B→C→A with DataObject 42 in all flows
    Given cycle A→B→C→A with DataObject 42 in all flows
    When  `GET /api/v1/lineage?entityId=42&direction=downstream`
    Then  `cycleDetected=true`; response contains the traversed subgraph; no timeout/endless loop

  @AC4
  Scenario: AC4 – EntityType filter – `entityTypeFilter=application-component`
    Given the precondition according to the requirement is met
    When  `entityTypeFilter=application-component`
    Then  response contains only nodes with `entityTypeId=application-component`; DataFlows appear only in `edges`

  @AC5
  Scenario: AC5 – Empty result – DataObject id=42 is not referenced by any DataFlow via carries-data
    Given DataObject id=42 is not referenced by any DataFlow via carries-data
    When  lineage query for id=42
    Then  `nodes=[]`, `edges=[]`, HTTP 200; no error
