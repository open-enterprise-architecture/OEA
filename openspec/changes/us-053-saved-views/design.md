# Design

## Context

- **Query:** `CatalogQueries.query` resolves the primary columns through `CatalogColumns` (configured or default columns), the joins with their modes, the sort and the filters (us-047, us-048, us-051); `CatalogQueryStore` reads count and page.
- **Configuration:** views live in `catalogs.config.savedViews`, which exists empty since us-046. The contract has a placeholder schema `CatalogSavedView` with `columnOrder`, `activeFilterIds`, `joinModeOverrides` and `isDefault`. `removeJoin` already removes the join mode overrides of views (us-048).
- **Saved filters (us-051):** stored in `config.savedFilters`, validated by `FilterRules` against the columns of the query, with `available` and the cleanup on `removeJoin`. Views follow the same patterns.
- **URL state:** `CatalogPage` keeps `sort`, `page`, `mode`, `filter` and `filterMode` in the URL (ADR-044); `viewOf` parses them.

## Goals / Non-Goals

**Goals:**

- views as snapshots of the view state, with add, replace and remove, the default and the availability;
- the columns of a view applied by the query;
- the view selection and the default view on opening in SCR-020.

**Non-Goals:**

- personal views in an instance catalog;
- editing a view in the UI beyond "Make default";
- hiding joins in a view.

## Decisions

### D1 Contract (delta to `api/openapi.yaml`)

- **`CatalogSavedViewInput`:**
  - `name` (1–100), `description` (≤ 1000, nullable);
  - `columnOrder`: 1–100 column keys of the primary columns;
  - `filters`: 0–20 `CatalogFilterExpression`, and `filterMode` (`CatalogFilterMode`, default `and`);
  - `joinModeOverrides`: `CatalogJoinModeOverride[]`, at most one per join;
  - `sort`: `CatalogSort` or `null`;
  - `isDefault`: boolean.
- **`CatalogSavedView`:** the input with `id` and `available` (read-only). `activeFilterIds` is dropped; the schema was only a placeholder, always empty.
- **Operations:**
  - `addCatalogView` `POST /catalogs/{id}/views`: 201 with the catalog; 403, 404, 422;
  - `updateCatalogView` `PUT /catalogs/{id}/views/{viewId}`: replaces the view and keeps its ID; 200; 403, 404, 422;
  - `removeCatalogView` `DELETE /catalogs/{id}/views/{viewId}`: 200; 403, 404.

  The condition for instance catalogs is enforced by the handler and probed as for the joins and filters.
- **`queryCatalog`:** `view` (`in: query`, UUID). `CatalogQueryProblem.invalidFields` gains `view`.
- **`Catalog.savedViews`:** loses the "follow later" note.

### D2 View model and validation

`Catalogs.addView` and `updateView` check, in this order (422 naming the field):

1. `name`: trimmed, 1–100 characters, no control characters, unique in the catalog ignoring case (for `PUT`, except the view itself).
2. `description`: at most 1000 characters, no NUL.
3. `columnOrder`: 1–100 keys. Each key is a key of the catalog's current primary columns (`CatalogColumns.primary`), at most once. Error field: `columnOrder[i]`.
4. `joinModeOverrides`: each `joinDefinitionId` is an available join, at most once, and `joinMode` is `rows` or `aggregate`. Error field: `joinModeOverrides[i].joinDefinitionId`.
5. `filters`: at most 20. Each expression is checked by the rules of us-051 (`Catalogs.check`, `FilterRules`) against the columns of the view and the columns of the available joins. Error field: `filters[i].attributePath`, `.operator` or `.value`. `filterMode` is `and` or `or`.
6. `sort`: a key of a sortable column of the view, or `{joinId}.{key}` of a sortable column of a join that is in rows mode in the view (override, else the join's or catalog's default). Error field: `sort`.
7. The limit of 20 views (`savedViews`).

The checks of filters and sort reuse the column resolution of the query, restricted to the view's columns, so that a view that saves is also a view the query accepts.

