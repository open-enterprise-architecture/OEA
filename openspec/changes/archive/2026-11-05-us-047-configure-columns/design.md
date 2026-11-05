# Design

## Context

- **Storage.** `catalogs.config` (JSONB, V7) holds `columns`, `joinDefinitions`, `savedFilters` and `savedViews`, all empty so far. The contract types them as arrays of untyped objects (#64).
- **Query.** `CatalogQueries.query` returns the default columns: `name`, and the property `Status` if the primary metatype maps it with visibility `public`. Column keys are `name` or the `sqlColumnName` of a property. The keys are used in `CatalogRow.values` and in `sort`. `CatalogQueryStore` sorts enum values by text (#65).
- **Visibility.** `Catalogs.get(principal, id)` answers 404 for an unknown catalog and for another person's personal catalog (ADR-032 §8 L3). Creating an instance catalog needs `catalog:manage` (a contract condition enforced by the handler, with a probe).
- **Storage names.** Property storage names match `^[a-z][a-z0-9_]{0,62}$` and never are `name`, `description`, `created_at`, `updated_at` and others.
- **SCR-020.** `CatalogPage.vue` shows the table with sortable headers and keeps page and sort in the URL.

## Goals / Non-Goals

**Goals:**

- Configure columns through the API and in SCR-020.
- The query honours the configured columns, the default sort, sortability and the enum order.
- Typed catalog schemas.

**Non-Goals:**

- Operations for joins, saved filters and saved views.
- Columns of joined metatypes.
- Optimistic locking of catalogs.

## Decisions

### D1 – Contract

**New operation `updateCatalogColumns`** (`PUT /catalogs/{id}/columns`):

- The body is `CatalogColumnsUpdate` with a required `columns` array of `CatalogColumnConfig`.
- The answer is the changed `Catalog`.
- **Authorization:** `x-oea-authorization: permissions [entity:read]` with the condition "the catalog has scope `instance`" → `catalog:manage`. The handler enforces it, with `@ConditionsEnforcedByHandler`, the role `viewer` and `probePath` on the nil UUID (D3).
- **Errors:** 422 with `invalidFields` such as `columns[2].propertyDefId`, and 403.

**`CatalogColumnConfig`:**

- exactly one of `field` (`CatalogSystemField`) and `propertyDefId`;
- `displayLabel`: optional, 1–100 characters after trimming;
- `visible` (default `true`) and `sortable` (default `true`);
- `defaultSortDirection` (`asc`, `desc` or `null`).

**`CatalogSystemField`:** `name`, `description`, `externalId`, `createdAt`, `updatedAt` and `entityVersion`.

**Typed schemas (#64)** after `business-objects/catalog.md`, all `readOnly` in `Catalog` until their stories:

- `CatalogJoinDefinition`, with `targetColumns` of `CatalogColumnConfig`;
- `CatalogSavedFilter` with `CatalogFilterExpression`;
- `CatalogSavedView` with `CatalogJoinModeOverride`.

`Catalog.columns` becomes `CatalogColumnConfig[]`.

### D2 – Validation and storage

`Catalogs.updateColumns(principal, id, columns)` validates and stores `config.columns`. The other keys of `config` stay untouched.

**Checks:**

1. Each entry names exactly one attribute.
2. A `propertyDefId` is a non-retired property with visibility `public`, mapped today to the primary metatype or one of its base types.
3. No attribute appears twice.
4. Labels are within the length limit.
5. At least one entry is visible.
6. At most one entry has `defaultSortDirection`.
7. At most 100 columns.

**Storage.** Each column is stored as `{"field": …}` or `{"propertyDefId": …}`, with `displayLabel`, `visible`, `sortable` and `defaultSortDirection`. An empty list restores the default columns.

**Audit.** The change sets `last_modified_*` and writes `catalog.columns-changed`, with the number of columns and the visible ones, in the same transaction.

### D3 – Who configures

1. `Catalogs.updateColumns` loads the catalog for its scope. Without `catalog:manage`, the only catalogs a principal may change are their own personal ones. Every other ID gets 403 (`PermissionDeniedException`): instance catalogs, other persons' personal catalogs and unknown IDs.
2. With `catalog:manage`, an unknown ID and another person's personal catalog get 404.

This keeps the existence of other personal catalogs hidden from everyone, and lets the probe (a `viewer` on the nil UUID) receive 403.

### D4 – Query with configured columns

**Columns.** The columns are the visible configured entries, in their order.

- An entry whose property is no longer mapped, retired or `public` is skipped.
- If nothing visible remains, the default columns apply.

**Keys:**

- system fields use their field name (`name`, `description`, `externalId`, `createdAt`, `updatedAt`, `entityVersion`; none matches the pattern of storage names except the reserved ones);
- properties use their `sqlColumnName`, as today.

**Labels.** A label is `displayLabel`, otherwise the property name or the English field label ("Name", "Description", "External ID", "Created", "Updated", "Version"). The portal translates the field labels when no `displayLabel` is set (D6).

**Values.** System fields come from the entity row: dates as ISO 8601 UTC, the version as a number in text form.

**Sort:**

- The `sort` parameter must name a returned column whose `sortable` is true; otherwise 400 `sort`.
- Without `sort`, the column with `defaultSortDirection` applies and is reported in `CatalogQueryResult.sort`. Without such a column, the order is name ascending and `sort` is `null`, as today.

**Store.** `CatalogQueryStore.page` takes a list of columns, each a system field or a property, and a sort index.

### D5 – Enum order (#65)

When the sort column is an enum property, the store orders by `property_enum_values.sort_order` of the stored value. Values outside the enum and missing values come last, then the name ascending, then the ID. The other types keep the order of US-049.

### D6 – SCR-020 dialog "Configure columns"

**The button** "Configure columns" in the catalog header is shown with `catalog:manage` for an instance catalog, and always for a personal catalog, because only its creator sees it.

**The dialog**:

- **Attributes.** It loads the catalog, the primary metatype (`getMetatype`, with inherited mappings) and the property definitions. It lists all attributes: first the configured ones in their order, then the others hidden.
- **Each row** has a drag handle, up/down buttons, a "Visible" switch, the label input (placeholder: the attribute name) and a "Sortable" switch. A configured property that is no longer available shows the badge "Not available".
- **Default sort.** A select of the column and the direction ("none" by default).
- **Validation.** Saving is blocked with "At least one column must be visible" while no row is visible.
- **Saving** sends every row, so hidden attributes stay in the configuration. Afterwards the table reloads and the sort resets to the default.
- **Drag and drop** uses native HTML5 drag events; there is no new dependency. The buttons cover keyboard use.

**Story** "Column configuration", plus a spec for each scenario. The messages come in English and German.

## Risks / Trade-offs

- **[Last write wins on columns]** → Catalogs have no version yet. Two managers saving at once lose one change. This is acceptable until catalogs get optimistic locking.
- **[Stale columns are skipped silently in the query]** → The dialog marks them "Not available", so a manager sees and removes them.
- **[403 for unknown IDs without `catalog:manage`]** → It differs from the 404 of reads. It is deliberate (D3) and documented in the operation.

## Migration Plan

No schema change: `catalogs.config.columns` exists and is empty. Existing catalogs keep the default columns. A rollback ignores stored columns.
