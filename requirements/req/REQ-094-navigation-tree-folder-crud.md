---
id: REQ-094
title: Navigation tree folder CRUD
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-11-07
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - tree-node
    - catalog
    - entity
  stakeholders:
    - SH-03
  adrs:
    - ADR-032
supersedes: []
superseded_by: []
---

# REQ-094: Navigation tree folder CRUD

## Statement

The system MUST enable creating, renaming and deleting TreeNode folders in the navigation tree. Folder names MUST be unique among siblings regardless of case (BR-01). Changing folders MUST require `viewpoint:manage`. Deleting a folder MUST recursively remove all child nodes and items; referenced objects remain untouched (BR-05). The root node "Repository" MUST NOT be deletable (BR-02); renaming is allowed.

## Rationale

The navigation tree is the primary structuring instrument for the EA repository. Without CRUD operations on folders, no meaningful navigation can be built.

## Acceptance Criteria

**AC1** (Create folder):

- When: a new folder is created under a parent
- Then: the API responds with HTTP 201; the folder is visible in the tree

**AC2** (Reject duplicate name):

- When: a folder is created with a name that already exists under the same parent, also in other upper and lower case
- Then: the API responds with HTTP 422 naming `name` ("A folder with this name already exists")

**AC3** (Recursive deletion):

- When: a folder with 3 child folders and 10 items is deleted
- Then: child folders and items are removed; referenced entities remain untouched

**AC4** (Root node protection):

- When: a DELETE request is sent to `/tree-nodes/{rootId}`
- Then: the API responds with HTTP 422 "The root folder cannot be deleted"

## Dependencies

- **Prerequisites**: none
- **Downstream effects**: REQ-095 (items), REQ-096 (drag & drop), REQ-097 (multiple placement)

## Implementation Notes

- Recursive deletion by `ON DELETE CASCADE` on `tree_nodes.parent_id` and `tree_node_items.tree_node_id`
- Unique index on `(coalesce(parent_id, zero UUID), lower(name))`; a trigger protects the root
- The root "Repository" is created by the migration `V21__navigation_tree.sql`; no API endpoint for creating the root
- Limits: at most 10 levels below the root, at most 500 folders; names trimmed, 1–255 characters without control characters
- Implemented by the OpenSpec change `us-054-catalog-navigation-tree`: `POST /tree-nodes`, `PATCH` and `DELETE /tree-nodes/{nodeId}`, and the folder actions of the sidebar SCR-055

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-07 | Solution Architect | Realigned with us-054-catalog-navigation-tree: names unique regardless of case, the root "Repository" created by the migration, permission `viewpoint:manage`, limits |
