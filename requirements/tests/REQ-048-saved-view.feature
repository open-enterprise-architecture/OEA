# Derived from: requirements/req/req-048-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-06
Feature: REQ-048 – Create SavedView and apply it on open

  The system MUST enable the catalog manager to create named
  `SavedView` entries within a
  [Catalog](../../business-objects/catalog.md). A SavedView bundles:
  `columnOrder` (ordered list of visible attribute names, BR-05),
  `activeFilterIds` (IDs of SavedFilters of this catalog) and
  `joinModeOverrides` (optional mode overrides per JoinDefinition).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create SavedView, happy path – catalog has columns [name, status, ven...
    Given catalog has columns [name, status, vendor] and SavedFilter "Active systems only"
    When  `POST /api/v1/catalogs/{id}/views` with `{ name: "Compact view", columnOrder: ["name", "status"], activeFilterIds: ["filter-1"], isDefault: true }`
    Then  HTTP 201; SavedView saved; isDefault=true

  @AC2
  Scenario: AC2 – BR-06 – Default switch – SavedView A with isDefault=true exists
    Given SavedView A with isDefault=true exists
    When  SavedView B created with isDefault=true
    Then  HTTP 201; SavedView B isDefault=true; SavedView A isDefault=false (automatically)

  @AC3
  Scenario: AC3 – BR-05 – invalid columnOrder entry – catalog has no column "unknown"
    Given catalog has no column "unknown"
    When  `columnOrder: ["name", "unknown"]`
    Then  HTTP 422 "The attribute ‹unknown› is not in the column configuration of the catalog"

  @AC4
  Scenario: AC4 – Apply default view on open – catalog has SavedView "Compact view" wit...
    Given catalog has SavedView "Compact view" with isDefault=true; columnOrder=[name, status]; activeFilterIds=[filter-active-systems]
    When  visitor opens the catalog
    Then  table shows only columns name and status; filter "Active systems only" is active; `totalCount` filtered accordingly

  @AC5
  Scenario: AC5 – No default – catalog has no SavedView with isDefault=true
    Given catalog has no SavedView with isDefault=true
    When  visitor opens the catalog
    Then  all configured columns visible; no filter preselected

  @AC6
  Scenario: AC6 – Delete SavedView – `DELETE /api/v1/catalogs/{id}/views/{viewId}` for the default view
    Given the precondition according to the requirement is met
    When  `DELETE /api/v1/catalogs/{id}/views/{viewId}` for the default view
    Then  HTTP 200; view deleted; no other default is set automatically; next open uses no default
