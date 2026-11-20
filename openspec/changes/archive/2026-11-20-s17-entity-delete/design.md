# Design

## Context

- **`EntityDeletion`** soft-deletes one entity and audits `entity.deleted`. The go-live (a delta `retiring` and the connections of its entity, each with the reason "Retired by the solution …") and the hierarchy (the automatic composition connection of a previous parent, a structuring aid) use it.
- **`entities`** has `deleted_at`, `deleted_by` and `deletion_reason` (V6) and the hierarchy column `parent_entity_id` (at most 10 levels). Nothing records which deletion took a row along. Connections are entities with source and target in `entity_connections`.
- **`restoreEntity`** restores a version (`/entities/{id}/restore`, `entity:restore`) and never undeletes; its audit event is `entity.restored`.
- **The batch** answers an item whose external ID belongs to a deleted entity with the item error `EXTERNAL_ID_DELETED` (entities and connections). The unique index `entities_external_id` covers deleted rows (#70).
- **The go-live** of a solution and of a plateau answers 409 `entity-deleted` (extension member `deltaIds`) for a delta `modified` whose entity is deleted, and skips a delta `retiring` of a deleted entity.
- **Solutions** hold deltas `modified` and `retiring` on entities; capturing one for a deleted entity is rejected.
- **Shapes and edges** of deleted entities are already kept and hidden (changes 11 and 13); the save keeps them.

## Goals / Non-Goals

**Goals:**

- Delete with cascade and preview.
- Cascade restore that identifies its rows exactly.
- #70.
- Deletion conflicts in solutions and plateaus.
- "Delete from repository" on the canvas.

**Non-Goals:**

- The pages to find deleted entities (change 15).
- Purge (REQ-190).
- Changing the history reads: deleting and restoring write no versions (see D3).

## Architecture Constraints

- **ADR-019:** soft delete, cascade in the service layer, cascade restore. This change refines it (D7): the cascade origin column replaces the identification by time and actor, the events are `entity.deleted` and `entity.undeleted`, and the path is `/undelete`.
- **ADR-051:** the cascade queries live in `core.entity` stores. `app.entity` holds the rules; `app.solution` reads the deletion conflicts through its stores; there are no cycles.
- **ADR-032:**
  - `entity:delete` for the preview and the delete;
  - `entity:restore` for the restore and for the batch restore (REQ-159: deleted entities only with `entity:restore`);
  - `visibility-scope:assign` when the deleted or restored set holds a connection of a scoping type (§ "Creating, changing or deleting such connections", REQ-161);
  - the preview carries counts and the name and version of the entity only; solution names are shown as in the delta conflicts.
- **ADR-033:** an event per deleted or restored entity, written in the same transaction.

## Decisions

### D1 Contract

| Operation | Path | Permission | Answers |
|---|---|---|---|
| `previewEntityDeletion` | `GET /entities/{id}/deletion` | `entity:delete` | 200 `EntityDeletionPreview`: `entity` (id, name, version), `childCount`, `connectionCount`, `solutions` (id, name, `deltaCount`); 404 unknown or deleted; 422 structuring aid or automatic composition connection |
| `deleteEntity` | `DELETE /entities/{id}` | `entity:delete`; condition `visibility-scope:assign` | `If-Match`; 200 `EntityDeletionResult` with `deleted` (the entity first, then children and connections, IDs); 403, 404, 409 `entity-blocked`, 412, 422, 428 |
| `restoreDeletedEntity` | `POST /entities/{id}/undelete` | `entity:restore`; condition `visibility-scope:assign` | `If-Match` with the version of the deleted entity; 200 `EntityUndeleteResult` with `restored` (IDs), `movedToTopLevel` (IDs) and `notRestored` (connection ID and `reason` `endpoint-deleted` or `parent-changed`); 403, 404, 409 `entity-not-deleted`, 409 `connection-not-restorable` (a connection restored directly whose endpoint is deleted, with `reason`), 412, 428 |

- `deleteEntity` loses the `planned` marker and its `reason` query parameter; the ADR-019 `cascade` parameter is not part of the contract.
- **Batch:** `BatchWriteItemResult` gets `restored` (boolean); the description names `EXTERNAL_ID_DELETED`; `x-oea-authorization` of `batchWriteEntities` gets the condition `{"when": "an upsert item names the external ID of a deleted entity", "permissions": ["entity:restore"]}` – handler-enforced as an item error, not a 403 (D4).
- **Solutions:** `SolutionDelta` gets `entityDeleted` (boolean; `false` for `new`). `SolutionGoLivePreview` and `PlateauGoLiveSolution` get `deletionConflicts` (`deltaId`, `deltaType`, `entityId`, `entityName`). The 409 `entity-deleted` of `goLiveSolution` and `goLivePlateau` covers deltas `modified` and `retiring` (description only; the problem type and `deltaIds` exist).
- The new operations use the shared `EntityIdParam` and the shared `If-Match`, 412 and 428 components; `previewEntityDeletion` has no request body, so `check_openapi_conventions.py` needs no allowlist entry.

### D2 Deleting

In one transaction:

1. Lock the entity; 404 if it is unknown or deleted; 422 for a structuring aid or an automatic composition connection (`autoCreated`); check `If-Match` (428, 412).
2. Collect the children on all levels (a recursive query in `core.entity`, at most 10 levels) and lock them in ascending ID order.
3. Collect the active connections with an endpoint in that set.
4. Check `visibility-scope:assign` if a connection of the set has a scoping type (403), then the open-issue block of the whole set (409 `entity-blocked` naming the blocked entities).
5. Soft-delete all with one `Instant` and actor; for the cascade the reason `cascade: entity <id> deleted` and `deleted_with_entity_id = <id>`, for the entity itself `deleted_with_entity_id = null`.
6. Audit `entity.deleted` for the entity (details `metatypeId`, `version`, `childCount`, `connectionCount`), and `entity.cascade-deleted` (details `triggeredByEntityId`, `metatypeId`, `version`) for each child and connection.

- A connection entity is deleted alone (no cascade).
- Automatic composition connections between the entity and its children are part of the connection cascade.
- **`EntityDeletion`** takes the cascade origin as an optional parameter. The hierarchy passes none. The go-live passes the retired entity for the connections it retires with it, so that restoring a retired entity restores them; its audit stays `entity.deleted` with `solutionId`.
- The preview runs steps 1–3 without locking and without `If-Match` and counts the active solutions (not `implemented` or `archived`) with deltas `modified` or `retiring` on any entity of the set.
- A reference value of another entity that points to the deleted entity stays; writing it again is rejected as today.

### D3 Restoring

In one transaction:

1. Lock the entity; 404 if unknown; 409 `entity-not-deleted` if active; check `If-Match` (428, 412).
2. **The set:**
   - the entity itself;
   - its descendants that are deleted with the same `deleted_at` and the same origin as the entity's own cascade – that is, rows with `deleted_with_entity_id` equal to the entity (when it is the origin) or equal to the entity's origin (when it is a child restored alone), reached through `parent_entity_id` from the entity;
   - the connections with `deleted_with_entity_id` equal to that origin and the same `deleted_at`, with an endpoint in the set.
   Rows deleted earlier or by another deletion are never part of it, even in the same instant.
3. A connection restored directly needs both endpoints active (409 `connection-not-restorable`).
4. Check `visibility-scope:assign` for connections of a scoping type in the set (403).
5. Restore the elements (clear `deleted_at`, `deleted_by`, `deletion_reason`, `deleted_with_entity_id`). The entity keeps its parent unless the parent is deleted or the subtree would end more than 10 levels below the root; then `parent_entity_id = null` as a new version with the reason "Parent deleted" or "Hierarchy too deep", audited as `entity.parent-changed`, and the entity is listed in `movedToTopLevel`.
6. Restore the set's connections whose two endpoints are active afterwards. An automatic composition connection is restored only if its target still has its source as parent. The others go to `notRestored` with the reason `endpoint-deleted` or `parent-changed`.
7. Audit `entity.undeleted` for each restored entity (details `metatypeId`, `version`, `triggeredByEntityId` for the rows restored with it).

- **Versions** do not change, except for the parent reset of step 5.
- **History reads** stay as they are: deleting and restoring write no versions; the deletion and the restore are found in the audit trail. Showing the deletion state belongs to change 15.
- **External ID index:** unchanged. It covers deleted rows, so no active entity can hold the external ID of a deleted one, and the restore needs no external ID check (#70 answered by restore).
- **Version restore of a deleted entity:** `restoreEntity` and `previewEntityRestore` answer 409 `entity-deleted` for a deleted entity, so no version is written while the entity stays deleted; the entity is undeleted first with `restoreDeletedEntity`. This closes the gap without a new open point.

### D4 Batch and #70

For an item with strategy `upsert` whose external ID belongs to a deleted entity:

- **With `entity:restore`:** the item restores that entity through D3, in the item's transaction (the batch transaction when atomic), and then applies the update. The item result has `restored: true`; the audit has `entity.undeleted` with the `batchId`, then `entity.updated` if a value changed. A connection item is restored only with active endpoints matching its own (otherwise the existing endpoint codes).
- **Without it:** the item keeps the existing error `EXTERNAL_ID_DELETED`. An atomic batch rejects everything; a non-atomic batch rejects the item.
- **`create-only` and `skip-existing`** keep `EXTERNAL_ID_DELETED`; they never restore.
- A restored set with a scoping connection needs `visibility-scope:assign`, like every scoping connection in a batch (403 for the whole batch).

### D5 Solutions and plateaus

- **Deletion conflicts** are computed on read: a delta `modified` or `retiring` whose entity is deleted in the repository. They appear:
  - in `listSolutionDeltas` (`entityDeleted: true`);
  - in the go-live preview of the solution and in each solution of the plateau go-live preview (`deletionConflicts`);
  - on SCR-061 (the changes and the go-live notice) and SCR-066.
- **The go-live** of a solution or a plateau rejects with the existing 409 `entity-deleted` and `deltaIds` before any write. New: a delta `retiring` of a deleted entity, skipped so far, is such a conflict too (owner decision 2026-11-20).
- In a plateau, an entity retired by an earlier solution of the same plateau keeps today's handling (a later `modified` gives `entity-deleted`, a later `retiring` is skipped).
- **Removing the delta**, which is already possible, or restoring the entity resolves the conflict.
- A delta `new` is not affected.

### D6 Canvas

- The context menus of shapes and edges show "Delete from repository" for holders of `entity:delete`; Ctrl+Delete opens the same confirmation (REQ-146). It is not offered for shapes of new, unsaved entities or for unsaved connections.
- The editor reads `previewEntityDeletion` and shows a confirmation dialog with the name, the counts and the solution names; a 422 of the preview shows its message instead.
- **On confirm:** `deleteEntity` with the version from the preview. On 412 the dialog says the entity was changed and offers to read the preview again; 409 `entity-blocked` and 403 show their message.
- **After the delete:** the shapes and edges of every ID in `deleted` are removed from the local state; unsaved connections with an endpoint among them and unsaved edits of those entities are discarded; the other unsaved changes stay and the next save does not send the removed ones. The stored shapes and edges remain hidden server-side.

### D7 Documentation

- **ADR-019:** an amendment of 2026-11-20 with the owner decisions, the cascade origin column, the events `entity.deleted`, `entity.cascade-deleted` and `entity.undeleted`, the `undelete` path, the dropped `cascade` parameter and #70.
- **The manual (en/de):** "Diagrams" (Delete from repository) and "Solutions" and "Plateaus" (deletion conflicts); the API reference is generated.
- **The audit catalog:** `entity.deleted` (direct deletion), `entity.cascade-deleted` and `entity.undeleted`.
- **`docs/data-model.puml` and the business object of the entity:** the column and the lifecycle with the cascade.
- **SCREENS.md:** SCR-021, SCR-061 and SCR-066.
- **US-074 and REQ-146** realigned; the sprint plan.
- **#70:** closed by the pull request.

## Data Model

Migration **V49** `entity_deletion_origin`:

- `entities.deleted_with_entity_id INTEGER REFERENCES entities (id)`, null for an entity deleted on its own or active;
- `CHECK (deleted_with_entity_id IS NULL OR deleted_at IS NOT NULL)`;
- a partial index `entities_deleted_with` on `(deleted_with_entity_id)` where it is not null.

Existing deleted rows keep `null`: their restore restores the entity alone, plus nothing else. `docs/data-model.puml` and the business object of the entity get the column and the lifecycle (active → deleted, alone or with a cascade → restored).

## API

D1.

## Risks / Trade-offs

- **[Risk]** A large cascade (deep hierarchy with many connections) in one transaction.
  - **Mitigation:** a bounded depth of 10, and set-based updates in the store.
- **[Risk]** Two deletions in the same instant by the same actor.
  - **Mitigation:** the cascade is identified by its origin column, not by time and actor alone.
- **[Trade-off]** Deletion conflicts block the go-live: the solution must decide explicitly, as the owner wants. This changes the go-live for a delta `retiring` of an already deleted entity, which was skipped.
- **[Trade-off]** A restored element whose parent stays deleted lands at the top level; moving it back is a normal parent change.
