# Derived from: requirements/req/req-065-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-06 @UC-08
Feature: REQ-065 – Catalog join for connection-type primaries and n-Connection traversal

  The system MUST support two extensions of the join logic
  (REQ-045/046) for [Catalog](../../business-objects/catalog.md)
  instances: 1. **Connection type as primary**: `primaryEntityType`
  MAY be set to an EntityType with `isConnection=true` (e.g.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Connection type as primary – MetamodelConfiguration with EntityType `data-flow` (isConnection=true)
    Given MetamodelConfiguration with EntityType `data-flow` (isConnection=true)
    When  catalog manager creates catalog with `primaryEntityType=data-flow`
    Then  HTTP 201; catalog is valid; query returns all data-flow instances as rows

  @AC2
  Scenario: AC2 – n-Connection join outbound – DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, target...
    Given DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, targetEntityId=42)
    And   catalog `Data Flow Overview` (primaryEntityType=data-flow) with JoinDefinition: `{ connectionType: carries-data, joinDirection: outbound, targetEntityType: data-object, targetColumns: [{attributeName: "name"}] }`
    When  catalog query
    Then  row for DataFlow id=5; joinResults contains DataObject id=42 with name "Customer master"

  @AC3
  Scenario: AC3 – Non-n-Connection remains untouched – regular catalog with primaryEnti...
    Given regular catalog with primaryEntityType=application-component and join data-flow outbound
    When  catalog query
    Then  result identical to REQ-046 behavior; no regression error

  @AC4
  Scenario: AC4 – Multiple n-Connections aggregated – DataFlow id=5 has 2 carries-data connections (data-objects 42 and 43)
    Given DataFlow id=5 has 2 carries-data connections (data-objects 42 and 43)
    When  catalog query, aggregate mode
    Then  one row for DataFlow id=5; joinResults contains array with both data-objects

  @AC5
  Scenario: AC5 – Connection primary: source and target entity available as columns – c...
    Given the precondition according to the requirement is met
    When  catalog with primaryEntityType=data-flow; columns `sourceEntityName`, `targetEntityName` configured
    Then  columns show the names of the source and target entity of each data flow
