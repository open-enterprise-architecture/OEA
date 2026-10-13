---
id: REQ-115
title: Manage TRM Category Hierarchy
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-115: Manage TRM Category Hierarchy

## Statement

The system MUST allow creating, renaming, and deleting TRM categories with `scope=organization`. On creation, the `level` field MUST be set automatically as `parent.level + 1`. Deleting a category with child nodes MUST be rejected with HTTP 422 (remove children first). Deleting a category with assigned entities (`classifiedEntities` not empty) MUST produce a warning but not block. TRM categories with `scope=organization` MUST be reorganizable within the hierarchy via drag & drop (cycle protection: BR-01 from trm-category.md).

## Rationale

The TRM is a living taxonomy. Without CRUD and reorganization, an organization cannot adapt its TRM to its own structures, which endangers acceptance of the tool.

## Acceptance Criteria

**AC1** (Automatic level):
- When: a subcategory is created under "Security Services"
- Then: the `level` field is automatically set to `parent.level + 1`

**AC2** (Delete with child nodes):
- When: a category with 3 child nodes is to be deleted
- Then: the system responds with HTTP 422 "Child nodes must be removed first"

**AC3** (Drag & drop cycle protection):
- When: a category is dragged via drag & drop under one of its own descendants
- Then: the drop is visually blocked; an API attempt responds with HTTP 422

## Dependencies

- **Prerequisites**: REQ-112 (import produces scope=imported categories as a basis)
- **Consequences**: REQ-116 (SBB governance assignment per category), REQ-117 (review date)

## Implementation Notes

- Hierarchy: adjacency list model with `parentId`; `level` as a computed field or denormalized
- Drag & drop: cycle check in the frontend (before drop) and backend (HTTP 422)
- Delete with entities: warning modal with the number of affected entities; no API blocking

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
