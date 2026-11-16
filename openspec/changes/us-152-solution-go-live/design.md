# Design

## Context

- **Solutions (S10–S13):** solutions hold deltas in `entity_deltas` with their values in `entity_delta_property_values`.
  - A delta `new` carries metatype, name, description and after values, and no entity yet; the constraint `entity_deltas_new_shape` requires `entity_id IS NULL` for it.
  - A delta `modified` records, per field, the before value it saw and the after value.
  - A delta `retiring` names its entity.
- **Runtime role:** it may not update `solutions` or `entity_deltas.entity_id`.
- **Entity writes:** `EntityWriter`, which appends a version and writes `entity.created` or `entity.updated`. Soft deletion (ADR-019) has columns (`deleted_at`, `deleted_by`, `deletion_reason`), but nothing writes them yet.
- **Rule "Completed solution":** it already rejects delta changes in an `implemented` solution.

## Goals / Non-Goals

**Goals:**

- The go-live of a solution in project mode, by the owner decisions of 2026-11-16: preview, field conflicts, resolution, transaction, open issues and audit.
- The action and the merge dialog on SCR-061 and SCR-063.

**Non-Goals:**

- Blocking and resolving open issues (change 2).
- Plateaus (changes 3 and 4).
- Restoring deleted entities (#70).

## Architecture Constraints

- **Layers (ADR-028, ArchUnit):** `app/solution` may use `app/entity` (writer, validator, rules); `app/entity` does not use `app/solution`.
- **Contract first (ADR-013):** Redocly lint and both authorization scripts pass.
- **History:** stays append-only. The migration grants column updates only where the go-live needs them.

## Decisions

### D1 – Schema (`V35__solution_go_live.sql`)

- **`entity_deltas_new_shape`** is replaced: a delta `new` needs metatype and name and may have an entity once it went live; `modified` and `retiring` need an entity.
- **`GRANT UPDATE (entity_id) ON entity_deltas`** and **`GRANT UPDATE (status) ON solutions`** to `oea_app`.
- **`solution_open_issues`**, with these columns:
  - `id`;
  - `solution_id → solutions`;
  - `entity_id → entities`;
  - `field` (`name`, `description` or the property ID as text) and `property_def_id` (nullable);
  - `kind` (`mandatory-missing`, `invalid-value`);
  - `created_at`.

  The runtime role gets `SELECT` and `INSERT`. Change 2 adds the resolution columns.

*Alternative:* reuse `entity_mandatory_marks`. Rejected: it has no solution and no kind, and invalid values need the same list.

### D2 – Field conflicts (`app/solution/SolutionGoLive`)

By ADR-051 (merged after this proposal), `SolutionGoLive` runs no SQL itself: `core/solution/GoLiveStore` reads and writes solutions, deltas, fields and open issues, and `core/entity/MetatypeRuleStore` supplies the write rules (`MetatypeRules.load(MetatypeRuleStore, date)`), which also moves the SQL of the existing `MetatypeRules` into core.

For each delta `modified`, the current values of its entity are read (`EntityStore.values`, name and description), and each field the delta changes is compared with its recorded before value, as text (`TypedValue.asText`). A difference is a conflict `{deltaId, entityId, entityName, field, label, dataType, before, current, after}`.

Values the caller may not read are redacted as in `SolutionDeltas` (`readableFor` on the entity).

### D3 – Resolution

`resolveConflicts(principal, solutionId, deltaId, choices)`:

1. Lock the solution `FOR UPDATE`; a completed solution gives 409.
2. Recompute the conflicts of the delta. A choice for a field without a conflict gives 422 naming the field.
3. Per choice:
   - `current` deletes the field's row;
   - `delta` keeps the after value and sets the before value to the current one;
   - `value` converts the text with `EntityValidator.accepts` and the conversion of the field. An invalid own value gives 422 naming the field.
4. `updated_at`/`updated_by` of the delta are set, and the event `solution.delta-updated` is written as for an edit.

### D4 – Go-live transaction

`goLive(principal, solutionId)`, `@Transactional`:

1. **Lock and check:**
   - lock the solution `FOR UPDATE`;
   - `implemented` or `archived` gives 409 `solution-completed`;
   - lock the entities of all deltas `FOR UPDATE` in ID order;
   - recompute the conflicts; any conflict gives 409 `merge-required` with the list.
2. **Deltas `new`:** a `Checked` is built directly from the captured typed values. They are not converted from text, so a value the current definition rejects is still written where its column holds it. `EntityWriter.write` creates the entity, and `entity_deltas.entity_id` is set.
3. **Deltas `modified`:** the after values are merged into the current values; `EntityWriter.write` writes the next version.
4. **Deltas `retiring`:** `EntityStore.softDelete(entityId, person, now, reason)` sets the deletion columns, with the reason "Retired by solution {name}". The same happens to every active connection whose source or target is the entity, and each writes `entity.deleted`.
5. **Open issues:** for every created or changed entity:
   - every writable mandatory property without a value;
   - every captured value that `EntityValidator.accepts` rejects.

   Each is inserted into `solution_open_issues`, and empty mandatory properties are also marked (`PropertyValueStore.mark`).
6. **Status and audit:** the solution is set to `implemented`, and `solution.went-live` is written with `new`, `modified`, `retired` and `openIssues`.

A failure in any step rolls back everything.

The write roles of the confirming person are not checked again (owner decision); the `PropertyWriteAccess` check of `EntityBatch` is not used.

### D5 – Contract

| Operation | Path | Response |
|---|---|---|
| `previewSolutionGoLive` | `GET /solutions/{id}/go-live` | `SolutionGoLivePreview { counts, conflicts[], violations[] }` |
| `resolveSolutionDeltaConflicts` | `POST /solutions/{id}/deltas/{deltaId}/resolution` | body `{ fields: [{ field, choice, value? }] }`; returns the delta |
| `goLiveSolution` | `POST /solutions/{id}/go-live` | `SolutionGoLiveResult { counts, openIssues[] }`; 409 `merge-required` with `conflicts`; 409 `solution-completed` |

- All three need `solution:write`.
- `SolutionDetail` gains `openIssueCount`, and a `GET /solutions/{id}/open-issues` lists them with entity name, field label and kind.

### D6 – Frontend

- **`SolutionPage.vue`:**
  - "Go live" (holders of `solution:write`, status not completed) loads the preview;
  - with conflicts, it opens `DeltaMergeDialog.vue` (SCR-063);
  - otherwise it opens the confirmation (`UModal`: counts, notice "cannot be undone", "Go live").
- **SCR-063** lists the conflicts grouped by entity. Per field there is a radio choice (current, delta, own value with input), and "Apply" sends one resolution per delta and then reloads the preview.
- **After the go-live:** the status badge, the deltas without actions, and the section "Open issues" (read-only in this change).
- **Stories:** SCR-061 `GoLive`, `Implemented`; SCR-063 `Conflicts`.

### D7 – Tests

- **`SolutionGoLiveTests`:** counts, conflicts and the absence of a conflict, the three resolutions and the 422s, the effects of each delta type including connections, open issues of both kinds, 409s, 403, rollback with a failing writer, and the audit events.
- **`EntityStoreTests`:** `softDelete`.
- **The migration test of the new shape:** a delta `new` with an entity is accepted after V35; a `modified` delta without an entity is rejected.
- **Cucumber:** `features/solution/solution-go-live.feature` with the scenarios of the spec.
- **Frontend:** `SolutionPage.spec.ts` and `DeltaMergeDialog.spec.ts`.

## Data Model

- `solution_open_issues` (new).
- `entity_deltas.entity_id`, set for `new` after the go-live.
- `solutions.status` becomes `implemented`.

These go in `docs/data-model.puml` and `business-objects/solution.md`.

## Risks / Trade-offs

- **No new check of write roles:** a value captured by a writer with a write role goes live even when the confirming person lacks that role, or the writer lost it meanwhile (owner decision). The audit names the capture and the go-live persons.
- **Invalid values in the repository:** the go-live writes values the current definition rejects. Change 2 makes them visible and blocking until they are resolved.
- **Lock order:** locking the entities in ID order prevents deadlocks between two go-lives that share entities.

## Open Questions

- None.
