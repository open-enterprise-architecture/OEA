# Derived from: requirements/req/req-045-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-045 – Add join definition to a catalog

  The system MUST enable the catalog manager to add, edit, and remove
  `JoinDefinition` entries on a
  [Catalog](../../business-objects/catalog.md). A `JoinDefinition`
  consists of: `name` (mandatory), `connectionType` (mandatory;
  EntityType with `isConnection=true` – BR-02), `joinDirection`
  (mandatory: `outbound` | `inbound` | `both`), `targetEntityType`
  (mandatory; must be reachable according to
  `allowedSourceTypes`/`allowedTargetTypes` of the ConnectionType –
  BR-03), `targetColumns` (ColumnConfig[]; attributes of the target
  entity), `defaultJoinMode` (optional; `rows` | `aggregate`; default
  = Catalog.defaultJoinMode) and `aggregateLabel` (optional; label of
  the aggregate cell).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Add join, happy path – catalog "Application Inventory" with primaryEn...
    Given catalog "Application Inventory" with primaryEntityType=ApplicationComponent; MetamodelConfiguration has ConnectionType `DataFlow` (isConnection=true, allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
    When  `POST /api/v1/catalogs/{id}/joins` with `{ name: "Interfaces", connectionType: "DataFlow", joinDirection: "outbound", targetEntityType: "Interface", targetColumns: [{attributeName: "name", sortOrder: 0}], defaultJoinMode: "aggregate" }`
    Then  HTTP 201; catalog contains new JoinDefinition with generated id

  @AC2
  Scenario: AC2 – BR-02 – connectionType is not a connection type – `ApplicationComponent` has `isConnection=false`
    Given `ApplicationComponent` has `isConnection=false`
    When  `connectionType: "ApplicationComponent"` in the request
    Then  HTTP 422 "The type ‹ApplicationComponent› is not a connection type (isConnection=false)"

  @AC3
  Scenario: AC3 – BR-03 – targetEntityType not reachable – DataFlow has `allowedTargetTypes=[Interface]`
    Given DataFlow has `allowedTargetTypes=[Interface]`
    When  `targetEntityType: "TechnologyComponent"` with `joinDirection=outbound`
    Then  HTTP 422 "The EntityType ‹TechnologyComponent› is not reachable via ‹DataFlow› in direction outbound"

  @AC4
  Scenario: AC4 – Remove join – `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
    Given the precondition according to the requirement is met
    When  `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
    Then  HTTP 200; JoinDefinition removed; existing SavedViews are cleaned up of JoinModeOverrides for this join

  @AC5
  Scenario: AC5 – targetColumns validation – Interface has no attribute `nonExistingProp`
    Given Interface has no attribute `nonExistingProp`
    When  targetColumns contains `attributeName: "nonExistingProp"`
    Then  HTTP 422 "The attribute ‹nonExistingProp› is not defined for the type ‹Interface›"
