---
id: REQ-094
title: Navigation tree folder CRUD
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
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
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-094: Navigation tree folder CRUD

## Statement

The system MUST enable creating, renaming and deleting TreeNode folders in the navigation tree. Folder names MUST be unique among siblings (BR-01). Deleting a folder MUST recursively remove all child nodes and items; referenced objects remain untouched (BR-05). The root node MUST NOT be deletable (BR-02); renaming is allowed.

## Rationale

The navigation tree is the primary structuring instrument for the EA repository. Without CRUD operations on folders, no meaningful navigation can be built.

## Acceptance Criteria

**AC1** (Create folder):

- When: a new folder is created under a parent
- Then: the API responds with HTTP 201; the folder is visible in the tree

**AC2** (Reject duplicate name):

- When: a folder is created with a name that already exists under the same parent
- Then: the API responds with HTTP 422 "Name already exists"

**AC3** (Recursive deletion):

- When: a folder with 3 child folders and 10 items is deleted
- Then: child folders and items are removed; referenced entities remain untouched

**AC4** (Root node protection):

- When: a DELETE request is sent to `/tree-nodes/root`
- Then: the API responds with HTTP 422 "Root node cannot be deleted"

## Dependencies

- **Prerequisites**: none
- **Downstream effects**: REQ-095 (items), REQ-096 (drag & drop), REQ-097 (multiple placement)

## Implementation Notes

- Implement recursive deletion as DB CASCADE or explicit recursive traversal
- Unique constraint on `(parentId, name)` in the DB
- Root node created by seeding; no API endpoint for creating the root

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
