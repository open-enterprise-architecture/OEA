# Spec: openspec catalog/catalog-query
# Source material: requirements/user-stories/US-049-show-catalog-data.md, REQ-046; ADR-019, ADR-032

@US-049 @UC-06 @catalog
Feature: Catalog query

  A catalog shows the active entities of its primary metatype as a table that
  can be sorted and paged. Readers see shared catalogs and their own personal
  catalogs; deleted entities are not shown.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the repository contains no applications

  @US-049 @AC1 @REQ-046 @AC6
  Scenario: Catalog with three applications
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Query Inventory" with scope "instance"
    When the viewer queries the catalog "Query Inventory"
    Then the catalog shows the columns "Name" and "Status"
    And the catalog shows the rows "CRM System", "ERP" and "Intranet" in this order with the status "active"

  @US-049 @AC4 @REQ-046 @AC6
  Scenario: Empty repository
    Given the enterprise architect has created the catalog "Empty Inventory" with scope "instance"
    When the viewer queries the catalog "Empty Inventory"
    Then the catalog shows no rows and a total of 0 entries

  @US-049 @AC5 @REQ-046 @AC5
  Scenario: Second page sorted by name
    Given 60 applications with the prefix "Application" exist
    And the enterprise architect has created the catalog "Paged Inventory" with scope "instance"
    When the enterprise architect queries the catalog "Paged Inventory" with page 1, page size 20 and sort "name:asc"
    Then the catalog shows a total of 60 entries
    And the catalog shows the 20 rows "Application 021" to "Application 040" in alphabetical order

  @US-049 @AC5
  Scenario: Default page size
    Given 120 applications with the prefix "Application" exist
    And the enterprise architect has created the catalog "Large Inventory" with scope "instance"
    When the viewer queries the catalog "Large Inventory"
    Then the catalog shows 50 rows on page 1 of 3

  @US-049 @AC2 @AC3 @REQ-046 @AC5
  Scenario: Sort descending by name
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Sorted Inventory" with scope "instance"
    When the enterprise architect queries the catalog "Sorted Inventory" sorted by "name:desc"
    Then the catalog shows the rows "Intranet", "ERP" and "CRM System" in this order with the status "active"

  @REQ-043 @AC4 @ADR-032
  Scenario: Personal catalog of another person
    Given the enterprise architect has created the catalog "Private Inventory" with scope "personal"
    When the viewer queries the catalog "Private Inventory"
    Then the query is answered with 404

  @ADR-019
  Scenario: Deleted entity
    Given the applications "Kept", "Removed" and "Also Kept" exist with the status "active"
    And the application "Removed" is soft-deleted
    And the enterprise architect has created the catalog "Cleaned Inventory" with scope "instance"
    When the enterprise architect queries the catalog "Cleaned Inventory"
    Then the catalog shows the rows "Also Kept" and "Kept" and a total of 2 entries
