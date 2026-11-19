# Design

## Context

- **Change 5 (`us-056-kpi-and-chart-widgets`):**
  - `core.entity.EntityAggregationStore` aggregates the current state per group (count, sum, min, max) from typed arguments;
  - `app.dashboard.DashboardData` derives the functions and applies `PropertyAccess.Snapshot.readableCompletely`;
  - `app.dashboard.Dashboards` rejects `vs-previous-value` with 422, and rejects `comparison` on a chart (`rejectSet`).
- **History (V6, ADR-022), as checked in the code (`EntityStore`, `PropertyValueStore`, `business-objects/entity-version.md`):**
  - an `entity_property_value_history` row of version N holds the value **before** the change of N (all value columns null if there was none), with `changed_at` of N;
  - version 1 (`EntityStore.create`) writes the entity, its header and `entity_property_values`, but **no** history rows;
  - every later value write goes through `EntityStore.update` (form, batch upsert, go-live, full and partial restore) or `PropertyValueStore.removeValues`, and both write the old value of every touched property in the same transaction; `updateParent` writes only a system-field row;
  - `entities.created_at` and `changed_at` come from the `Clock` of the caller, not from the database default.
- **Deletion:** `EntityStore.softDelete` is the only deletion. It sets `deleted_at` once (`WHERE deleted_at IS NULL`), and no code clears it. The restore of V32 and V33 restores the values of an earlier version and does not undelete. Entities are not purged (REQ-190 is `proposed`). `deleted_at` is therefore the exact and final time of the deletion.
- **Indexes:** `entity_property_value_history (entity_id, entity_version)`; `entities_active (metatype_id) WHERE deleted_at IS NULL`.

## Goals / Non-Goals

**Goals:** as in the delta spec.

**Non-goals:**

- a reconstruction of the metamodel at that time;
- a configurable period;
- snapshots.

## Decisions

### D1 – Contract

- `comparison` accepts `vs-previous-value`.
- `WidgetData` gains `previousValue` (number or null) and `previousAt` (date-time, the point in time used); both are null for other modes.

### D2 – Historical aggregation (`EntityAggregationStore.aggregateAt`)

`aggregateAt(Collection<UUID> metatypeIds, UUID valuePropertyDefId, Instant at)` returns one `GroupRow` (key and name null) with count, sum, min and max, with the same meaning as `aggregate(…, Grouping.none())`: without a property, every entity is counted; with a property, only entities that had a value of it at `at`. No grouping (KPI only). Empty `metatypeIds` gives count 0 without a query.

**Entities existing at `at`:** `e.created_at <= at AND (e.deleted_at IS NULL OR e.deleted_at > at)`. This is exact, because a deletion is final (see Context): an entity deleted after `at` counts, one deleted before or exactly at `at` does not. A deleted entity is never restored, so no other source is needed.

**Value at `at`:** the old value of the first history row after `at`, else the current value:

```sql
FROM entities e
LEFT JOIN LATERAL (
    SELECT h.id, h.value_text, h.value_int, h.value_bool, h.value_date, h.value_ref, h.value_enum
    FROM entity_property_value_history h
    WHERE h.entity_id = e.id AND h.property_def_id = ? AND h.changed_at > ?
    ORDER BY h.entity_version LIMIT 1) h ON true
LEFT JOIN entity_property_values v ON v.entity_id = e.id AND v.property_def_id = ?
```

- The value columns are `CASE WHEN h.id IS NOT NULL THEN h.<column> ELSE v.<column> END`; an entity has a value when `num_nonnulls` of these columns is greater than 0, as in `aggregate`.
- Version 1 writes no history, so an entity unchanged since `at` correctly takes its current value.
- A change exactly at `at` has taken effect (`changed_at > at` selects only later changes), in line with creation and deletion at `at`.
- Assumption: `changed_at` does not decrease with the version of an entity (one `Clock`); the query orders by `entity_version`.

**Metatype:** the current one. `EntityStore` never changes `entities.metatype_id`, so this is exact.

**SQL:** fixed text in the store, IDs and `at` bound as parameters (ADR-051).

### D3 – Computation (`DashboardData`)

- **Point in time:** `at = clock.instant() - 30 days` (exactly 30 × 24 h).
- **Order of checks:** the same visibility check as for the current value, before any query; a restricted property queries nothing.
- **Values:** `previousValue` is derived with the same function as the current value.
  - Count over no entities gives 0.
  - Sum, avg, min and max without values give null, displayed as "No comparison value".
- **Configuration that no longer fits:** the same `invalid-configuration` as before.

### D4 – Index: none

No migration. The lateral lookup uses `entity_property_value_history (entity_id, entity_version)`: per entity it reads that entity's history rows in version order and filters by property and time, and an entity has few versions. The `entities` scan is the same as for the current value. REQ-055 sets no time budget, so no index is added on speculation; a measured problem is a later change.

### D5 – Frontend

**`KpiWidget.vue`** with `vs-previous-value`:

- the previous value with "30 days ago" (tooltip: the date of `previousAt`, formatted locally);
- the difference with its sign, and the percentage when the previous value is not 0;
- an arrow up, down or equal as icon plus text;
- with `previousValue` null, "No comparison value".

**`WidgetDialog.vue`:** the mode "Compared with 30 days ago" becomes selectable for KPI.

**Fakes and stories:** in `src/test/dashboards.ts` and the SCR-024 stories, increased, decreased, equal and no comparison value.

### D6 – Documentation

- **Manual:** "Dashboards" in English and German.
- **Requirements:** REQ-053 (comparison) and the implementation note of REQ-052 (which still says `vs-previous-value` answers 422) are realigned, and US-056 is set to done.
- **Sprint plan:** the S17 status.

### D7 – Security

- **Visibility:** the same rules for the historical aggregate as for the current value. A restricted property shows neither value, so the history cannot leak values that the reader may not read now.
- **SQL:** typed arguments and a bound timestamp only; no SQL text from `app`.
- **Boundary of the period:** an entity created exactly at `at` is counted, one created one second later is not. An entity deleted exactly at `at` is not counted, one deleted one second later is. A value changed exactly at `at` counts as the value at `at`. The clock is fixed in the tests.
- **Authorization:** unchanged from change 5.

## Risks / Trade-offs

- **Current metamodel:** comparisons are approximate after metatype changes. This is documented in the manual.
- **Undelete or purge later:** D2 relies on deletions being final and entities never purged. A future undelete or the purge of REQ-190 must record deletions over time (or keep this aggregation exact) in its own change.

## Migration Plan

None.

## Open Questions

None.
