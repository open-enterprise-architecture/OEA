# Spec: openspec catalog/catalog-filters
# Source material: requirements/user-stories/US-051-ad-hoc-filter.md, US-052-saved-filter.md, US-080 AC5, AC6; REQ-047, REQ-064; ADR-032, ADR-044

@US-051 @US-052 @UC-06 @catalog
Feature: Catalog filters

  A reader narrows a catalog with filters on its columns, including the
  columns of joins and of the source and target of a connection; a catalog
  keeps named saved filters for quick selection.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the repository contains no applications

  @US-051 @AC1 @AC4 @REQ-047
  Scenario: Active applications only
    Given 10 applications with the prefix "Filtered" exist, 4 of them with the status "active"
    And the enterprise architect has created the catalog "Filtered Inventory" with scope "instance"
    When the viewer filters the catalog "Filtered Inventory" by "status:eq:active"
    Then the filtered catalog shows 4 rows and a total of 4 entries
    When the viewer queries the catalog "Filtered Inventory" without filters
    Then the filtered catalog shows 10 rows and a total of 10 entries

  @US-051 @AC3 @US-052 @AC4 @REQ-047 @AC5
  Scenario: Both and either condition
    Given 10 applications with the prefix "Combined" exist, 4 of them with the status "active"
    And the enterprise architect has created the catalog "Combined Inventory" with scope "instance"
    When the viewer filters the catalog "Combined Inventory" by "status:eq:active" and "name:eq:Combined 010"
    Then the filtered catalog shows 0 rows and a total of 0 entries
    When the viewer filters the catalog "Combined Inventory" by "status:eq:active" or "name:eq:Combined 010"
    Then the filtered catalog shows 5 rows and a total of 5 entries

  @ADR-044
  Scenario: Unknown column
    Given the enterprise architect has created the catalog "Unknown Filter" with scope "instance"
    When the viewer filters the catalog "Unknown Filter" by "nonexistent:eq:x"
    Then the filter is rejected with 400 naming "filter"

  @US-080 @AC5
  Scenario: Flows from the CRM
    Given the metamodel has the connection metatype "Information Flow" from "Application Component" to "Application Component"
    And the metamodel has the connection metatype "Carries Data" from "Information Flow" to "Data Object"
    And the information flows "Lead Orders" from "CRM System" to "ERP" and "Lead Invoices" from "ERP" to "Accounting"
    And the enterprise architect creates the catalog "Filtered Flows" for "Information Flow" with scope "instance"
    When the viewer filters the catalog "Filtered Flows" by "source.name:contains:CRM"
    Then the filtered catalog shows only "Lead Orders"

  @US-051 @AC2 @US-080 @AC6 @REQ-064
  Scenario: Data flows carrying personal data
    Given the metamodel has the connection metatype "Information Flow" from "Application Component" to "Application Component"
    And the metamodel has the connection metatype "Carries Data" from "Information Flow" to "Data Object"
    And data objects carry a classification
    And the information flows "Client Sync" from "CRM System" to "ERP" and "Stock Levels" from "ERP" to "Warehouse"
    And the information flow "Client Sync" carries "Client master data" classified "PII" and "Client Order" classified "Internal"
    And the information flow "Stock Levels" carries "Stock Item" classified "Internal"
    And the enterprise architect creates the catalog "GDPR Flows" for "Information Flow" with scope "instance"
    And the enterprise architect adds the join "Data objects" with name and classification via "Carries Data" outbound to "Data Object" to "GDPR Flows"
    When the viewer filters the catalog "GDPR Flows" by "Data objects" classification "PII"
    Then the filtered catalog shows only "Client Sync" with the data object "Client master data"

  @US-052 @AC1 @AC2 @REQ-047 @AC1 @AC2
  Scenario: Active systems only
    Given the enterprise architect has created the catalog "Saved Inventory" with scope "instance"
    When the enterprise architect saves the filter "Active systems only" with "status:eq:active" in "Saved Inventory"
    Then the viewer sees the saved filter "Active systems only" with "status" "eq" "active" in "Saved Inventory"

  @US-052 @REQ-047 @AC3
  Scenario: Name taken
    Given the enterprise architect has created the catalog "Taken Inventory" with scope "instance"
    And the enterprise architect saves the filter "Active systems only" with "status:eq:active" in "Taken Inventory"
    When the enterprise architect saves the filter "active systems only" with "name:isNotNull" in "Taken Inventory"
    Then the saved filter is rejected with 422 naming "name"

  @US-052 @REQ-047 @AC3
  Scenario: Unknown attribute
    Given the enterprise architect has created the catalog "Unknown Attribute" with scope "instance"
    When the enterprise architect saves the filter "Broken" with "nonexistent:eq:x" in "Unknown Attribute"
    Then the saved filter is rejected with 422 naming "expressions[0].attributePath"

  @US-052 @REQ-047
  Scenario: Remove a saved filter
    Given the enterprise architect has created the catalog "Removed Filter" with scope "instance"
    And the enterprise architect saves the filter "Active systems only" with "status:eq:active" in "Removed Filter"
    When the enterprise architect removes the saved filter "Active systems only" from "Removed Filter"
    Then the catalog "Removed Filter" has no saved filters

  @US-052 @AC3 @ADR-032
  Scenario: Reader of an instance catalog
    Given the enterprise architect has created the catalog "Guarded Inventory" with scope "instance"
    When the viewer saves the filter "Mine" with "name:isNotNull" in "Guarded Inventory"
    Then the saved filter is rejected with 403

  @US-052 @AC3 @ADR-032
  Scenario: Own personal catalog
    Given the viewer creates the catalog "Viewer Inventory" for "Application Component" with scope "personal"
    When the viewer saves the filter "Mine" with "name:isNotNull" in "Viewer Inventory"
    Then the audit trail names the viewer for the saved filter in "Viewer Inventory"
