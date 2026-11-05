# Proposal

## Why

A catalog shows only attributes of its primary metatype. Kurt wants to see related entities in the same table, for example all interfaces of an application, without navigating to each one. Joins via connection metatypes do this. They are the feature that distinguishes a catalog from an entity list (UC-06, US-048, REQ-045). The visitor switches each join between one row per entity and one row per join result (US-050, REQ-049). The join column of US-049 AC1 follows with them.

Connection metatypes and connections exist since S8 change 1 (US-036).

This is S9 change 1. Owner decisions of 2026-11-05 cut S9 into seven changes:

1. `us-048-catalog-joins`: US-048, US-050 and the join column of US-049 AC1.
2. US-080: a connection metatype as the primary metatype of a catalog.
3. Filters: US-051 and US-052.
4. Saved views: US-053 and US-049 AC6.
5. US-054: catalogs in the navigation tree, with a minimal folder management.
6. Portal: #67 and #76.
7. Performance: #66, measured against the finished query.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) |
| User Stories | [US-048: Add a Join to the Catalog](../../../requirements/user-stories/US-048-add-join.md), [US-050: Switch Join Mode](../../../requirements/user-stories/US-050-switch-join-mode.md), [US-049](../../../requirements/user-stories/US-049-show-catalog-data.md) AC1 (join column) |
| Requirements | [REQ-045: Add a Join Definition](../../../requirements/req/REQ-045-configure-join-definition.md), [REQ-049: Switch Join Mode at Runtime](../../../requirements/req/REQ-049-join-mode-runtime.md), [REQ-046](../../../requirements/req/REQ-046-catalog-query.md) (join results in the query) |
| ADRs | [ADR-004](../../../adrs/ADR-004-reification-details.md) (one traversal level), [ADR-010](../../../adrs/ADR-010-n-connection-data-lineage.md) (connections), [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8), [ADR-044: View State in the URL](../../../adrs/ADR-044-view-state-in-url.md), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-020 Catalog Browser |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-05:

- **Operations per join**, as in REQ-045:
  - `addCatalogJoin` (`POST /catalogs/{id}/joins`);
  - `updateCatalogJoin` (`PUT /catalogs/{id}/joins/{joinId}`);
  - `removeCatalogJoin` (`DELETE /catalogs/{id}/joins/{joinId}`).

  The server assigns the join ID. Removing a join also removes the join mode overrides of saved views that refer to it.
- **A join has:**
  - a name;
  - a connection metatype;
  - a direction (`outbound`, `inbound` or `both`);
  - a target metatype;
  - target columns: `CatalogColumnConfig` as in US-047, with system fields and `public` properties of the target metatype;
  - an optional default join mode (otherwise the catalog's).
- **Validation (422):**
  - the connection metatype must be a connection (BR-02);
  - the target metatype must be reachable through it in the chosen direction, by the allowed endpoints of US-036 (BR-03);
  - the target columns follow the rules of US-047 for the target metatype.
- **Who configures:** the same rule as for the columns (US-047): `catalog:manage` for an instance catalog, and only the creator for a personal catalog. Each change is audited.
- **Query results are keyed by join ID.** Each row carries `joinResults[<joinId>]`, and the result lists the joins with name, mode and columns. A primary entity without results stays in the result, with an empty list (LEFT JOIN semantics).
- **Join mode at runtime.** `joinMode[<joinId>]=rows|aggregate` overrides the default of a join for one query and does not change the catalog. An unknown join ID gives 400.
- **Rows mode:**
  - paging counts rows, and `totalElements` is the number of rows;
  - the query can sort by a column of the target metatype;
  - aggregate mode sorts only by columns of the primary metatype.
- **SCR-020:**
  - a dialog "Joins" next to "Configure columns" lists the joins and adds, edits and removes them;
  - adding goes in steps: connection metatype → direction → target metatype → columns and mode;
  - each join column group has a "Compact / Rows" switch in the table header;
  - the mode is part of the view state in the URL (ADR-044).
- **Manual:** "Joins" in "Catalogs", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-045:**
  - `connectionType` and `targetEntityType` become `connectionMetatypeId` and `targetMetatypeId`;
  - `targetColumns` use `field` or `propertyDefId` (US-047);
  - the answer to `POST` is the changed catalog;
  - `DELETE` answers 200 with the catalog;
  - `aggregateLabel` is dropped: the join name is the header of its columns.
- **REQ-046:**
  - `joinResults` is keyed by join ID instead of the join name;
  - each entry is a list of results with entity ID and values, with at most one entry in rows mode.
- **US-048 technical notes:** the configurator is a dialog in SCR-020.

Assumptions, open for the owner's review:

- **Several joins in rows mode** give their cross product per primary entity, like SQL joins.
- **Limits:** a catalog has at most 10 joins, and a join at most 20 target columns.
- **`both`** deduplicates target entities that are reached in both directions.
- **Deleted entities:** only active connections and active target entities count.
- **Exact target metatype:** the join finds targets of exactly the target metatype, as the primary rows have exactly the primary metatype.
- **Stale joins:** a join whose connection or target metatype is deleted, or is no longer reachable, is left out of the query without an error. The dialog shows it as unavailable.

## Out of Scope

- Filters on join attributes (US-051 AC2, change 3) and saved views with join mode overrides (US-053, change 4).
- A connection metatype as the primary metatype (US-080, change 2).
- Traversal over more than one level (ADR-004).
- Reordering joins (REQ-045 implementation notes, optional); joins keep their creation order.

## Capabilities

### New Capabilities

- `catalog/catalog-joins`: join definitions of a catalog — connection metatype, direction, target metatype, target columns, default mode, validation — and who may configure them.

### Modified Capabilities

- `catalog/catalog-query`:
  - join results per row and the runtime join mode (new requirements);
  - "Sorting" and "Pagination" in rows mode.

## Impact

- `api/openapi.yaml`:
  - the operations `addCatalogJoin`, `updateCatalogJoin` and `removeCatalogJoin`, and the schema `CatalogJoinInput`;
  - `CatalogJoinDefinition` loses its `readOnly` placeholder description;
  - `CatalogRow.joinResults`, `CatalogQueryResult.joins` with `CatalogQueryJoin`, and `CatalogJoinResult`;
  - the `joinMode` parameter of `queryCatalog`, and `joinMode` in `CatalogQueryProblem`.
- `backend/`:
  - `io.oea.app.catalog`: join validation through `AllowedConnections`, and the query with joins;
  - `io.oea.core.catalog`: the store with the join traversal;
  - no migration: the joins live in `catalogs.config.joinDefinitions`.
- `frontend/`: the dialog "Joins", the join columns and the mode switch in SCR-020, the URL state, the stories and the messages in English and German.
- `manual/`: "Catalogs" in English and German.
- Documentation:
  - `business-objects/catalog.md`, `docs/data-model.puml`, `docs/audit-event-catalog.md`;
  - US-048, US-050, US-049, REQ-045, REQ-046 and REQ-049;
  - the sprint plan with the cut of S9.
