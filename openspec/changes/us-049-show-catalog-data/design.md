# Design

## Context

Change 6 created catalogs (`catalogs`, V7) with an empty JSONB configuration, `GET /catalogs/{id}` with the visibility rule for personal catalogs (`Catalogs.get`, 404 for other persons) and a catalog page with a placeholder where the table belongs. Entities live in `entities` (V6) with `metatype_id`, `name`, `deleted_at` and the index `entities_active (metatype_id) WHERE deleted_at IS NULL`; property values are typed rows in `entity_property_values` (`value_text`, `value_int`, `value_bool`, `value_date`, `value_ref`, `value_enum`; ADR-022). Property definitions carry a stable `sql_column_name` (never `name`) and a `visibility_mode`. The starter package maps one property, `Status` (enum, public), to each element type.

The contract defines the paging conventions `PageParam` (`page`, from 0), `PageSizeParam` (`pageSize`, 1–200, default 50), `SortParam` (`sort=field:asc|desc`) and `PageMeta` (`page`, `pageSize`, `totalElements`, `totalPages`). REQ-046 describes `sortBy`/`sortDir`, a maximum page size of 500 and `totalCount`/`data` instead. The contract also still contains `GET /views/catalog` and `GET /views/catalog/{id}` from the former SCR-020 mockup, never implemented; the portal operation `/portal/catalog` shares their schemas.

## Goals / Non-Goals

**Goals:**

- One query endpoint that later stories extend with configured columns, joins and filters without changing its shape
- Server-side sorting and paging, so that the client never loads a whole metatype

**Non-Goals:**

- Joins, filters, saved views, configured columns (later stories)
- Performance tuning beyond the existing indexes (REQ-071 follows)

## Architecture Constraints

- ADR-028: `io.oea.api.catalog.CatalogController` (contract), `io.oea.app.catalog` (column resolution, visibility, paging rules), `io.oea.core.catalog` (SQL); the API layer sees only app-layer types
- ADR-022: values are read from the typed columns; the text form of a value is what the table shows
- ADR-019: soft-deleted entities are excluded (`deleted_at IS NULL`)
- ADR-032: `entity:read`; the personal-catalog rule of `Catalogs.get` is reused; only `public` properties appear (REQ-160 restricted properties follow)
- ADR-013: contract first; frontend types generated from `api/openapi.yaml`
- ADR-044: view state of list and table views in the URL query, written with `router.replace` (D8)

## Decisions

### D1 – Paging and sort parameters follow the contract conventions

`GET /catalogs/{id}/query` takes `PageParam`, `PageSizeParam` and `SortParam` and answers with `PageMeta`, like every other paged operation of the contract. `sort` accepts exactly one `column:direction` pair; several pairs, an unknown column or an unknown direction answer 400 (`validation-failed`, `invalidFields: ["sort"]`). A page beyond the last one answers 200 with no rows.

*Alternative:* `sortBy`/`sortDir`, `pageSize` ≤ 500 and `totalCount` as written in REQ-046 – rejected for consistency with the existing operations; REQ-046 is aligned in the wrap-up.

### D2 – Columns are resolved by the application layer and returned with the rows

The app service determines the catalog's columns: configured columns are not possible yet, so every catalog gets the default columns:

1. `name` – the entity name (system field), label "Name"
2. the property named `Status` (case-insensitive) if the primary metatype maps it with a mapping valid today and the property's visibility is `public`; key = its `sql_column_name` (`status`), label = the property name

The response carries `columns: [{key, label, sortable}]` and `rows: [{entityId, values: {key: text|null}}]`. The client renders whatever columns come back, so US-047 changes only the resolution, not the contract or the table component.

*Alternative:* fixed fields `name`/`status` in the row schema – rejected, it would break with the first configured column.

### D3 – One SQL query per page plus one count

`io.oea.core.catalog.CatalogQueryStore` builds the statement from the resolved columns: `entities e` filtered by `metatype_id` and `deleted_at IS NULL` (index `entities_active`), one `LEFT JOIN entity_property_values` per property column, the text form `coalesce(value_enum, value_text, value_int::text, value_bool::text, value_date::text, value_ref::text)`. Order: the sort column with `NULLS LAST`, then `lower(e.name)`, then `e.id` as tie-breaker (default without sort: `lower(e.name), e.id`); `LIMIT`/`OFFSET` from the page. The count uses the same filter without joins. Column keys never reach the SQL text: the store receives property definition IDs as parameters and the sort as an enum of resolved columns.

