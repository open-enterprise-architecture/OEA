# Design

## Context

- **Deletion fields (change 14):** `entities.deleted_at`, `deleted_by` and `deleted_with_entity_id`, and `restoreDeletedEntity` with its cascade.
- **Catalog query:** `queryCatalog` reads only active entities, through `core.catalog` stores with cursor paging and a time limit.
- **Pattern for a paged list:** the diagram list (change 10) uses `PageParam`, `PageSizeParam`, `CursorParam` and an `items` envelope.

## Goals / Non-Goals

**Goals:**

- A list and page of deleted entities with restore.
- Deleted rows in the catalog on request.

**Non-Goals:**

- Deleting there.
- Purge.
- Other read channels.

## Architecture Constraints

- **ADR-051:** SQL in `core.entity` and `core.catalog` only.
- **ADR-032:** `entity:restore` for the list and for `includeDeleted`. The condition on `queryCatalog` is declared in `x-oea-authorization` (`conditions`: `when` "includeDeleted=true", `permissions` [`entity:restore`]) and enforced by the handler: `@ConditionsEnforcedByHandler` on `queryCatalog` with `probeRole` `viewer` (holds `entity:read`, not `entity:restore`; only `enterprise-architect` holds it) and `probePath` `/api/v1/catalogs/00000000-0000-0000-0000-000000000000/query?includeDeleted=true`. The check therefore runs before the catalog is looked up, so the probe gets 403, not 404. `HandlerEnforcedConditionsTests` picks the probe up from the annotation.
- **ADR-013:** the contract first. The conventions check applies: `items` envelope, `maxLength` and `UuidIdParam`.

## Decisions

### D1 `listDeletedEntities`

- `GET /entities/deleted`, `entity:restore`.
- **Parameters:**
  - `q`, at most 255 characters, matched with an escaped `ILIKE`;
  - `metatypeId` (uuid);
  - `page`, `pageSize` (1–200, default 50) and `cursor`.
- **Order:** `deleted_at DESC, id DESC`. The cursor carries both values plus a fingerprint of the query, like the diagram cursor.
- **Items (`DeletedEntity`):**
  - `id`, `name` and `metatype` (`id`, `name`, `abbreviation`);
  - `isConnection`, and for a connection `source` and `target` (`id` and `name`, also when deleted);
  - `version`, `deletedAt` and `deletedBy` (`PrincipalRef`);
  - `deletedWith` (`id` and `name`, or null).
- **Path order:** `/entities/deleted` does not clash with `/entities/{id}` (`EntityIdParam`, an integer), and the literal path wins in Spring MVC. Redocly's `no-ambiguous-paths` does not flag a literal segment against a template, as `/diagrams/deleted` beside `/diagrams/{id}` and `/entities/batch` beside `/entities/{id}` show; no entry in `.redocly.lint-ignore.yaml` is added.
- **Visibility:** the entries carry no property values, so the property visibility and the scopes of ADR-032 do not apply; names, like in `listDeletedDiagrams`, are visible to every holder of `entity:restore`.
- **Restore of a part:** an entry taken along by a cascade can be restored on its own; `restoreDeletedEntity` then places an element at the top level when its parent is still deleted, or answers 409 `connection-not-restorable` for a connection with a deleted endpoint.

### D2 `includeDeleted` on the catalog query

- A boolean query parameter of `queryCatalog`, default false. With true and without `entity:restore`, the answer is 403, checked before the query runs.
- **Predicate:** `CatalogQueryStore.where` gets a flag. Without it the SQL stays exactly `e.metatype_id = ? AND e.deleted_at IS NULL`, so the default statement and its plan do not change; with it the `deleted_at` condition on the primary alias `e` is left out (not ORed with a bound parameter). It applies to the primary metatype only.
- **Joins:** the join pairs keep reading active connections and active targets (`pairs`, `narrow`, the aggregate `EXISTS`). The connections of a deleted entity were deleted with its cascade, so a deleted row has no join results: in mode `rows` it keeps one row without results, and a filter on a join column excludes it.
- **Connection catalogs:** with a connection metatype as primary metatype, deleted connections are rows too. The endpoint aliases (`es`, `et` in `endpoints`) of a deleted row also join a deleted endpoint, so its `source.{key}` and `target.{key}` show the endpoints the connection had; for an active row a deleted endpoint still gives empty values. Endpoint filters and sorting use the same values. Restoring such a row with a deleted endpoint answers 409 `connection-not-restorable`, shown as a notice.
- **Rows:** `CatalogRow` (the row of `CatalogQueryResult`) gets three optional members, set only on a deleted row: `deleted` (`true`), `deletedAt` (date-time) and `version` (integer, the version of the deleted entity, the `If-Match` of `restoreDeletedEntity`; a deleted entity cannot be read through `getEntity`). `CatalogQueryResult` is shared by `queryPortalCatalog` and the table widgets of the dashboards (`WidgetData.table`); there the members never appear, so these responses do not change. The member is absent rather than `false` on active rows for the same reason.
- **Cursor:** the fingerprint (`CatalogQueries.fingerprint`) gets `includeDeleted` as one more part, so a cursor of the other mode is rejected with 400 `cursor`. The channel tag already separates the portal.
- **Counts:** the total counts deleted rows when they are included.
- **Saved views:** they store no `includeDeleted`.
- **Portal:** the portal query and the dashboard table widgets are unchanged; they call the query with the flag off.
- **Open issues:** `openIssues` is computed for deleted rows as for active ones.

### D3 Frontend

- **SCR-068:** `DeletedEntitiesPage.vue` at `/entities/deleted`.
  - Name filter, metatype filter (from the metamodel read) and "Load more".
  - Each entry shows name, metatype, deletion time, actor and "Deleted with …".
  - "Restore" opens a confirmation, calls `restoreDeletedEntity` with the entry's version, and then shows a notice with the restored and not-restored items, and refreshes.
  - On 409 or 412: a notice and a reload.
  - The header entry is shown only with `entity:restore`. A direct visit without it shows "No permission".
- **SCR-020:**
  - The switch "Show deleted" in the toolbar, for `entity:restore` only. It is kept in the URL query (`deleted=1`, like sort and page, ADR-044) but not in views. Without `entity:restore` the URL value is dropped as an invalid value, and switching resets the page to 0 without a cursor.
  - Deleted rows are greyed, with a marker "Deleted" and the time, and the row action "Restore" with a confirmation, using the row's `version`.
  - Restoring refreshes the page; 409 or 412 show a notice and refresh, and `notRestored` is named as on SCR-068.
- **Accessibility:** the marker is text, not color only. The actions are reachable by keyboard.

### D4 Documentation

- **The manual (en/de):** the new page "Deleted entities", and "Catalogs" (Show deleted).
- **SCREENS.md:** SCR-068 new and `implemented`; SCR-020 extended.
- **ADR-019:** the amendment notes the screens.
- **The sprint plan.**

## Data Model

No change. An index for the list may be needed: `entities (deleted_at DESC, id DESC) WHERE deleted_at IS NOT NULL`. If so, add it as the next migration.

## API

D1 and D2.

## Risks / Trade-offs

- **[Risk]** `includeDeleted` widens the catalog query.
  - **Mitigation:** the same time limit (REQ-186, 503 `query-timeout`) applies. The default statement is unchanged (D2), so the latency budgets of `catalog/catalog-query-latency` still hold and its CI measurement stays as is; no new budget is set for `includeDeleted`, which is an occasional query of few users.
