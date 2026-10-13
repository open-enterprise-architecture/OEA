# Derived from: requirements/req/req-054-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-054 – Configure and validate CatalogQuery DataSource

  The system MUST validate a `CatalogQueryDataSource` configuration
  for `table` widgets and delegate to the catalog query API (REQ-046)
  during data computation (REQ-055). The DataSource MUST require
  `catalogId` as a mandatory field and MUST check at write time
  whether the referenced
  [catalog](../../business-objects/catalog.md) is visible to the
  requesting user.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Valid DataSource – catalog "Application Inventory" (scope=instance) exists; Kurt is logged in
    Given catalog "Application Inventory" (scope=instance) exists; Kurt is logged in
    When  create widget with type=table, catalogId=<id>
    Then  HTTP 201; DataSource saved

  @AC2
  Scenario: AC2 – Invalid catalogId – create widget with non-existent catalogId
    Given the precondition according to the requirement is met
    When  create widget with non-existent catalogId
    Then  HTTP 422 "Catalog not found or not visible"

  @AC3
  Scenario: AC3 – Another user's personal catalog – Franz has a scope=personal catalog
    Given Franz has a scope=personal catalog
    When  Kurt tries to use this catalog as a DataSource
    Then  HTTP 403 "Catalog not visible"

  @AC4
  Scenario: AC4 – savedViewId valid – catalog has SavedView "Compact view"
    Given catalog has SavedView "Compact view"
    When  create widget with savedViewId=<view-id>
    Then  HTTP 201; on data retrieval, the columns and filters of the SavedView are applied

  @AC5
  Scenario: AC5 – Catalog deleted later – widget with CatalogQueryDataSource exists; catalog is deleted afterwards
    Given widget with CatalogQueryDataSource exists; catalog is deleted afterwards
    When  GET /api/v1/dashboards/{id}/data
    Then  this widget returns `{ "status": "error", "error": "Catalog not found" }`; other widgets render normally
