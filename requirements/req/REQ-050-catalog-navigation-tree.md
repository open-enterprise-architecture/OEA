---
id: REQ-050
title: Place a catalog in the navigation tree
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
    - tree-node
  business_rules:
    - tree-node:BR-04
    - tree-node:BR-05
    - tree-node:BR-06
  stakeholders:
    - SH-03
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-050: Place a catalog in the navigation tree

## Statement

The system MUST allow the catalog manager to assign a [Catalog](../../business-objects/catalog.md) to one or more [TreeNode](../../business-objects/tree-node.md) nodes as a `TreeNodeItem` (itemType=`catalog`, referenceId=catalogId). The **soft reference principle** applies: the same catalog MAY appear in several nodes (BR-06); removing a `TreeNodeItem` MUST NOT delete the catalog record (BR-05). The catalog MUST be directly openable by clicking the TreeNode entry in the navigation tree.

## Rationale

Without placement in the navigation tree, catalogs can only be found via the global catalog overview. For large repositories (many catalogs), contextual navigation – e.g., all business architecture catalogs in the folder "Business Architecture" – is indispensable so that visitors can navigate specifically to relevant views.

## Context

The navigation tree is a shared structure for all repository content: entities, diagrams, and catalogs. For catalogs, the same TreeNode rules apply as for other objects. Placement in the tree is optional – a catalog is also accessible via the catalog overview without tree placement.

## Type-Specific Fields

### For type=functional

**Inputs (attach catalog to tree)**:

| Field | Type | Optional | Description |
|---|---|---|---|
| catalogId | string | mandatory | UUID of the catalog to attach |
| parentNodeId | string | mandatory | UUID of the target TreeNode (must exist) |
| displayLabel | string | optional | Overrides the catalog name in the tree |
| sortOrder | integer | optional | Order within the node; default: at the end |

**Processing**:

1. Check that the catalog exists (BR-04); 404 otherwise
2. Check that the target TreeNode exists; 404 otherwise
3. TreeNodeItem anlegen: `{ itemType: "catalog", referenceId: catalogId, parentNodeId, displayLabel, sortOrder }`
4. Soft reference: the same catalog can be attached to several nodes; no duplicate prevention at `referenceId` level

**Processing (remove catalog from tree)**:

1. Delete the TreeNodeItem (not the catalog itself – BR-05)
2. All other entries of the catalog in other nodes are retained

**Outputs**:

- HTTP 201 Created with the new TreeNodeItem
- HTTP 404 Not Found if the catalog or TreeNode does not exist
- HTTP 403 Forbidden on missing write permission

## Acceptance Criteria

**AC1** (Placement, happy path):

- Given: catalog "Application Inventory" and TreeNode "IT Landscape" exist
- When: `POST /api/v1/tree-nodes/{nodeId}/items` with `{ itemType: "catalog", referenceId: "{catalogId}" }`
- Then: HTTP 201; TreeNodeItem created; the catalog appears in the navigation tree under "IT Landscape"

**AC2** (Soft reference – multiple placement):

- Given: catalog "Application Inventory" is already attached in "IT Landscape"
- When: the catalog is additionally attached in "Compliance/ISO 27001 View"
- Then: HTTP 201; the catalog appears in both nodes; there is one catalog record (no duplicate)

**AC3** (BR-05 – removal does not delete the catalog):

- When: `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (remove the TreeNodeItem)
- Then: HTTP 200; TreeNodeItem deleted; `GET /api/v1/catalogs/{catalogId}` still returns HTTP 200

**AC4** (Open via navigation tree):

- Given: the catalog is attached in the navigation tree
- When: a visitor clicks the entry in the navigation tree
- Then: the catalog is opened directly (identical to opening via the catalog overview); the default SavedView is applied (REQ-048)

**AC5** (displayLabel override):

- When: `{ itemType: "catalog", referenceId: "{catalogId}", displayLabel: "System inventory" }`
- Then: in the navigation tree the catalog appears under the name "System inventory"; the name of the catalog object itself remains "Application Inventory"

## Dependencies

- Blocked by: REQ-043 (the catalog must exist), TreeNode implementation (separate REQ still pending)
- Related: REQ-046 (opening the catalog from the tree)

## Implementation Notes

- Endpoint: `POST /api/v1/tree-nodes/{nodeId}/items` (generic for all item types)
- Removal: `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}` (deletes only the TreeNodeItem, not the catalog)
- Navigation tree response: TreeNode with items[] contains for each catalog item: type=catalog, referenceId, displayLabel, catalogName (for display when no displayLabel is set)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
