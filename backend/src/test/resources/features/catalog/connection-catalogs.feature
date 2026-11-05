# Spec: openspec catalog/catalog-query (Connections as rows, Default columns) and
# catalog/catalog-columns (All attributes as columns)
# Source material: requirements/user-stories/US-080-catalog-join-connection-type-primary.md, REQ-065; ADR-004, ADR-010

@US-080 @UC-06 @catalog
Feature: Connection catalogs

  A catalog whose primary metatype is a connection metatype lists the
  connections, with the attributes of their source and target, and joins the
  entities connected to each connection.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the metamodel has the connection metatype "Information Flow" from "Application Component" to "Application Component"
    And the metamodel has the connection metatype "Carries Data" from "Information Flow" to "Data Object"

  @AC1 @REQ-065 @AC1
  Scenario: Data flow catalog
    Given the information flows "Orders" from "CRM System" to "ERP" and "Invoices" from "ERP" to "Accounting"
    When the enterprise architect creates the catalog "Data Flow Overview" for "Information Flow" with scope "instance"
    Then the viewer sees the flow "Orders" from "CRM System" to "ERP" in the catalog "Data Flow Overview"
    And the viewer sees the flow "Invoices" from "ERP" to "Accounting" in the catalog "Data Flow Overview"

  @AC2 @REQ-065 @AC5
  Scenario: Default columns of a connection catalog
    When the enterprise architect creates the catalog "Flow Columns" for "Information Flow" with scope "instance"
    Then the viewer sees the columns "Source", "Name" and "Target" with the keys "source.name", "name" and "target.name" in the catalog "Flow Columns"

  @AC2 @REQ-065 @AC5
  Scenario: Sort by source
    Given the information flows "Purchase Orders" from "Billing" to "Warehouse" and "Shipments" from "Warehouse" to "Carrier"
    When the enterprise architect creates the catalog "Sorted Flows" for "Information Flow" with scope "instance"
    And the viewer sorts the catalog "Sorted Flows" by "source.name" ascending
    Then the flow "Purchase Orders" comes before "Shipments"

  @AC3 @AC4 @REQ-065 @AC2 @AC4
  Scenario: Data objects of a data flow
    Given the information flows "Customer Sync" from "CRM System" to "ERP" and "Payroll Export" from "HR" to "ERP"
    And the information flow "Customer Sync" carries the data objects "Customer master data" and "Order"
    And the enterprise architect creates the catalog "Carried Data" for "Information Flow" with scope "instance"
    When the enterprise architect adds the join "Data objects" via "Carries Data" outbound to "Data Object" to "Carried Data"
    Then the viewer sees the flow "Customer Sync" in one row of "Carried Data" with the data objects "Customer master data" and "Order"

  @AC2
  Scenario: Endpoint column outside a connection catalog
    Given the enterprise architect has created the catalog "Element Endpoint" with scope "instance"
    When the enterprise architect configures the source name as column of "Element Endpoint"
    Then the endpoint column is rejected with 422 naming "columns[0].endpoint"