### D3 Storage, default and cleanup

- **Storage:** `config.savedViews[]`: `{id, name, description, columnOrder, filters, filterMode, joinModeOverrides, sort, isDefault}`, written with `CatalogStore.updateSavedViews` under the row lock of `writable()` (us-051 D7).
- **Default (BR-06):** with `isDefault: true`, the other views get `isDefault: false` in the same write. Removing the default view sets no new default.
- **Permissions:** `writable()`, the rule of the columns.
- **Audit:**
  - `catalog.view-added`, `catalog.view-changed`: `viewId`, `name`, `isDefault`, the number of columns and filters;
  - `catalog.view-removed`: `viewId`, `name`.
- **`removeJoin`:** besides the overrides (us-048), it removes from each view the filters on the join's columns and a sort on them.
- **Reading (`available`):** a view is available if every key of `columnOrder` is a primary column, every override names an available join, every filter key is a column of the view or of an available join, and the sort key, if any, is one of them. This is computed in `catalog()` as for saved filters.

### D4 Query with a view

`CatalogQueries.query` gains `view` (UUID or `null`):

- An unknown view or one with `available: false` adds `view` to the invalid parameters (400).
- With a view, the primary columns are those of `columnOrder`, in its order, taken from `CatalogColumns.primary`. Filters and sort resolve against these columns; joins stay as they are.
- The view does not apply its own filters, modes or sort. The page passes them as parameters, so that the URL shows them and a reader can change them (decision of 2026-11-06).

### D5 SCR-020

- **View selection:** a `USelect` "Views" next to the filter bar, shown when the catalog has views.
  - It offers "All columns" and the views; the default view carries a mark, and an unavailable view is disabled with "not available".
  - Choosing a view writes `view`, `filter`, `filterMode`, `mode` and `sort` of the view into the URL (`router.replace`, page 0).
  - Choosing "All columns" removes `view` and keeps the rest.
- **Changed view:** when the URL state differs from the chosen view (another filter, sort or mode), the selection shows "(changed)" after the name.
- **Default on opening:** in `loadCatalog`, after the catalog is read, if the URL has none of `view`, `sort`, `page`, `mode`, `filter` and `filterMode`, and the catalog has an available default view, the page writes the default view into the URL and queries with it.
- **"Save view"** (with the right to configure, `canConfigure`):
  - a modal with name, description, the columns of the current result with checkboxes and up/down buttons (preselected: the shown columns in their order), and "Use as default";
  - it saves the current filters, filter mode, join modes and sort.

  A taken name shows the 422 message.
- **Dialog "Views"** (with the right to configure): the list of views with "Make default" (a `PUT` of the view with `isDefault: true`) and "Remove".
- **URL:** `viewOf` accepts `view` as a UUID. On a 400 `view`, the page drops the view and keeps the rest; if the query still answers 400, the existing fallback drops everything.

### D6 Data model and documentation

- **`business-objects/catalog.md`:**
  - SavedView as implemented: `columnOrder` as column keys, `filters` and `filterMode` instead of `activeFilterIds`, `sort`, `available`, the limits;
  - BR-05 sharpened (keys of the catalog's primary columns), BR-06 with the switch;
  - BR-10 extended by the cleanup of filters and sort.
- **`docs/data-model.puml`:** `config.savedViews` typed.
- **`docs/audit-event-catalog.md`:** the three events.

No migration: `savedViews` is empty in every catalog so far.

## Risks / Trade-offs

- **[A view saved before a column change becomes unavailable]** → the selection shows it disabled, and the dialog "Views" lets a manager remove it and save it anew. No automatic repair, so that a view never shows something different from what was saved.
- **[The view's filters are not bound to the view in the query]** → a reader who changes a filter still sees the view's columns, and "(changed)" says so. This keeps one filter mechanism (us-051) instead of two.
- **[The default view is applied by the client]** → API clients see the configured columns unless they pass `view`. That is intended: the API stays explicit, and the default is a presentation choice.
