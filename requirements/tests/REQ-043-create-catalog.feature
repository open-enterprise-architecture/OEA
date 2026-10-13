# Derived from: requirements/req/req-043-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-043 – Create catalog

  The system MUST enable the catalog manager to create a new
  [Catalog](../../business-objects/catalog.md) with the following
  fields: `name` (mandatory, unique within the instance),
  `description` (optional), `primaryEntityType` (mandatory, must be a
  valid EntityType name from the MetamodelConfiguration), `scope`
  (mandatory: `instance` | `personal`) and `defaultJoinMode`
  (mandatory: `rows` | `aggregate`, default: `aggregate`). The catalog
  is initially created without columns, joins, filters, or views;
  column and join configuration take place in separate steps (REQ-044,
  REQ-045).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Creation, happy path – Kurt is logged in with role "Enterprise Archi...
    Given Kurt is logged in with role "Enterprise Architect"; the MetamodelConfiguration contains the EntityType `ApplicationComponent`
    When  `POST /api/v1/catalogs` with `{ name: "Application Inventory", primaryEntityType: "ApplicationComponent", scope: "instance", defaultJoinMode: "aggregate" }`
    Then  HTTP 201; response contains id, name, primaryEntityType, scope, defaultJoinMode, createdBy=Kurt's ID, empty columns/joinDefinitions/savedFilters/savedViews lists

  @AC2
  Scenario: AC2 – Name collision – a catalog with name="Application Inventory" already exists
    Given a catalog with name="Application Inventory" already exists
    When  repeated POST with the same name (whether identical spelling or variations such as leading/trailing spaces)
    Then  HTTP 422 with error message "A catalog with this name already exists"

  @AC3
  Scenario: AC3 – Invalid EntityType – the MetamodelConfiguration contains no EntityType "UnknownType"
    Given the MetamodelConfiguration contains no EntityType "UnknownType"
    When  POST with `primaryEntityType: "UnknownType"`
    Then  HTTP 422 "The EntityType ‹UnknownType› is not defined in the MetamodelConfiguration"

  @AC4
  Scenario: AC4 – Scope=personal – Sabine creates a catalog with scope=personal
    Given Sabine creates a catalog with scope=personal
    When  Franz (another user) retrieves the catalog overview
    Then  Sabine's personal catalog does NOT appear in Franz's view; 404 on direct access

  @AC5
  Scenario: AC5 – Missing permission – Franz has only the role "Viewer"
    Given Franz has only the role "Viewer"
    When  Franz tries to create a catalog
    Then  HTTP 403 Forbidden
