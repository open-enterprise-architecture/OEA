# Derived from: requirements/req/req-050-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-06
Feature: REQ-050 – Place catalog in the navigation tree

  The system MUST enable the catalog manager to assign a
  [Catalog](../../business-objects/catalog.md) to one or more
  [TreeNode](../../business-objects/tree-node.md) nodes as a
  `TreeNodeItem` (itemType=`catalog`, referenceId=catalogId). The
  **soft-reference principle** applies: the same catalog MAY appear in
  multiple nodes (BR-06); removing a `TreeNodeItem` MUST NOT delete
  the catalog record (BR-05).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Placement, happy path – catalog "Application Inventory" and TreeNode "IT Landscape" exist
    Given catalog "Application Inventory" and TreeNode "IT Landscape" exist
    When  `POST /api/v1/tree-nodes/{nodeId}/items` with `{ itemType: "catalog", referenceId: "{catalogId}" }`
    Then  HTTP 201; TreeNodeItem created; catalog appears in the navigation tree under "IT Landscape"

  @AC2
  Scenario: AC2 – Soft reference – multiple placement – catalog "Application Inventory" is already attached to "IT Landscape"
    Given catalog "Application Inventory" is already attached to "IT Landscape"
    When  catalog is additionally attached to "Compliance/ISO 27001 View"
    Then  HTTP 201; catalog appears in both nodes; there is one catalog record (no duplicate)

  @AC3
  Scenario: AC3 – BR-05 – Removal does not delete catalog – `DELETE /api/v1/tree-nodes/...
    Given the precondition according to the requirement is met
    When  `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (remove TreeNodeItem)
    Then  HTTP 200; TreeNodeItem deleted; `GET /api/v1/catalogs/{catalogId}` still returns HTTP 200

  @AC4
  Scenario: AC4 – Open via navigation tree – catalog is attached in the navigation tree
    Given catalog is attached in the navigation tree
    When  visitor clicks the entry in the navigation tree
    Then  catalog is opened directly (identical to opening via catalog overview); default SavedView is applied (REQ-048)

  @AC5
  Scenario: AC5 – displayLabel override – `{ itemType: "catalog", referenceId: "{catalo...
    Given the precondition according to the requirement is met
    When  `{ itemType: "catalog", referenceId: "{catalogId}", displayLabel: "System inventory" }`
    Then  in the navigation tree the catalog appears under the name "System inventory"; the name of the catalog object itself remains "Application Inventory"
