# Spec: openspec catalog/catalog-columns
# Source material: requirements/user-stories/US-047-configure-columns.md, REQ-044; ADR-032

@US-047 @UC-06 @catalog
Feature: Catalog columns

  A catalog manager decides which attributes of the primary metatype a catalog
  shows, in which order, with which label, whether they are visible and sortable,
  and by which column the catalog is sorted by default.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the repository contains no applications

  @AC1
  Scenario: Built-in and custom attributes
    Given "Application Component" maps the properties "Vendor" and "Support End Date"
    And the enterprise architect has created the catalog "Attribute Inventory" with scope "instance"
    When the enterprise architect configures every attribute as a column of "Attribute Inventory"
    Then the columns of "Attribute Inventory" name the system fields and the properties "Vendor" and "Support End Date"

  @REQ-044 @AC2
  Scenario: Attribute of another metatype
    Given the enterprise architect has created the catalog "Foreign Inventory" with scope "instance"
    When the enterprise architect configures a property that "Application Component" does not map as column of "Foreign Inventory"
    Then the column change is rejected with 422 naming "columns[0].propertyDefId"

  @AC2
  Scenario: Status before name
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Ordered Inventory" with scope "instance"
    When the enterprise architect configures the columns "Status" and "Name" of "Ordered Inventory"
    Then the viewer sees the columns "Status" and "Name" in the catalog "Ordered Inventory"

  @AC3
  Scenario: Own label
    Given the enterprise architect has created the catalog "Labelled Inventory" with scope "instance"
    When the enterprise architect configures the columns "Status" labelled "Operational Status" and "Name" of "Labelled Inventory"
    Then the viewer sees the columns "Operational Status" and "Name" in the catalog "Labelled Inventory"
    And the first column of "Labelled Inventory" still refers to the property "Status"

  @AC4
  Scenario: Hidden column
    Given the enterprise architect has created the catalog "Hidden Inventory" with scope "instance"
    When the enterprise architect configures the columns "Name" and a hidden "Description" of "Hidden Inventory"
    Then the viewer sees only the column "Name" in the catalog "Hidden Inventory"
    And the configuration of "Hidden Inventory" still lists the hidden "Description"

  @AC5
  Scenario: No visible column
    Given the enterprise architect has created the catalog "Invisible Inventory" with scope "instance"
    When the enterprise architect configures only a hidden "Name" column of "Invisible Inventory"
    Then the column change is rejected with 422 naming "columns"

  Scenario: Default sort by status
    Given the applications "Billing" with the status "planned" and "Archive" with the status "active" exist
    And the enterprise architect has created the catalog "Sorted Status Inventory" with scope "instance"
    When the enterprise architect configures the columns "Name" and "Status" sorted descending of "Sorted Status Inventory"
    Then the viewer sees the rows "Archive" and "Billing" in this order in the catalog "Sorted Status Inventory"

  Scenario: Two default sorts
    Given the enterprise architect has created the catalog "Twice Sorted Inventory" with scope "instance"
    When the enterprise architect configures two columns with a default sort of "Twice Sorted Inventory"
    Then the column change is rejected with 422 naming "columns"

  Scenario: Reader without catalog management
    Given the enterprise architect has created the catalog "Managed Inventory" with scope "instance"
    When the viewer configures the columns "Name" and "Status" of "Managed Inventory"
    Then the column change is rejected with 403
    And the catalog "Managed Inventory" has no configured columns

  Scenario: Own personal catalog
    Given the viewer creates the catalog "Viewer Inventory" for "Application Component" with scope "personal"
    When the viewer configures the columns "Name" and "Status" of "Viewer Inventory"
    Then the columns of "Viewer Inventory" are saved and the audit trail names the viewer