### D4 – Visibility reuses the catalog read

The query service first reads the catalog through the same visibility rule as `Catalogs.get` (404 for a missing or foreign personal catalog), then resolves columns and queries. The operation declares `entity:read`, without conditions.

### D5 – Contract cleanup

`GET /views/catalog` (`getCatalogView`) and `GET /views/catalog/{id}` (`getCatalogEntityDetail`) and the tag `Views – Catalog` are removed. The schemas `EntityListItem` and `CatalogFilterOptions` stay because `/portal/catalog` uses them; its description no longer points to `/views/catalog`. Entity details remain available as `GET /entities/{id}`.

### D6 – Frontend

- `src/api/catalogs.ts`: `queryCatalog(id, {page, pageSize, sort})`
- `CatalogTable.vue` (component): renders the returned columns with `UTable`; the headers are buttons that cycle ascending → descending → default and show ↑/↓ (`aria-sort` for screen readers); below the table the text "Page N of M" and `UPagination`; while loading, the previous rows stay visible with a loading state; the empty state shows the hint of US-049 AC4
- `CatalogPage.vue` replaces the placeholder with `CatalogTable`; sort and page come from the URL (D8), a sort change resets to page 0

### D7 – Screens

The SCR-020 story file gets the catalog page with the table as its **first** story ("Catalog table", 3 rows), so that the generated screen image shows the catalog browser with data; further stories: sorted descending, several pages, empty catalog, catalog overview. SCR-020 and SCR-028 move to `implemented`.

### D8 – View state in the URL

Applies [ADR-044](../../../adrs/ADR-044-view-state-in-url.md) to the catalog page.

The catalog page keeps `sort` and `page` as URL query (`/catalogs/7?sort=name:desc&page=2`) with the same meaning as the API parameters (`page` from 0), so that one parser serves both. Default values are omitted from the URL; `pageSize` is not part of it.

- Sort and page changes write the URL with `router.replace`, not `router.push`: a click on a column header creates no history entry, and back navigation leaves the catalog instead of stepping through sort states.
- On opening, the page reads its state from the URL; back navigation, reload and a shared link therefore show the same view.
- Invalid values fall back to the default and are removed from the URL with `replace`: a `page` that is not a non-negative integer, a malformed `sort`, and a `sort` the API answers with 400. A `page` beyond the last page of a non-empty catalog falls back to page 0.

*Alternative:* `<KeepAlive>` or sessionStorage – rejected, the view is lost on reload or in a new window and cannot be linked.

## Data Model

No change. `docs/data-model.puml` and `business-objects/catalog.md` already describe empty columns with the default `name`/`status`; the business object gets the precise rule of D2 (property named `Status`, public, mapped). No Flyway migration: the query uses `entities_active` and the unique `(entity_id, property_def_id)` index of `entity_property_values`.

## API

Delta to `api/openapi.yaml`:

- New `GET /catalogs/{id}/query` (`queryCatalog`, tag `Catalogs`, `entity:read`): parameters `PageParam`, `PageSizeParam`, `SortParam`; `200` `CatalogQueryResult`; `400` validation problem with `invalidFields`; `401`, `403`, `404`
- New schemas `CatalogColumn` (`key`, `label`, `sortable`), `CatalogRow` (`entityId` integer, `values` object of string or null), `CatalogQueryResult` (`columns`, `rows`, `page` as `PageMeta`, `sort` as `{column, direction}` or null)
- Removed: `/views/catalog`, `/views/catalog/{id}`, tag `Views – Catalog`; `/portal/catalog` description updated

## Risks / Trade-offs

- [`OFFSET` paging gets slower on deep pages of large metatypes] → acceptable for the skeleton; keyset paging with REQ-071
- [Text sort of enum values follows the value, not the enum order] → acceptable; enum order with configured columns (US-047) if needed
- [The `Status` default is a name convention] → only until configured columns exist (US-047); documented in the business object
- [Removing contract operations] → never implemented; no client depends on them

## Open Questions

- none
