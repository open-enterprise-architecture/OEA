---
id: REQ-097
title: Navigation tree multiple placement of objects
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

# REQ-097: Navigation tree multiple placement of objects

## Statement

The same repository object (entity, diagram or catalog) MUST be allowed to be referenced as an item in multiple folders at the same time. Each folder entry is an independent TreeNodeItem with its own `referenceId`; no data duplication of the object.

## Rationale

An application cluster can belong to the domain "Finance" and to the initiative "Cloud Migration" at the same time. Exclusive placement would restrict the representation of real structures and force users into data duplication.

## Acceptance Criteria

**AC1** (Multiple placement):

- When: the entity "SAP ERP" is attached as an item in the folders "Finance" and "Cloud Migration"
- Then: two independent TreeNodeItems with the same `referenceId` exist; the entity itself is unchanged

**AC2** (Independent removal):

- When: the item "SAP ERP" is removed from the folder "Finance"
- Then: the entry in "Finance" is deleted; the entry in "Cloud Migration" remains valid

## Dependencies

- **Prerequisites**: REQ-095 (soft-reference principle)
- **Downstream effects**: none

## Implementation Notes

- No unique constraint on `(folderId, referenceId)` — multiple placement explicitly allowed
- TreeNodeItem has its own ID (`referenceId`) independent of the referenced object
- No database model duplicate of the object; only the link entry is created multiple times

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
