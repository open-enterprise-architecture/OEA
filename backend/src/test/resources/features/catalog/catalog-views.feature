# Spec: openspec catalog/catalog-views
# Source material: requirements/user-stories/US-053-saved-view.md, US-049 AC6; REQ-048; ADR-032, ADR-044

@US-053 @UC-06 @catalog
Feature: Catalog views

  A catalog keeps named views: a column selection and order with filters,
  join modes and a sort. One view can be the default, and the query shows
  the columns of a view.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the repository contains no applications

  @AC1 @REQ-048 @AC1
  Scenario: Compact view
    Given the enterprise architect has created the catalog "Viewed Inventory" with scope "instance"
    When the enterprise architect saves the view "Compact view" with the columns "status" and "name" and the filter "status:eq:active" in "Viewed Inventory"
    Then the viewer sees the view "Compact view" with the columns "status" and "name" and the filter "status:eq:active" in "Viewed Inventory"

  @US-049 @AC6 @REQ-048 @AC4
  Scenario: Columns of the compact view
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Column View Inventory" with scope "instance"
    And the enterprise architect saves the view "Compact view" with the columns "status" and "name" and the filter "status:eq:active" in "Column View Inventory"
    When the viewer queries the catalog "Column View Inventory" with the view "Compact view"
    Then the view query shows the columns "status" and "name"

  @REQ-048 @AC3
  Scenario: Unknown column
    Given the enterprise architect has created the catalog "Unknown Column View" with scope "instance"
    When the enterprise architect saves the view "Broken" with the columns "name" and "unknown" and the filter "name:isNotNull" in "Unknown Column View"
    Then the view is rejected with 422 naming "columnOrder[1]"

  @REQ-048
  Scenario: Filter on a column outside the view
    Given the enterprise architect has created the catalog "Outside Filter View" with scope "instance"
    When the enterprise architect saves the view "Broken" with the columns "name" and "status" and the filter "description:isNull" in "Outside Filter View"
    Then the view is rejected with 422 naming "filters[0].attributePath"

  @AC3 @REQ-048 @AC2
  Scenario: Default switch
    Given the enterprise architect has created the catalog "Default Views" with scope "instance"
    And the enterprise architect saves the default view "Compact view" in "Default Views"
    When the enterprise architect saves the default view "Full view" in "Default Views"
    Then the default view of "Default Views" is "Full view"

  @REQ-048 @AC6
  Scenario: Remove the default view
    Given the enterprise architect has created the catalog "Removed Default" with scope "instance"
    And the enterprise architect saves the default view "Compact view" in "Removed Default"
    When the enterprise architect removes the view "Compact view" from "Removed Default"
    Then the catalog "Removed Default" has no default view

  @US-049 @AC6
  Scenario: Unknown view
    Given the enterprise architect has created the catalog "Unknown View Inventory" with scope "instance"
    When the viewer queries the catalog "Unknown View Inventory" with an unknown view
    Then the view query is rejected with 400 naming "view"

  @REQ-048
  Scenario: Column removed
    Given the enterprise architect has created the catalog "Stale View Inventory" with scope "instance"
    And the enterprise architect saves the view "Compact view" with the columns "status" and "name" and the filter "name:isNotNull" in "Stale View Inventory"
    When the enterprise architect configures only the name column of "Stale View Inventory"
    Then the view "Compact view" of "Stale View Inventory" is not available

  @ADR-032
  Scenario: Reader of an instance catalog
    Given the enterprise architect has created the catalog "Guarded Views" with scope "instance"
    When the viewer saves the view "Mine" with the columns "name" and "status" and the filter "name:isNotNull" in "Guarded Views"
    Then the view is rejected with 403
