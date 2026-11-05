# Proposal

## Why

A catalog shows fixed columns today: the name and, if mapped, the status (US-049). Its audience needs the attributes relevant to it, in its order and with its labels. That is the main difference between a catalog and a generic entity list (UC-06, US-047, REQ-044).

Two deferred items belong to it:

- **#64:** the catalog's `columns`, `joinDefinitions`, `savedFilters` and `savedViews` are untyped arrays in the contract.
- **#65:** sorting by an enum column follows the text, not the enum order.

This is S8 change 4.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-06: Create, Configure and Use an Architecture Catalog](../../../requirements/use-cases/UC-06-create-and-use-catalog.md) |
| User Story | [US-047: Configure the Columns of a Catalog](../../../requirements/user-stories/US-047-configure-columns.md) |
| Requirements | [REQ-044: Configure the Columns of a Catalog](../../../requirements/req/REQ-044-configure-columns.md), [REQ-046](../../../requirements/req/REQ-046-catalog-query.md) (query uses the configured columns) |
| Issues | [#64](https://github.com/open-enterprise-architecture/OEA/issues/64) typed catalog schemas, [#65](https://github.com/open-enterprise-architecture/OEA/issues/65) enum order |
| ADRs | [ADR-032: Authorization Model](../../../adrs/ADR-032-authorization-model.md) (§8, object-level checks), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Screen | SCR-020 Catalog Browser |
| Sprint / Milestone | S8 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-05:

- **The columns of a catalog** are configured with the new operation `updateCatalogColumns` (`PUT /catalogs/{id}/columns`), which replaces the whole list.
- **Each column names its attribute:**
  - a property by its `propertyDefId`, so a renamed property keeps its column;
  - or a system field: `name`, `description`, `externalId`, `createdAt`, `updatedAt` or `entityVersion`. The version is called `entityVersion` because a property may have the storage name `version`, and the query uses both as column keys.
- **Each column has:**
  - an optional `displayLabel`;
  - `visible` (a hidden column stays in the configuration);
  - `sortable`;
  - an optional `defaultSortDirection`, set on at most one column.
- **Validation:**
  - at least one visible column (422);
  - properties must be mapped to the primary metatype, including inherited mappings, and be `public`;
  - no attribute twice.
- **The query uses the configured columns:**
  - the visible columns, in their order and with their labels;
  - the default sort comes from the configuration;
  - a column that is not sortable cannot be sorted by (400);
  - a catalog without configured columns keeps the default columns of US-049.
- **Enum columns sort by the order of the enum values (#65).** Values outside the enum and missing values come last.
- **Typed schemas (#64)** for `columns`, `joinDefinitions`, `savedFilters` and `savedViews`, following the business object `catalog`. Joins, filters and views stay empty until their stories (US-048 and later).
- **SCR-020 gets a dialog "Configure columns":**
  - it lists all attributes, each with a visible switch, a label and the sortable switch;
  - the order can be changed by drag and drop and with up/down buttons (keyboard, accessibility);
  - it has a choice of the default sort;
  - it shows the error "At least one column must be visible".
- **Who configures:** with `catalog:manage` for an instance catalog; for a personal catalog, only its creator. Every change is audited as `catalog.columns-changed`.
- **Manual:** "Configuring columns" in "Catalogs", in English and German.

Realigned sections, which were non-binding until this change:

- **REQ-044 Inputs and AC1–AC5:**
  - `attributeName` becomes `propertyDefId` or `field`;
  - `sortOrder` becomes the order of the list (AC5: the order of the sent list is the display order).
- **REQ-044 AC4 (cleaning up saved views) and PATCH** follow with the saved views.
- **US-047 technical notes:** the configurator is a dialog in SCR-020.

Assumptions, open for the owner's review:

- **Personal catalogs of others:** a person without `catalog:manage` gets 403 for every catalog that is not their own personal catalog, also for unknown and other personal ones. So no answer reveals whether another person's personal catalog exists.
- **Stale columns:** a configured property that is later unmapped, retired or no longer `public` is left out of the query, without an error. The configuration keeps it, and the dialog shows it as unavailable.
- **No optimistic locking for columns:** the last write wins. Catalogs have no version yet.

## Out of Scope

- Joins (US-048), saved filters and saved views with their operations, and the cleanup of saved views (REQ-044 AC4).
- Columns of joined metatypes.
- Column widths and per-person column settings.

## Capabilities

### New Capabilities

- `catalog/catalog-columns`: configuring the columns of a catalog — attributes, order, labels, visibility, sortability, default sort — and who may configure them.

### Modified Capabilities

- `catalog/catalog-query`: the query returns the configured columns ("Default columns" becomes "Columns of the query") and sorts by the configured default and by enum order ("Sorting").

## Impact

- `api/openapi.yaml`:
  - the new operation `updateCatalogColumns`;
  - the schemas `CatalogColumnConfig`, `CatalogSystemField`, `CatalogJoinDefinition`, `CatalogSavedFilter`, `CatalogFilterExpression` and `CatalogSavedView`, which type `Catalog`;
  - the description of `queryCatalog`.
- `backend/`:
  - `io.oea.app.catalog` (column validation, the query with configured columns and default sort);
  - `io.oea.core.catalog.CatalogQueryStore` (system fields, enum order);
  - no migration: the columns live in `catalogs.config`.
- `frontend/`: the dialog in SCR-020, the API functions, the story and the messages in English and German.
- `manual/`: "Catalogs" in English and German.
- `business-objects/catalog.md` (ColumnConfig as implemented), `docs/data-model.puml` (`config.columns`), `docs/audit-event-catalog.md`, US-047, REQ-044, REQ-046, the sprint plan.
