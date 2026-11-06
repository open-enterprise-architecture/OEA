# Proposal

## Why

Readers of a catalog set up the same view again and again: a few columns in a certain order, a filter, a join in rows mode, a sort. UC-06 A3 and step 13 describe named views that a catalog manager prepares and marks as default, so that readers see the right view as soon as they open the catalog (US-053, US-049 AC6, REQ-048).

This is S9 change 4 of the cut of 2026-11-05 (see `us-048-catalog-joins`).

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) (A3, main flow step 13) |
| User Stories | [US-053: Create a SavedView and Mark It as Default](../../../requirements/user-stories/US-053-saved-view.md), [US-049](../../../requirements/user-stories/US-049-show-catalog-data.md) AC6 |
| Requirements | [REQ-048: Create SavedView and Apply on Opening](../../../requirements/req/REQ-048-saved-view.md), [REQ-047](../../../requirements/req/REQ-047-set-and-save-filters.md) AC7 (dropped) |
| ADRs | [ADR-044: View State in the URL](../../../adrs/ADR-044-view-state-in-url.md), [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-020 Catalog Browser |
| Sprint / Milestone | S9 / Milestone 2 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-06:

- **A view is a snapshot of the view state.** It keeps the column selection and order, the filters with their AND/OR, the join modes and the sort. It does not reference saved filters, so `activeFilterIds` of REQ-048 is replaced by the filter expressions themselves, and REQ-047 AC7 (cleaning up views when a saved filter is removed) is dropped.
- **The server applies the columns of a view.** The URL carries `view=<id>`. The query then returns only the columns of the view, in its order. Filters, join modes and sort stay explicit parameters, as in changes 1 and 3.
- **The default view applies only without view state in the URL.** When the catalog opens without sort, page, join mode, filter or view in the address, the page applies the default view and writes it into the URL. A shared link with its own state keeps that state (ADR-044).

Further scope:

- **Operations:**
  - `addCatalogView` (`POST /catalogs/{id}/views`);
  - `updateCatalogView` (`PUT /catalogs/{id}/views/{viewId}`);
  - `removeCatalogView` (`DELETE /catalogs/{id}/views/{viewId}`).

  A view has a name (1–100 characters, unique in the catalog regardless of case), an optional description, `columnOrder` (1–100 column keys), `filters` (0–20 expressions) with `filterMode`, `joinModeOverrides`, an optional `sort` and `isDefault`. Marking a view as default clears the previous default (BR-06). A catalog has at most 20 views. Each change is audited.
- **Permissions:** views are changed under the rule of the columns: `catalog:manage` for an instance catalog, only the creator for a personal catalog. Every reader sees and uses them.
- **Query:** `queryCatalog` gains `view=<viewId>`. An unknown or unavailable view answers 400 `view`. Filters and sort may only name columns that the view shows.
- **Availability:** a view is `available: false` if it names a column, a join or a sort column that the catalog no longer has. The page offers it disabled.
- **Removing a join** also removes the filters and the sort on its columns from the views, besides its join mode overrides.
- **SCR-020:**
  - a view selection next to the filter bar;
  - "Save view" (name, description, the columns of the current view with their order, "default") for readers with the right to configure;
  - a dialog "Views" with "Make default" and "Remove".
- **Manual:** "Views" in "Catalogs", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-048:**
  - `columnOrder` holds column keys of the query result (`name`, `status`, `source.name`);
  - `filters` and `filterMode` replace `activeFilterIds`;
  - `sort` is added;
  - the client applies the default view only without view state in the URL;
  - the permission rule of the columns applies.
- **REQ-047 AC7:** dropped; no view references a saved filter.
- **US-053:**
  - AC1 with filter expressions instead of filter IDs;
  - the technical notes: the dialog "Views" and the view selection instead of a tab.
- **US-049 AC6:** realized, with the URL rule above.
- **Business object `catalog`:** SavedView as implemented; BR-05 and BR-06 sharpened.

Assumptions, open for the owner's review:

- **The view selection replaces the whole view state:** columns, filters, join modes and sort. Changing a filter or a sort afterwards keeps the view's columns (`view` stays in the URL), and the selection shows that the view was changed.
- **"All columns"** in the selection leaves the view and shows the configured columns.
- **Editing a view in place** is limited to "Make default" in the UI. To change a view, save it anew under its name after removing the old one. The API's `PUT` replaces the whole view.
- **Join columns** are not part of `columnOrder`; a view shows every available join, in the mode of the view.
- **Columns:** a view names only columns of the catalog's current primary columns (configured or default). Hidden configured columns cannot be part of a view.

## Out of Scope

- **Views per person** (personal views in an instance catalog): a reader keeps their own views in a personal catalog, as for saved filters.
- **Editing a view in the UI** beyond "Make default".
- **Hiding joins in a view.**
- **Exports per view:** a later change.

## Capabilities

### New Capabilities

- `catalog/catalog-views`: saved views of a catalog (operations, validation, default, permissions, availability), the columns of a view in the query, the view selection and the default view on opening.

### Modified Capabilities

- `catalog/catalog-joins`: "Edit and remove a join" also removes the filters and the sort of views on the join's columns.

## Impact

- `api/openapi.yaml`:
  - `addCatalogView`, `updateCatalogView` and `removeCatalogView` with `CatalogSavedViewInput`;
  - `CatalogSavedView` as implemented, with `available`;
  - the parameter `view` of `queryCatalog`, and `view` in `CatalogQueryProblem`.
- `backend/`:
  - `io.oea.app.catalog`: validation and storage of views in `config.savedViews`, the columns of a view in the query, the cleanup on removing a join;
  - no migration.
- `frontend/`:
  - the view selection, "Save view" and the dialog "Views" in SCR-020;
  - the default view on opening and `view` in the URL;
  - stories and messages in English and German.
- `manual/`: "Catalogs" in English and German.
- Documentation:
  - `business-objects/catalog.md` (SavedView, BR-05, BR-06), `docs/data-model.puml`, `docs/audit-event-catalog.md`;
  - US-049, US-053, REQ-047 and REQ-048;
  - the sprint plan.
