# Derived from: requirements/req/req-044-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-044 – Configure columns of a catalog

  The system MUST enable the catalog manager to define and update the
  `columns` list of a [Catalog](../../business-objects/catalog.md).
  Each `ColumnConfig` entry defines, for an attribute of the
  `primaryEntityType`: `attributeName` (mandatory, must be a valid
  attribute of the type), `displayLabel` (optional; default =
  attributeName), `visible` (boolean, default: true), `sortOrder`
  (integer, 0-based) and `sortable` (boolean, default: true).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Set columns, happy path – catalog "Application Inventory" with primaryEntityType=ApplicationComponent
    Given catalog "Application Inventory" with primaryEntityType=ApplicationComponent
    When  PUT `/api/v1/catalogs/{id}/columns` with `[{attributeName: "name", sortOrder: 0}, {attributeName: "status", sortOrder: 1, displayLabel: "Operating status"}]`
    Then  HTTP 200; catalog has 2 columns; second column has displayLabel "Operating status"

  @AC2
  Scenario: AC2 – Invalid attribute – ApplicationComponent has no attribute `nonExistingField`
    Given ApplicationComponent has no attribute `nonExistingField`
    When  PUT with `attributeName: "nonExistingField"`
    Then  HTTP 422 "The attribute ‹nonExistingField› is not defined for the type ‹ApplicationComponent›"

  @AC3
  Scenario: AC3 – No visible column – PUT with exclusively `visible: false` entries
    Given the precondition according to the requirement is met
    When  PUT with exclusively `visible: false` entries
    Then  HTTP 422 "At least one column must be visible"

  @AC4
  Scenario: AC4 – SavedView cleanup – SavedView "Compact view" contains `columnOrder: ["name", "vendor"]`; column "vendor" is removed from columns
    Given SavedView "Compact view" contains `columnOrder: ["name", "vendor"]`; column "vendor" is removed from columns
    When  PUT without `vendor` column
    Then  HTTP 200; response contains `warnings: [{type: "savedViewColumnRemoved", savedViewId: "...", removedColumns: ["vendor"]}]`; SavedView `columnOrder` is cleaned up to `["name"]`

  @AC5
  Scenario: AC5 – Order – columns with sortOrder [2, 0, 1] are sent
    Given the precondition according to the requirement is met
    When  columns with sortOrder [2, 0, 1] are sent
    Then  system orders columns by sortOrder; first displayed column is the one with sortOrder=0
