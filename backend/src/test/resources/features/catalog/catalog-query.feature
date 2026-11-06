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

  @US-051 @AC1 @REQ-046 @AC3
  Scenario: Filtered total
    Given 120 applications with the prefix "Counted" exist, 30 of them with the status "active"
    And the enterprise architect has created the catalog "Filtered Count" with scope "instance"
    When the viewer filters the catalog "Filtered Count" by "status:eq:active"
    Then the filtered catalog shows 30 rows and a total of 30 entries on 1 page

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

  @US-047 @AC2 @AC3 @AC4
  Scenario: Configured columns
    Given the enterprise architect has created the catalog "Configured Inventory" with scope "instance"
    And the enterprise architect configures the columns "Status" labelled "Operational Status", "Name" and a hidden "Description" of "Configured Inventory"
    When the viewer queries the catalog "Configured Inventory"
    Then the catalog shows the columns "Operational Status" and "Name"

  @US-047
  Scenario: Status in enum order
    Given the applications "Old" with the status "retired", "New" with the status "planned" and "Running" with the status "active" exist
    And the application "Unknown" exists without a status
    And the enterprise architect has created the catalog "Enum Inventory" with scope "instance"
    When the enterprise architect queries the catalog "Enum Inventory" sorted by "status:asc"
    Then the catalog shows the rows "New", "Running", "Old" and "Unknown" in this order

  @US-047
  Scenario: Column not sortable
    Given the enterprise architect has created the catalog "Unsortable Inventory" with scope "instance"
    And the enterprise architect configures the columns "Name" not sortable and "Status" of "Unsortable Inventory"
    When the enterprise architect queries the catalog "Unsortable Inventory" sorted by "name:asc"
    Then the query is answered with 400


  @US-048 @AC1 @REQ-046 @AC1
  Scenario: Aggregated interfaces
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the enterprise architect has created the catalog "Aggregated Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Aggregated Inventory"
    Then the viewer sees "CRM System" in one row of "Aggregated Inventory" with the interfaces "REST API" and "SFTP Export"

  @US-049 @AC1 @REQ-046 @AC4
  Scenario: Application without interfaces
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the enterprise architect has created the catalog "Empty Join Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Empty Join Inventory"
    Then the viewer sees "ERP" in one row of "Empty Join Inventory" without interfaces

  @US-050 @AC1 @AC2 @AC3 @REQ-049 @AC1 @AC2 @AC3
  Scenario: Switch to rows and back to compact
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the enterprise architect has created the catalog "Switched Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Switched Inventory"
    When the viewer queries the catalog "Switched Inventory" with the join "Interfaces" in mode "rows"
    Then the catalog shows 2 rows for "CRM System"
    And the catalog "Switched Inventory" keeps the join "Interfaces" without default mode
    When the viewer queries the catalog "Switched Inventory" with the join "Interfaces" in mode "aggregate"
    Then the catalog shows 1 rows for "CRM System"

  @US-050 @AC4 @REQ-049 @AC5
  Scenario: Joins switched independently
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the metamodel has the connection metatype "Runs On" from "Application Component" to "Technology Component"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the application "CRM System" runs on the technologies "Linux" and "PostgreSQL" via "Runs On"
    And the enterprise architect has created the catalog "Independent Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Independent Inventory"
    And the enterprise architect adds the join "Technologies" via "Runs On" outbound to "Technology Component" to "Independent Inventory"
    When the viewer queries the catalog "Independent Inventory" with the join "Interfaces" in mode "rows"
    Then the catalog shows 2 rows for "CRM System"
    And every row of "CRM System" lists 2 results of the join "Technologies"

  @US-050 @REQ-049 @AC4
  Scenario: Unknown join
    Given the enterprise architect has created the catalog "Unknown Join Inventory" with scope "instance"
    When the viewer queries the catalog "Unknown Join Inventory" with an unknown join in mode "rows"
    Then the query is rejected with 400 naming "joinMode"

  @US-048 @REQ-046 @AC5
  Scenario: Sort by a join column
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "SFTP Export" and "REST API" via "Data Flow"
    And the enterprise architect has created the catalog "Protocol Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Protocol Inventory"
    When the viewer queries the catalog "Protocol Inventory" with the join "Interfaces" in mode "rows" sorted by the protocol
    Then the interface protocols are "REST", "SFTP" and none in this order
    When the viewer queries the catalog "Protocol Inventory" sorted by the protocol of the join "Interfaces"
    Then the query is rejected with 400 naming "sort"

  @US-048 @REQ-046 @AC5
  Scenario: Rows mode counts rows
    Given the metamodel has the connection metatype "Data Flow" from "Application Component" to "Interface"
    And the applications "CRM System", "ERP" and "Intranet" exist with the status "active"
    And the application "CRM System" reaches the interfaces "REST API" and "SFTP Export" via "Data Flow"
    And the application "Intranet" reaches the interface "LDAP" via "Data Flow"
    And the enterprise architect has created the catalog "Counted Inventory" with scope "instance"
    And the enterprise architect adds the join "Interfaces" via "Data Flow" outbound to "Interface" to "Counted Inventory"
    When the viewer queries the catalog "Counted Inventory" with the join "Interfaces" in mode "rows"
    Then the catalog shows a total of 4 rows
