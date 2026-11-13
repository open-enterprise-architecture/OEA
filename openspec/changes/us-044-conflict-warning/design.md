# Design

## Context

See proposal.md – Why. The current state after change 4 (`us-043-diff-view`, #250):

- **Tables:**
  - `entity_deltas` (V26) holds the deltas with `solution_id`, `delta_type` and `entity_id` (null for `new`). Its indexes cover `solution_id` and the unique pair `(solution_id, entity_id)`; there is none on `entity_id` alone.
  - `solutions` (V25) has `status` (`draft` … `archived`) and `owner_id` → `persons`.
  - No operation changes the status of a solution yet; it stays `draft` until S12–S14.
- **`SolutionDeltas`:** pages the deltas by cursor and returns `Page(items, …, counts, nextCursor)`. Its `read(solutionId, where, args, limit, access)` serves `list`, `create` and `update`.
- **`SolutionDiff.read`:** builds the diff entries from a page of `SolutionDeltas`.
- **`SolutionLandscape`:** pages the baseline with a `LEFT JOIN entity_deltas` for `deltaType`. `entry` reads one entity.
- **Metatype settings:**
  - the re-import setting is a column `metatypes.reimport_organization_mappings`, set by `ReimportPolicies.updateMetatype` for any scope;
  - it is also possible in `import-only`, and a re-import keeps it;
  - each change raises the metatype version (`MetamodelVersions`) and is audited through `MetamodelAudit`;
  - `MetatypeCatalog` reads the column into `MetatypeDetail`.
- **Frontend:**
  - `SolutionPage.vue` holds the areas `SolutionDeltas.vue` and `SolutionBaseline.vue`; `SolutionDiffPage.vue` is SCR-062;
  - `MetatypeDetail.vue` shows the re-import setting, editable with `canConfigure`.

## Goals / Non-Goals

**Goals:**

- Conflicts computed on every read, so they appear and disappear without a stored flag.
- One extra query per page, not one per delta.
- The baseline marking per metatype, off by default, so that an instance sees no change until an administrator turns it on.

**Non-Goals:**

- No status transitions of solutions; the go-live is S14.
- No conflicts of `new` deltas.

## Architecture Constraints

- **Layers (ADR-028):** the app services in `app/solution` and `app/metamodel` read with `JdbcTemplate`; the controllers in `api/` map to the generated types.
- **Authorization (ADR-032):**
  - reading conflicts needs `entity:read`, as the deltas, the diff and the baseline do;
  - every holder of `entity:read` already lists all solutions with their owners (`listSolutions`), so a conflict reveals nothing new;
  - the setting needs `metamodel:write`, as the re-import setting does.
- **ADR-049:** a re-import keeps settings an administrator made.

## Decisions

### D0 – Conflict query (`SolutionConflicts`)

A new app service `SolutionConflicts` in `app/solution` reads, for a solution and a set of entity IDs, the conflicts per entity in one query:

```sql
SELECT d.entity_id, s.id, s.name, s.status, s.owner_id, p.name, d.delta_type
FROM entity_deltas d
JOIN solutions s ON s.id = d.solution_id
JOIN persons p ON p.id = s.owner_id
WHERE d.entity_id = ANY(?) AND d.solution_id <> ?
  AND s.status NOT IN ('implemented', 'archived')
ORDER BY d.entity_id, lower(s.name), s.id
```

- It returns `Map<Integer, List<Conflict>>`, where `Conflict(solutionId, solutionName, status, ownerId, ownerName, deltaType)`.
- `entity_id = ANY(?)` is null for `new` deltas, so they never match.
- **`conflicted`:** one count per solution:

  ```sql
  SELECT count(*) FROM entity_deltas d
  WHERE d.solution_id = ? AND d.entity_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM entity_deltas o JOIN solutions s ON s.id = o.solution_id
                WHERE o.entity_id = d.entity_id AND o.solution_id <> d.solution_id
                  AND s.status NOT IN ('implemented', 'archived'))
  ```

- **Index:** migration V28 adds `entity_deltas_entity ON entity_deltas (entity_id) WHERE entity_id IS NOT NULL`.
- **Alternative considered:** a stored conflict flag on the delta. Rejected: it would need an update on every status change and every delta write of any other solution (US-044 AC4).

### D1 – Attaching the conflicts

- **`SolutionDeltas`:**
  - `Delta` gains `List<Conflict> conflicts`;
  - `read` collects the entity IDs of the page and attaches the conflicts from D0, so `list`, `create` and `update` all carry them;
  - `Page` gains `long conflicted`.
- **`SolutionDiff`:** `Entry` gains the conflicts of its delta, from the delta page it already reads.
- **`SolutionLandscape`:**
  - `Entry` gains `List<Conflict> parallelChanges`;
  - the page query additionally reads `m.show_parallel_changes`;
  - the service reads the conflicts for the entities of the page whose metatype has the setting on, and the other entries get an empty list;
  - `entry` does the same for its one entity.
- **Writes:** the conflicts are read inside the transaction of the write, after the insert or update. A conflict never raises an exception, so the write is never blocked. REQ-042's separate error case is dropped (realigned).

### D2 – Contract

**`api/openapi.yaml`, solutions:**

```yaml
SolutionDeltaConflict:
  type: object
  required: [solutionId, solutionName, status, ownerId, ownerName, deltaType]
  description: Another active solution that changes the same entity (US-044)
  properties:
    solutionId:   { type: string, format: uuid }
    solutionName: { type: string }
    status:       { $ref: '#/components/schemas/SolutionStatus' }
    ownerId:      { type: integer, format: int64 }
    ownerName:    { type: string }
    deltaType:    { $ref: '#/components/schemas/SolutionDeltaType' }
```

- **`SolutionDelta`, `SolutionDiffEntry`:** required `conflicts` (array of `SolutionDeltaConflict`; empty for `new`).
- **`SolutionDeltaList`:** required `conflicted` (integer ≥ 0).
- **`SolutionLandscapeEntry`:** required `parallelChanges` (array; empty when the setting of the metatype is off).
- No new operation and no new parameter on the solution side.

**`api/openapi.yaml`, metamodel:**

- **`MetatypeDetail`:** `showParallelChanges` (boolean, read-only).
- **New operation `PUT /metamodel/metatypes/{id}/parallel-changes`** (`updateMetatypeParallelChanges`):
  - permission `metamodel:write`, header `If-Match`;
  - body `MetatypeParallelChanges { showInBaseline: boolean }` (required);
  - answers 200 with `MetatypeDetail` and an `ETag`;
  - errors: 400, 401, 403, 404, 412, 422 and 429, as `updateMetatypeReimportPolicy`.

### D3 – Metatype setting (`app/metamodel`)

- **Migration V28:** `ALTER TABLE metatypes ADD COLUMN show_parallel_changes BOOLEAN NOT NULL DEFAULT false`. Existing, created and imported metatypes start off.
- **`ParallelChangeSettings.update(principal, metatypeId, value, expectedVersion)`**, following `ReimportPolicies.updateMetatype`:
  - `effects.lock()`, then `versions.check(METATYPE, …)`;
  - the update, then `versions.increment`;
  - `audit.changed("metamodel.metatype-parallel-changes-changed", …, {before, after})`.
- **Lock mode:** it does not call the lock-mode guard, so it works in `import-only`.
- **Re-import:** the package importer does not write the column, so a re-import keeps it. A test covers this.
- **Change history:** the event matches `metamodel.metatype-%`, so the kind `metatype` lists it without a change to `MetamodelChanges`. The audit event catalog documents it.
- **Inheritance:** none. Inheriting would need a three-valued setting as the re-import setting has; the owner asked for a switch (owner decision 2026-11-13).

### D4 – Screens

- **SCR-061, `SolutionDeltas.vue`:**
  - an entry with conflicts gets a warning line per conflict: a `triangle-alert` icon and the i18n text `solutions.conflicts.entry`;
  - the solution name is a `RouterLink` to `/solutions/{id}`.
- **SCR-061, `SolutionPage.vue`:**
  - a `UAlert` (color `warning`, icon) under the header when `conflicted > 0`, with plural forms;
  - after a capture or an edit with conflicts, a `useToast` warning naming the solutions;
  - the list then reloads as before.
- **SCR-061, `SolutionBaseline.vue`:** a badge "Changed in {n} other solutions" with an icon in the name cell, and the names in a tooltip. The names also go into the accessible label, so the tooltip is not the only way to read them.
- **SCR-062, `SolutionDiffPage.vue`:** the same warning lines in the card header area, from one shared component `components/solutions/SolutionConflicts.vue`.
- **SCR-030, `MetatypeDetail.vue`:**
  - a section "Show parallel changes in the baseline" with a `USwitch` and a hint, shown when `canConfigure ?? canWrite`, otherwise as text;
  - the `ETag` collision handling follows the re-import setting.
- Colors are never the only signal.
- **Stories:**
  - SCR-061 `Conflicts` (entry, header and baseline marker);
  - SCR-062 `Conflicts`;
  - SCR-030 with the switch in the existing metatype story.

### D5 – Tests

- **Backend:**
  - `SolutionConflictsTests` covers the conflict rules: every active status, `implemented` and `archived`, `new`, own solution, other entity, order, owner and delta type;
  - `SolutionDeltasTests`, `SolutionDiffTests` and `SolutionLandscapeTests` cover the attachment and `conflicted`;
  - `MetamodelEditingTests` (or a new `ParallelChangeSettingsTests`) covers the setting;
  - Cucumber: `solution-conflicts.feature`, and scenarios in the metamodel features.
- **Status:** a test helper sets `solutions.status` directly, since no operation changes it yet.
- **Frontend:** specs and stories per screen.

## Risks / Trade-offs

- **[Query per page]:** each delta, diff and baseline page runs one more query, and the delta list also runs the count of `conflicted`. → Both use the new index on `entity_id`. The counts are small: one solution against the deltas of the other active solutions on the same entities.
- **[Status set only in tests]:** until S12–S14, every solution is `draft` in practice, so every overlap is a conflict. → This is the intended behavior. The status rule is tested through the helper.
- **[Owner name changes]:** the conflict reads the current person name, not a stored copy. → This is wanted.
- **[Baseline marking off by default]:** nobody sees the baseline marking until an administrator turns it on. → The decision is the owner's. The manual explains the setting.

## Migration Plan

- V28 adds the column with a default and the partial index; it changes no rows and needs no rollback logic beyond dropping both.
- No backfill: off is the intended default for existing metatypes.

## Data Model

- **`docs/data-model.puml`:** `Metatype.showParallelChanges : Boolean = false`, and a note that conflicts are computed, not stored.
- **`business-objects/metamodel-configuration.md`:** the attribute on the metatype.
- **`business-objects/solution.md`:** BR-05 says how a conflict is defined (owner decision 2026-11-13).
- No change to `entity_deltas` beyond the index.

## Open Questions

None. The owner decided what counts as a conflict, where the warnings show, and how the setting works (scope, default and effect) on 2026-11-13.
