---
id: REQ-146
title: Remove from Diagram (DEL) vs. Delete from Repository (Ctrl+DEL) via Context Menu
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-29
last_updated: 2026-11-20
author: requirements-engineer
references:
  use_cases:
    - UC-05
    - UC-14
    - UC-15
  business_objects:
    - diagram
    - architecture-element
  business_rules: []
  stakeholders:
    - SH-01
    - SH-03
    - SH-04
    - SH-07
  concept: []
  adrs:
    - ADR-019
supersedes: []
superseded_by: []
---

# REQ-146: Remove from Diagram (DEL) vs. Delete from Repository (Ctrl+DEL) via Context Menu

## Statement

The system MUST offer two semantically different delete operations in the diagram editor:

1. **"Remove from Diagram" (DEL)**: Removes the shape from the current diagram. The referenced entity in the solution remains unchanged. Other diagrams that reference the same entity are not affected.

2. **"Delete from repository" (Ctrl+DEL)**: Deletes the entity from the repository using soft delete (ADR-019) and requires `entity:delete`. The dialog first names what the deletion takes along: the children of the entity on all levels, the connections of the entity and its children, and the active solutions with changes to them. After the confirmation the entity, its children and their connections are deleted at once, outside the explicit save of the diagram, and their shapes and edges are removed from the canvas; the other unsaved changes stay. The stored shapes of other diagrams stay hidden while the entity is deleted and show again after it was restored (`entity:restore`).

Both operations MUST be available in the context menu (right-click on a shape) as well as as keyboard shortcuts.

## Rationale

The diagram is a view of the model — not a standalone data source. An architecture element exists in the model before it is made visible in diagrams. This separation must also be communicated clearly when deleting. An erroneous "Delete Entity" can affect many diagrams and is reversible through soft delete (ADR-019) — but the user must confirm explicitly.

## Context

| Operation | Shortcut | Scope | Entity in solution | Other diagrams |
|---|---|---|---|---|
| Remove from Diagram | DEL | this diagram only | unchanged | untouched |
| Delete from repository | Ctrl+DEL | repository-wide | soft-deleted with its cascade | shapes hidden, shown again after a restore |

Soft delete means: `deleted_at`, `deleted_by`, `deletionReason` set and, for the cascade, `deleted_with_entity_id` (ADR-019); the entity remains in the DB and does not appear in normal queries.

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Description |
|---|---|---|
| shapeId | uuid | selected shape in the diagram |
| operation | "remove-from-diagram" \| "delete-from-repository" | chosen delete operation |

**Processing — Remove from Diagram**:

1. User presses DEL or selects "Remove from Diagram" in the context menu
2. No confirmation dialog (non-destructive for the model)
3. `DELETE /api/v1/diagrams/{diagramId}/shapes/{shapeId}`
4. Shape disappears from the canvas immediately
5. Log: "[INFO] Shape removed from diagram: <Name> — entity preserved"

**Processing — Delete from repository**:

1. User presses Ctrl+DEL or selects "Delete from repository" in the context menu (`entity:delete`; not offered for new, unsaved entities)
2. `GET /api/v1/entities/{id}/deletion` names the children, the connections and the active solutions; the dialog asks for confirmation
3. User confirms → `DELETE /api/v1/entities/{id}` with the version of the preview (soft delete with cascade per ADR-019); a changed entity (412) asks to read the preview again
4. The shapes and edges of everything deleted are removed from the canvas; unsaved connections to them and unsaved changes of those entities are discarded, the other unsaved changes stay
5. Other diagrams: the stored shapes stay hidden while the entity is deleted
6. Audit: `entity.deleted` and `entity.cascade-deleted`; a restore writes `entity.undeleted`

**Context menu structure** (on right-click of a shape):

```text
Edit Properties
Add Connection
────────────────────
Remove from Diagram    DEL
Delete from repository Ctrl+DEL
```

**Outputs**:

- For "Remove": shape no longer on the canvas; entity still visible in catalog/explorer
- For "Delete from repository": entity and cascade marked as deleted; shapes removed from the canvas and hidden in all diagrams

## Acceptance Criteria

**AC1** (Remove from Diagram — model untouched):

- Given: shape "Catalog-Service" [AC] is in diagrams A and B
- When: the user presses DEL on the shape in diagram A
- Then: shape in diagram A gone; shape in diagram B unchanged; entity present in the explorer

**AC2** (Delete from repository — confirmation dialog):

- When: the user presses Ctrl+DEL on a shape
- Then: the confirmation dialog names the children, connections and active solutions; proceed only on confirmation

**AC3** (Delete from repository — soft delete with cascade):

- When: the user confirms
- Then: the entity, its children and their connections have `deleted_at` set and no longer appear in normal catalog queries; they are restorable with `entity:restore`

**AC4** (Delete from repository — other diagrams):

- When: an entity is deleted that appears as a shape in 3 further diagrams
- Then: the shapes are hidden in these diagrams and show again after a restore; no silent data loss

**AC5** (Context menu accessibility):

- When: the user right-clicks on a shape
- Then: the context menu appears with both options incl. shortcuts; DEL and Ctrl+DEL also work without the context menu

**AC6** (Undo Remove from Diagram):

- When: the user has removed a shape from the diagram via DEL
- Then: Ctrl+Z restores the shape (local undo, no API call)

## Dependencies

- Blocked by: REQ-145 (shape insert), ADR-019 (soft delete)
- Related: UC-14 (audit log), UC-15 (restore)
- Affects: all diagrams that reference the deleted entity

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | Requirements Engineer | Initial draft |
| 0.2.0 | 2026-11-20 | Fullstack Engineer | Realigned with the owner decisions of 2026-11-19 and 2026-11-20 (change s17-entity-delete): "Delete from repository" with cascade, repository instead of solution, shapes hidden and shown again after a restore |
