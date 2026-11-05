# Design

## Context

- **Rows:** the query (`CatalogQueryStore.page`) reads the active entities of exactly the primary metatype. For a connection metatype these are its connections. Their endpoints live in `entity_connections (entity_id, source_entity_id, target_entity_id)` (V19, US-036).
- **Joins:** go through `entity_connections` from the primary entity, which can be a connection. `AllowedConnections.between` lets a connection metatype be an allowed endpoint. So `data-flow → carries-data → data-object` needs no change in the traversal (ADR-010, one level by ADR-004).
- **Columns:** a column (`Catalogs.Column`, `config.columns`) is a system field or a property of the primary metatype. `CatalogQueries.configured` turns it into a `QueryColumn`, which the store reads for the alias `e`. The target columns of a join use the same machinery with the alias `j<n>`.

## Goals / Non-Goals

**Goals:**

- endpoint columns for source and target, with system fields and the offered properties;
- the default columns of a connection catalog;
- the hint in the dialog "Joins";
- tests for connection rows and the n-connection join.

**Non-Goals:**

- filters (change 3);
- chained joins;
- endpoint columns of a join target.

## Decisions

### D1 Contract (delta to `api/openapi.yaml`)

- **`CatalogColumnConfig.endpoint`:** `type: [string, 'null']`, `enum: [source, target, null]`, default `null`, meaning the primary entity. With `endpoint`, `field` or `propertyDefId` names the attribute of the source or the target. The description names BR-11.
- **`CatalogColumn.key`:** the description adds `source.<key>` and `target.<key>`, where `<key>` is a system field key or the `sqlColumnName` of a property.
- **The `sort` parameter of `queryCatalog`:** the description names `source.<key>` and `target.<key>` besides `{joinId}.{key}`.

No new operation. `CatalogColumnsUpdate` and `CatalogJoinInput.targetColumns` reuse `CatalogColumnConfig`; for the latter, `endpoint` is rejected (422).

### D2 Validation of endpoint columns

`Catalogs.validate(metatypeId, columns, ...)` gains the endpoint rule (BR-11):

- **Where it is allowed:** an endpoint column is allowed only on the columns of a catalog whose primary metatype is a connection (`CatalogStore.isConnection`). In join target columns it is always 422, with the field `targetColumns[i]`.
- **Offered properties of a side** (`offeredEndpointProperties(connectionMetatypeId, side, date)`):
  - the `public`, not retired properties mapped on the date to an allowed endpoint metatype of the side, to one of its subtypes, or to one of their base types;
  - without a constraint for the side, all `public`, not retired properties mapped to any metatype of the instance metamodel.

  The allowed endpoints come from `metatype_connection_constraints`, read as in `AllowedConnections`.
- **Uniqueness:** an attribute counts as duplicate per `(endpoint, attribute)`, so `name` and `source.name` can both be columns.
- **Available:** `available` is `false` when a property is no longer offered. The query leaves the column out, as for primary columns (D4 of us-047-configure-columns).

### D3 Store

`CatalogQueryStore.QueryColumn` gains `Endpoint endpoint` (`null`, `SOURCE`, `TARGET`).

- **`page`:** if any column or the sort names an endpoint, it adds:
  - `LEFT JOIN entity_connections ec ON ec.entity_id = e.id`;
  - `LEFT JOIN entities es ON es.id = ec.source_entity_id AND es.deleted_at IS NULL`;
  - `LEFT JOIN entities et ON et.id = ec.target_entity_id AND et.deleted_at IS NULL`.

  An endpoint column uses the alias `es` or `et` in `text(...)` and `values(...)`. The `SystemField` templates already take the alias, so a deleted endpoint gives `NULL`.
- **Sorting:** an endpoint column sorts like a primary column, with `es`/`et` as the sort alias. `Sort` keeps its form; the column index points to the primary list, which contains the endpoint columns.
- **`count`:** unchanged. Endpoints do not multiply rows, because `entity_connections` has one row per connection.

### D4 Query

- **Defaults:** `CatalogQueries.defaults(metatypeId)` checks `isConnection`. For a connection catalog the columns are `source.name` ("Source"), `name` ("Name"), `target.name` ("Target"), and the status property if it is mapped.
- **Keys and labels:**
  - the key is `<endpoint>.<key>`;
  - the label is the own label, else "Source"/"Target" for `name`, else "Source: <attribute>"/"Target: <attribute>".
- **Sort keys:** the `SORT` pattern accepts the prefixes `source.` and `target.` besides a join ID. `sortOf` checks the endpoint prefix first; a UUID prefix stays a join.

### D5 SCR-020

- **Dialog "Configure columns":**
  - for a connection catalog, two further groups, "Source" and "Target", with the system fields and the offered properties;
  - the offered properties are computed from `fetchMetatypes`: the allowed endpoints from `connectionConstraints`, their subtypes over `baseMetatypeId`, their mappings with base types, or all properties without a constraint;
  - rows carry `endpoint`; the keys `source:<id>` and `target:<id>` keep the rows apart.
- **Table:** `CatalogPage.translatedColumns` translates endpoint keys:
  - `source.name` and `target.name` become `catalog.columns.endpoint.source` and `catalog.columns.endpoint.target`;
  - other endpoint keys become `catalog.columns.endpointAttribute`, filled with the side and the translated attribute.

  Sorting and the URL state stay unchanged (ADR-044); the sort key is the column key.
- **Create form:** the metatype selection groups elements and connections (`USelect` items with group labels).
- **Dialog "Joins":** for a connection catalog without connection items, it shows the hint `catalog.joins.connectionCatalogHint` instead of `catalog.joins.noConnections`.
- **Story:** a new story "Connection catalog" in `SCR-020`, with synthetic information flows and their source and target (`frontend/src/test/catalogs.ts`). The `carries-data` join is covered by the backend tests, since the join dialog and table are unchanged.

### D6 Data model

- **`business-objects/catalog.md`:**
  - ColumnConfig gains `endpoint` (`source`, `target` or null);
  - new BR-11: endpoint columns only on connection catalogs, with the offered properties of D2;
  - the primary metatype may be a connection metatype.
- **`docs/data-model.puml`:** the ColumnConfig note in `config.columns` gains `endpoint`.

No migration.

## Risks / Trade-offs

- **Unconstrained sides offer every property.** With no constraint, the dialog lists all `public` properties, and many cells stay empty. This is accepted (owner decision); the groups keep the list readable.
- **Three extra joins per page** only when an endpoint column is shown or sorted. The primary key and indexes of `entity_connections` keep them cheap. Performance follows in change 7 (#66).
