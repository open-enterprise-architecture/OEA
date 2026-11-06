# Spec: openspec catalog/catalog-joins
# Source material: requirements/user-stories/US-048-add-join.md, REQ-045; ADR-032

@US-048 @UC-06 @catalog
Feature: Catalog joins

  A catalog manager adds joins to a catalog: via a connection metatype, in a
  direction, to a target metatype whose columns the catalog then shows next to
  each entity of the primary metatype.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the repository contains no applications
    And the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"

  @AC1 @REQ-045 @AC1
  Scenario: Interfaces of an application
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the enterprise architect has created the catalog "Interface Inventory" with scope "instance"
    When the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Interface Inventory"
    Then the catalog "Interface Inventory" contains the join "Interfaces" with a new ID
    And the viewer sees "CRM System" in one row of "Interface Inventory" with the interfaces "REST API" and "SFTP Export"

  @AC5
  Scenario: Default mode of the join
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the enterprise architect has created the catalog "Rows Inventory" with scope "instance"
    When the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" in mode "rows" to "Rows Inventory"
    Then the viewer sees 2 rows for "CRM System" in the catalog "Rows Inventory"
    And the catalog "Rows Inventory" keeps the default join mode "aggregate"

  @AC2 @REQ-045 @AC2
  Scenario: Not a connection metatype
    Given the enterprise architect has created the catalog "Wrong Connection Inventory" with scope "instance"
    When the enterprise architect adds the join "Wrong" via "Application Component" outbound to "Interface" to "Wrong Connection Inventory"
    Then the join change is rejected with 422 naming "connectionMetatypeId"

  @AC3 @REQ-045 @AC3
  Scenario: Target not reachable
    Given the enterprise architect has created the catalog "Unreachable Inventory" with scope "instance"
    When the enterprise architect adds the join "Nodes" via "Data Flow" outbound to "Technology Component" to "Unreachable Inventory"
    Then the join change is rejected with 422 naming "targetMetatypeId"

  @AC4 @REQ-045 @AC5
  Scenario: Unknown target column
    Given the enterprise architect has created the catalog "Unknown Column Inventory" with scope "instance"
    When the enterprise architect adds a join to "Interface" with a property that "Interface" does not map to "Unknown Column Inventory"
    Then the join change is rejected with 422 naming "targetColumns[1].propertyDefId"

  @AC6 @REQ-045 @AC4
  Scenario: Remove a join
    Given the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the enterprise architect has created the catalog "Removed Join Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Removed Join Inventory"
    When the enterprise architect removes the join "Interfaces" from "Removed Join Inventory"
    Then the catalog "Removed Join Inventory" has no joins
    And the viewer sees no join results in the catalog "Removed Join Inventory"

  @US-053 @AC4
  Scenario: Remove a join used by a view
    Given the enterprise architect has created the catalog "Viewed Join Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Viewed Join Inventory"
    And the enterprise architect saves the view "Detail view" with the join "Interfaces" in rows mode sorted by its name in "Viewed Join Inventory"
    When the enterprise architect removes the join "Interfaces" from "Viewed Join Inventory"
    Then the view "Detail view" of "Viewed Join Inventory" has neither a join mode nor a sort

  @REQ-045 @ADR-032
  Scenario: Reader without catalog management
    Given the enterprise architect has created the catalog "Protected Join Inventory" with scope "instance"
    When the viewer adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Protected Join Inventory"
    Then the join change is rejected with 403
    And the catalog "Protected Join Inventory" has no joins
