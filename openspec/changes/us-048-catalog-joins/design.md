# Design

## Context

- **Storage.** `catalogs.config` (JSONB, V7) holds `joinDefinitions`, which is still empty. The contract types it as `CatalogJoinDefinition`, which is still `readOnly` in `Catalog` (#64).
- **Connections.** A connection is an entity of a connection metatype with a row in `entity_connections` (V19): `source_entity_id` and `target_entity_id`, indexed on both. The allowed endpoints are in `metatype_connection_constraints`. `AllowedConnections.between(source, target)` decides which connection metatypes may connect two metatypes, in which direction. An allowed endpoint also allows its subtypes, and a direction without constraints allows every metatype (ADR-010, design D5 of us-036-connection-metatypes).
- **Query.** `CatalogQueries` resolves the configured or default columns and the sort. `CatalogQueryStore.page` reads one page of active entities of the primary metatype, with `LIMIT`/`OFFSET`, and `count` counts them.
- **Columns.** `Catalogs.updateColumns` validates `CatalogColumnConfig` entries (US-047) against the properties available for a metatype. The same rules apply to the target columns of a join.
- **Permissions.** `Catalogs.updateColumns` applies the rule of design D3 of us-047-configure-columns:
  - without `catalog:manage`, 403 for everything except one's own personal catalog;
  - with it, 404 for an unknown catalog or another person's personal catalog.
- **SCR-020.** `CatalogPage.vue` keeps `page` and `sort` in the URL (ADR-044). It has the dialog "Configure columns".

## Goals / Non-Goals

**Goals:**

- Add, edit and remove joins through the API and in SCR-020.
- Return join results in the query, in both modes, with a runtime override per join.
- Paging and sorting in rows mode.

**Non-Goals:**

- Filters on join attributes, saved views, and connection metatypes as primary metatype (later S9 changes).
- More than one traversal level (ADR-004).
- Optimistic locking of catalogs.

## Decisions

### D1 – Contract

**New operations**, each answering the changed `Catalog`:

| Operation | Method and path | Success |
|---|---|---|
| `addCatalogJoin` | `POST /catalogs/{id}/joins` | 201 |
| `updateCatalogJoin` | `PUT /catalogs/{id}/joins/{joinId}` | 200 |
| `removeCatalogJoin` | `DELETE /catalogs/{id}/joins/{joinId}` | 200 |

- **Authorization:** `x-oea-authorization: permissions [entity:read]` with the condition "the catalog has scope `instance`" → `catalog:manage`. The handler enforces it, with `@ConditionsEnforcedByHandler`, the role `viewer` and `probePath` on the nil UUID, as for `updateCatalogColumns`.
- **Errors:**
  - 422 with `invalidFields` such as `connectionMetatypeId`, `targetMetatypeId` or `targetColumns[1].propertyDefId`;
  - 403;
  - 404 for an unknown join ID.

**`CatalogJoinInput`:**

- `name`: 1–100 characters after trimming;
- `connectionMetatypeId`, `joinDirection` (`outbound`, `inbound`, `both`) and `targetMetatypeId`;
- `targetColumns`: 1–20 entries of `CatalogColumnConfig`;
- `defaultJoinMode`: `rows`, `aggregate` or `null` (use the catalog's).

**`CatalogJoinDefinition`** is `CatalogJoinInput` plus `id` and the read-only `available` (D4). `aggregateLabel` is dropped: the join name is the header of its columns.

**Query additions:**

- **Parameter `joinMode`** of `queryCatalog`: `style: deepObject`, `explode: true`, an object whose keys are join IDs and whose values are `JoinMode`, so `joinMode[<joinId>]=rows`. If the generated interface does not bind it, the controller reads the `joinMode[...]` parameters from the request.
- **`CatalogQueryResult.joins`:** `CatalogQueryJoin[]` with `id`, `name`, the applied `mode` and `columns` (`CatalogColumn[]`). Only joins that are available appear.
- **`CatalogRow.joinResults`:** an object keyed by join ID. Each value is an array of `CatalogJoinResult`:
  - `entityId`, and `values` keyed like the target columns;
  - all results in aggregate mode;
  - at most one in rows mode (an empty array for a primary entity without results).
- **`CatalogQueryProblem.invalidFields`** gains `joinMode`.

### D2 – Validation and storage

`Catalogs.addJoin` and `Catalogs.updateJoin` validate in this order:

1. **Name** is within the length limit.
2. **Connection metatype** exists in the instance configuration and has `isConnection = true` (BR-02), else 422 `connectionMetatypeId`.
3. **Target metatype** exists in the instance configuration and is reachable (BR-03), else 422 `targetMetatypeId`. Reachability uses `AllowedConnections.between(primary, target)` and needs an option for the connection metatype:
   - `outbound`: direction `SOURCE_TO_TARGET` or `BIDIRECTIONAL`;
   - `inbound`: `TARGET_TO_SOURCE` or `BIDIRECTIONAL`;
   - `both`: any direction.
4. **Target columns** follow the rules of US-047 (D2 of us-047-configure-columns) against the properties available for the target metatype:
   - a field or a mapped `public` property;
   - no duplicates;
   - labels within the limit;
   - at least one visible column.
   `defaultSortDirection` is rejected on target columns (422): the default sort stays on the primary columns.
5. **At most 10 joins** per catalog, checked by `addJoin`.

**Storage.** The join is stored in `config.joinDefinitions` with a generated UUID. `addJoin` appends it, so the order is the creation order. `updateJoin` replaces the entry and keeps its ID.

**Removal.** `removeJoin` deletes the entry and removes the `joinModeOverrides` with its ID from every entry of `config.savedViews`. Saved views stay empty until US-053, but the cleanup belongs to the removal (US-048 AC6).

**Audit and permissions.** Every change sets `last_modified_*` and writes `catalog.join-added`, `catalog.join-changed` or `catalog.join-removed`, with the join ID and name, in the same transaction. Permissions follow D3 of us-047-configure-columns.

### D3 – Traversal

A join of a primary entity `p` finds its target entities `t` through active connections `c` of the connection metatype:

| Direction | Condition |
|---|---|
| `outbound` | `c.source = p` and `c.target = t` |
| `inbound` | `c.target = p` and `c.source = t` |
| `both` | either, with distinct `t` |

- `c` and `t` must be active (`deleted_at IS NULL`), and `t` must have the target metatype exactly, as the primary rows have exactly the primary metatype.
- Results are ordered by target name, case-insensitive, then target ID.
- Target column values use the text form of the primary columns (system fields and properties).

### D4 – Query

1. `CatalogQueries.query` resolves the available joins:
   - the connection and target metatypes still exist and are reachable (D2 steps 2 and 3);
   - at least one target column can still be shown.

   Other joins are skipped without an error, and `Catalog.joinDefinitions[].available` reports them as `false`.
2. **Mode per join:** the `joinMode` override, else the join's `defaultJoinMode`, else `Catalog.defaultJoinMode`. An override for a join ID that the catalog does not have, or for an unavailable join, gives 400 `joinMode`.
3. **Aggregate joins** are read after the page: one statement per join, for the entity IDs of the page (`= ANY(?)`).
4. **Rows joins** join the page statement. Each rows join adds a `LEFT JOIN LATERAL` with the traversal of D3 and the target columns, and `count` counts the same `FROM` clause. Several rows joins give their cross product per primary entity.
5. **Sort keys of join columns** are `<joinId>.<key>`; the sort pattern allows that form. A join column is sortable only in rows mode and if its `sortable` is true. Otherwise 400 `sort`. Ties are ordered by the primary name, primary ID and target ID.

The text form, enum order and missing-values-last rules of the primary columns apply to join columns.

### D5 – SCR-020

**The dialog "Joins"** sits next to "Configure columns", with the same permission rule. It lists the joins with name, connection metatype, direction, target metatype and default mode, and shows a "Not available" badge where `available` is false. Each row has "Edit" and "Remove"; "Add join" opens the form.

**The form** has four steps:

1. **Connection:** the connection metatypes whose allowed endpoints admit the primary metatype as source or as target.
2. **Direction:** only the directions that admit the primary metatype.
3. **Target:** the metatypes reachable in that direction (the allowed endpoints and their subtypes, or every non-connection metatype if a side has no constraints).
4. **Columns and mode:** the target columns with the editor of "Configure columns" (without the default sort), the name and the default mode.

The steps compute their options from `listMetatypes` and the connection constraints of the metatypes. The server validates again.

**The table:**

- every available join adds a column group; its header shows the join name and a "Compact / Rows" switch;
- in aggregate mode a cell lists one value per result, one per line;
- in rows mode it shows the single value;
- sorting by a join column is offered only in rows mode.

**URL state.** The mode is kept as `mode=<joinId>:<mode>` for every join whose mode differs from its default (ADR-044). Switching keeps `page`; a page beyond the new total returns to the first page, as for an old link. Switching a join to compact ends a sort by one of its columns. An invalid or unknown entry is removed from the URL together with sort and page, like an invalid sort.

**Stories** "Joins" (dialog) and "Join rows" (table in rows mode), plus specs for the scenarios. The messages come in English and German.

## Risks / Trade-offs

- **Cross product of several rows joins** can multiply rows. → Accepted: it is the rows semantics of the business object. Paging bounds the page, and the counting statement uses the same joins. The latency is measured in S9 change 7 (#66).
- **`OFFSET` paging over rows joins** gets slower on deep pages. → Keyset paging is #66.
- **Deep-object parameter binding** in the generated Spring interface. → If the generated interface does not bind it, the controller reads the raw parameters; a test covers the binding.
- **Exact target metatype** leaves out entities of subtypes of the target metatype. → Consistent with the primary rows. A later change can widen both together.

## Migration Plan

No database migration. Joins are stored in `catalogs.config`. Existing catalogs have empty `joinDefinitions` and keep their behaviour.

## Open Questions

None. The owner decisions of 2026-11-05 settle the operations, the result keys, the rows paging and the UI.
