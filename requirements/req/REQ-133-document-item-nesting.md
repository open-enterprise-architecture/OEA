---
id: REQ-133
title: Nesting of DocumentItems (Hierarchical Chapter Structure)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - document-collection
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-133: Nesting of DocumentItems (Hierarchical Chapter Structure)

## Statement

The system MUST allow hierarchical nesting of DocumentItems within a DocumentCollection. A DocumentItem MAY reference a parent DocumentItem of the same collection via the field `parentId` (`parentId=null` = root chapter).

**Rules:**

- Maximum depth: **5 levels** (level 0 = root, `parentId=null`)
- Cycles are forbidden (BR-11); the system MUST perform a cycle check on every `parentId` change
- `sortOrder` applies relative to sibling items with the same `parentId`
- Deleting a parent item: child items are **deleted along with it** (cascade delete) — with a warning before execution
- Moving: `parentId` and `sortOrder` of an item can be changed via drag & drop in the chapter navigation

## Rationale

Without nesting, complex documentation (e.g. Arc42 §5 building block view with sub-views) can hardly be structured as a flat list. Hierarchical chapters enable a natural outline analogous to book chapters (§1 → §1.1 → §1.1.1).

## Acceptance Criteria

**AC1** (Create sub-item):

- When: the user selects "Create sub-chapter" in the chapter navigation
- Then: a new DocumentItem with the `parentId` of the selected item is created; it appears indented in the navigation

**AC2** (Depth limit):

- When: the user tries to create a sub-chapter at level 6
- Then: the action is refused with the note "Maximum depth (5) reached"

**AC3** (Cycle protection):

- When: the `parentId` of an item would be set such that a cycle arises (item A → B → A)
- Then: saving refused; error message

**AC4** (Cascade delete warning):

- When: the user deletes an item with children
- Then: warning "This chapter has N sub-chapters that will also be deleted"; confirmation required

**AC5** (Drag & drop):

- When: the user moves an item in the chapter navigation via drag & drop
- Then: `parentId` and `sortOrder` are updated; the navigation shows the new structure immediately

## Dependencies

- **Prerequisites**: REQ-132 (DocumentItem base structure)
- **Consequences**: –

## Implementation Notes

- Cycle check: iterative ancestor walk from `parentId` to the root; if the own ID is found → cycle; O(depth) ≤ O(5) — not a graph problem
- Chapter navigation: tree component with drag & drop (e.g. vue-draggable-next); levels visually distinguishable by indentation

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
