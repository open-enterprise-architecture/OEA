# Proposal

## Why

Entities cannot be deleted through the API or the screens: `deleteEntity` is planned. The canvas needs "Delete from repository" (owner decision 2026-11-19). [ADR-019](../../../adrs/ADR-019-soft-delete-strategy.md) fixes the soft delete with a cascade to connections and a cascade restore. Today only the go-live of a solution and the hierarchy delete entities, through `EntityDeletion`.

Issue [#70](https://github.com/open-enterprise-architecture/OEA/issues/70) waits for this change: the external ID of a deleted entity stays reserved.

This is S17 change 14 ([sprint plan](../../../docs/sprint-plan.md)). The screens to find and restore deleted entities follow in change 15 (`s17-deleted-entities`).

## Traceability

| Artifact | Reference |
|---|---|
| ADR | [ADR-019](../../../adrs/ADR-019-soft-delete-strategy.md) (soft delete, cascade, cascade restore) |
| Issue | [#70](https://github.com/open-enterprise-architecture/OEA/issues/70) (external IDs of deleted entities) |
| Use Case | [UC-10](../../../requirements/use-cases/UC-10-model-business-processes.md) |
| User Stories | [US-074](../../../requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md) ("Delete from repository" on the canvas), [US-141](../../../requirements/user-stories/US-141-entity-batch-import.md) (#70), [US-152](../../../requirements/user-stories/US-152-solution-go-live.md) and [US-157](../../../requirements/user-stories/US-157-plateau-solutions-go-live.md) (deletion conflicts) |
| Requirements | [REQ-146](../../../requirements/req/REQ-146-diagram-delete-vs-entity-delete.md) (realigned: "Delete from repository", repository instead of solution, shapes hidden and shown again after a restore), [REQ-159](../../../requirements/req/REQ-159-object-level-authorization.md) (deleted entities only with `entity:restore`) |
| ADRs | [ADR-032](../../../adrs/ADR-032-authorization-model.md) (`entity:delete`, `entity:restore`), [ADR-033](../../../adrs/ADR-033-audit-trail-hardening.md), [ADR-051](../../../adrs/ADR-051-backend-module-cut.md) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions:**

- **2026-11-19:** "Delete from repository" on the canvas is a soft delete with `entity:delete` and a confirmation.
- **2026-11-20:**
  - **#70:** a batch write of the external ID of a deleted entity restores it when the writer holds `entity:restore` and then updates it; otherwise the item is rejected.
  - **Children:** deleting an entity with children warns with the number of children on all levels and deletes them with it; they are restored together.
  - **Finding deleted entities:** on a page "Deleted entities" and through "Show deleted" in the catalog (change 15).
  - **Active solutions:** an entity with a delta `modified` or `retiring` in an active solution can be deleted after a warning naming the solutions. These deltas become conflicts that the solution resolves before its go-live.

**Decisions and assumptions of this change (open for review):**

- **Contract:**
  - `deleteEntity` (`DELETE /entities/{id}`, `entity:delete`, `If-Match`) is implemented with the cascade and answers 200 with the IDs of everything deleted, so that the canvas can remove it. The ADR-019 `cascade` and the planned `reason` query parameters are dropped: the cascade always applies, and no reason is asked for.
  - `previewEntityDeletion` (`GET /entities/{id}/deletion`, `entity:delete`) is new.
  - `restoreDeletedEntity` (`POST /entities/{id}/undelete`, `entity:restore`, `If-Match`) is new. The path `/entities/{id}/restore` is taken by the restore of a version.
- **Cascade:** children on all levels, and every connection with the entity or a child as endpoint, get the same `deleted_at` and `deleted_by`, the reason `cascade: entity <id> deleted` and, in a new column `deleted_with_entity_id` (V49), the entity whose deletion took them along.
- **Cascade restore:** the rows whose `deleted_with_entity_id` is the restored entity (or, for a child restored alone, its descendants and their connections from the same cascade) are restored. A connection whose other endpoint is still deleted, or an automatic composition connection whose child no longer has that parent, stays deleted and is named in the answer.
- **Restoring an element** places it at the top level, as a new version, when its parent is deleted or its subtree would end more than 10 levels below the root.
- **Not deletable through `deleteEntity`:** a structuring aid (its own operation moves the children to the top level) and an automatic composition connection (changing the parent removes it); both answer 422.
- **External IDs:** the unique index keeps covering deleted rows, so the external ID of a deleted entity stays reserved and a restore can never collide (#70: restore, not reuse). Only a batch `upsert` restores; `create-only` and `skip-existing` keep the existing item error `EXTERNAL_ID_DELETED`.
- **Blocking:** open issues on the entity or any part of its cascade block a deletion (409 `entity-blocked`, as for every write).
- **Scoping connections:** deleting or restoring a set that contains a connection of a type used as `scopingConnectionType` needs `visibility-scope:assign` (ADR-032, REQ-161), as writing one does.
- **Deltas on deleted entities** are deletion conflicts: shown in the delta list and the go-live previews of the solution and the plateau, and both go-lives answer the existing 409 `entity-deleted` until the delta is removed or the entity restored. A delta `retiring` of a deleted entity, skipped so far, becomes such a conflict too.
- **Audit:**
  - `entity.deleted` for the entity;
  - `entity.cascade-deleted` for each part of the cascade, with `triggeredByEntityId`;
  - `entity.undeleted` on restore, distinct from `entity.restored` of the version restore;
  - `entity.parent-changed` when a restore places an element at the top level.
- **Canvas:** "Delete from repository" (context menu and Ctrl+Delete, REQ-146) calls the preview, then deletes at once outside the explicit save with the version of the preview, and removes the deleted set's shapes and edges locally. Their stored shapes stay hidden and come back after a restore.

**Not in this change:**

- the page "Deleted entities" and "Show deleted" in the catalog (change 15);
- hard delete and purge;
- deleting from the catalog rows (only the canvas offers deletion; the page of change 15 offers only restore).

## Capabilities

### New Capabilities

- `repository/entity-delete`: the deletion preview, deleting with the cascade, and restoring a deleted entity with its cascade.

### Modified Capabilities

- `repository/entity-batch-write`: the external ID of a deleted entity (#70).
- `solution/solution-go-live`: deltas on deleted entities as conflicts.
- `solution/plateau-go-live`: the same conflicts in the plateau go-live.
- `diagram/diagram-canvas`: "Delete from repository".

## Impact

- **Contract:** `deleteEntity` (implemented), `previewEntityDeletion` and `restoreDeletedEntity`; `restored` and `EXTERNAL_ID_DELETED` in the batch with the condition `entity:restore`; the deletion conflicts in the delta list, both go-live previews and both go-lives (409 `entity-deleted`).
- **Backend:**
  - `app.entity` (deletion, preview and restore, using `EntityDeletion`);
  - `core.entity` stores (the cascade queries and the undelete);
  - the batch upsert;
  - the solution and plateau go-live, the previews and the delta list;
  - `EntityDeletion` gains the cascade origin; the go-live records it for the connections it retires with an entity;
  - migration V49: the column `deleted_with_entity_id` (`deletion_reason` exists since V6).
- **Frontend:**
  - "Delete from repository" with the preview dialog on the canvas;
  - the deletion conflicts on SCR-061 (delta list, go-live) and SCR-066 (plateau go-live);
  - stories and fakes.
- **Documentation:**
  - the manual "Diagrams", "Solutions" and the API (en/de);
  - ADR-019 (amendment of 2026-11-20 with the decisions);
  - the audit catalog;
  - `docs/data-model.puml`, the business object of the entity, SCREENS.md, US-074 and REQ-146;
  - the sprint plan;
  - #70 closed by the pull request.
