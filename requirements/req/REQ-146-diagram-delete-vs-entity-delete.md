---
id: REQ-146
title: Delete from Diagram (DEL) vs. Delete Entity (Ctrl+DEL) via Context Menu
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
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

# REQ-146: Delete from Diagram (DEL) vs. Delete Entity (Ctrl+DEL) via Context Menu

## Statement

The system MUST offer two semantically different delete operations in the diagram editor:

1. **"Remove from Diagram" (DEL)**: Removes the shape from the current diagram. The referenced entity in the solution remains unchanged. Other diagrams that reference the same entity are not affected.

2. **"Delete Entity" (Ctrl+DEL)**: Deletes the entity from the solution using soft delete (ADR-019). The shape is removed from the current diagram. All other diagrams that reference this entity MUST mark the shape as "deleted" or remove it automatically (configurable). The entity is restorable via the audit log (UC-15).

Both operations MUST be available in the context menu (right-click on a shape) as well as as keyboard shortcuts.

## Rationale

The diagram is a view of the model — not a standalone data source. An architecture element exists in the model before it is made visible in diagrams. This separation must also be communicated clearly when deleting. An erroneous "Delete Entity" can affect many diagrams and is reversible through soft delete (ADR-019) — but the user must confirm explicitly.

## Context

| Operation | Shortcut | Scope | Entity in solution | Other diagrams |
|---|---|---|---|---|
| Remove from Diagram | DEL | this diagram only | unchanged | untouched |
| Delete Entity | Ctrl+DEL | solution-wide | soft-deleted | shape marked as "deleted" |

Soft delete means: `deleted_at`, `deleted_by`, `deletionReason` set (ADR-019); the entity remains in the DB and does not appear in normal queries.

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Description |
|---|---|---|
| shapeId | uuid | selected shape in the diagram |
| operation | "remove-from-diagram" \| "delete-entity" | chosen delete operation |

**Processing — Remove from Diagram**:

1. User presses DEL or selects "Remove from Diagram" in the context menu
2. No confirmation dialog (non-destructive for the model)
3. `DELETE /api/v1/diagrams/{diagramId}/shapes/{shapeId}`
4. Shape disappears from the canvas immediately
5. Log: "[INFO] Shape removed from diagram: <Name> — entity preserved"

**Processing — Delete Entity**:

1. User presses Ctrl+DEL or selects "Delete Entity" in the context menu
2. System shows a confirmation dialog: "Delete entity '<Name>' from the solution? This affects all diagrams referencing this entity. The entity can be restored via Audit Log."
3. User confirms → `DELETE /api/v1/entities/{entityId}` (soft delete per ADR-019)
4. Shape is removed from the current diagram
5. All other diagrams: shape is marked as `status: deleted` (gray, strikethrough) or removed — depending on user/system setting
6. Log: "[WARN] Entity deleted: <Name> (<Type>) — soft-deleted, recoverable via Audit Log"

**Context menu structure** (on right-click of a shape):

```text
Edit Properties
Add Connection
────────────────────
Remove from Diagram    DEL
Delete Entity          Ctrl+DEL
```

**Outputs**:

- For "Remove": shape no longer on the canvas; entity still visible in catalog/explorer
- For "Delete Entity": entity marked as "deleted" in the catalog; shape removed from or marked in all diagrams

## Acceptance Criteria

**AC1** (Remove from Diagram — model untouched):

- Given: shape "Catalog-Service" [AC] is in diagrams A and B
- When: the user presses DEL on the shape in diagram A
- Then: shape in diagram A gone; shape in diagram B unchanged; entity present in the explorer

**AC2** (Delete Entity — confirmation dialog):

- When: the user presses Ctrl+DEL on a shape
- Then: confirmation dialog appears with the warning "affects all diagrams"; proceed only on confirmation

**AC3** (Delete Entity — soft delete):

- When: the user confirms Delete Entity
- Then: the entity has `deleted_at` set; no longer appears in normal catalog queries; is restorable via the audit log (UC-15)

**AC4** (Delete Entity — other diagrams marked):

- When: an entity is deleted that appears as a shape in 3 further diagrams
- Then: in these 3 diagrams the shape is marked as "deleted" (gray, warning); no silent data loss

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
