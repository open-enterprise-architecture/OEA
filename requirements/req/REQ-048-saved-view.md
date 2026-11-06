---
id: REQ-048
title: Create a SavedView and apply it on open
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-06
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
  business_rules:
    - catalog:BR-05
    - catalog:BR-06
  stakeholders:
    - SH-03
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-048: Create a SavedView and apply it on open

## Statement

The system MUST allow the catalog manager to create named `SavedView` entries within a [Catalog](../../business-objects/catalog.md). A SavedView bundles: `columnOrder` (ordered list of visible attribute names, BR-05), `activeFilterIds` (IDs of SavedFilters of this catalog), and `joinModeOverrides` (optional mode overrides per JoinDefinition). At most one SavedView per catalog MAY have `isDefault=true` (BR-06). When a catalog is opened, the default SavedView – if present – MUST be applied automatically. If a new SavedView is marked as default, an existing default SavedView MUST automatically be set to `isDefault=false`.

## Rationale

Without SavedViews, every visitor has to reconfigure the desired column and filter combination manually each time the catalog is opened. SavedViews allow the catalog manager to preconfigure several context-related views (e.g., one for the daily check, one for the compliance export) and to mark the most important one as default.

## Context

A SavedView does not change the stored `columns` configuration of the catalog; it is an overlay applied on open. The `columnOrder` in the SavedView overrides the order of the `ColumnConfig`; only the columns named in `columnOrder` are visible. Columns that the catalog does not show are not allowed in `columnOrder` (BR-05).

Realigned 2026-11-06 (change us-053-saved-views, owner decisions): a SavedView is a snapshot of the view state – column keys, filter expressions with `filterMode`, join mode overrides and sort – and references no SavedFilter. The query applies the columns of a view (`view` parameter); the client passes its filters, modes and sort as parameters and applies the default view only when the URL holds no view state (ADR-044). SavedViews are changed under the rule of the columns: `catalog:manage` for an instance catalog, the creator for a personal catalog.

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | 1–100 characters, unique within the catalog regardless of case |
| description | string | optional | At most 1000 characters |
| columnOrder | string[] | mandatory | 1–100 column keys of the catalog's primary columns; missing columns = hidden |
| filters | FilterExpression[] | optional | 0–20 expressions on columns of the view or of joins (REQ-047) |
| filterMode | enum | optional | `and` (default) or `or` |
| joinModeOverrides | JoinModeOverride[] | optional | Per JoinDefinitionId: `rows` or `aggregate` |
| sort | {column, direction} | optional | A sortable column of the view, or of a join in rows mode |
| isDefault | boolean | mandatory | true = applied when the catalog is opened without view state in the URL |

**Processing**:

1. Check the right to configure the catalog; otherwise 403
2. Validate name and description, then `columnOrder` against the primary columns (BR-05), `joinModeOverrides[].joinDefinitionId` against the available joins, the filters against the columns of the view and the joins (REQ-047), and the sort; 422 naming the field, e.g. `columnOrder[1]`
3. Check the limit of 20 views
4. If `isDefault=true`: set all other SavedViews of this catalog to `isDefault=false` (BR-06)
5. Persist the SavedView in the catalog and write the audit event

**Outputs**:

- HTTP 201 Created with the updated catalog (including the new SavedView with id)
- HTTP 200 OK on update (PUT)
- HTTP 422 Unprocessable Entity on validation errors

## Acceptance Criteria

**AC1** (Create SavedView, happy path):

- Given: the catalog has columns [name, status, vendor] and SavedFilter "Active systems only"
- When: `POST /api/v1/catalogs/{id}/views` with `{ name: "Compact view", columnOrder: ["name", "status"], filters: [{attributePath: "status", operator: "eq", value: "active"}], isDefault: true }`
- Then: HTTP 201; SavedView saved; isDefault=true

**AC2** (BR-06 – default switch):

- Given: SavedView A with isDefault=true exists
- When: SavedView B is created with isDefault=true
- Then: HTTP 201; SavedView B isDefault=true; SavedView A isDefault=false (automatically)

**AC3** (BR-05 – invalid columnOrder entry):

- Given: the catalog has no column "unknown"
- When: `columnOrder: ["name", "unknown"]`
- Then: HTTP 422 naming `columnOrder[1]`

**AC4** (Apply default view on open):

- Given: the catalog has SavedView "Compact view" with isDefault=true; columnOrder=[name, status]; the filter `status = "active"`
- When: a visitor opens the catalog without view state in the URL
- Then: the table shows only the columns name and status; the filter "Active systems only" is active; `totalCount` filtered accordingly

**AC5** (No default):

- Given: the catalog has no SavedView with isDefault=true
- When: a visitor opens the catalog
- Then: all configured columns visible; no filter preselected

**AC6** (Delete SavedView):

- When: `DELETE /api/v1/catalogs/{id}/views/{viewId}` for the default view
- Then: HTTP 200; view deleted; no other default is set automatically; the next open uses no default

## Dependencies

- Blocked by: REQ-043, REQ-044, REQ-047 (SavedViews reference columns and filters)
- Enables: better usability of the catalog for all visitors

## Implementation Notes

- Endpoints: `POST /api/v1/catalogs/{id}/views`, `PUT /api/v1/catalogs/{id}/views/{viewId}`, `DELETE /api/v1/catalogs/{id}/views/{viewId}`
- Default view propagation: when setting isDefault=true on a view, set all other views to false transactionally (no partial update problem)
- When opening the catalog: the client first queries the catalog configuration and finds the default view; it then uses its parameters for the first query call (REQ-046)
- Realized by OpenSpec change `us-053-saved-views`, commit e8decc0 (#193) (AC1–AC6): `addCatalogView` (201), `updateCatalogView` and `removeCatalogView` (200) with the catalog; the query parameter `view`; at most 20 views; `available` for views whose column, join or sort column is gone; removing a join removes its overrides, filters and sort from the views; audit events `catalog.view-added`, `catalog.view-changed`, `catalog.view-removed`; the view selection, "Save view" and the dialog "Views" in SCR-020. Tests: `CatalogViewTests`, `CatalogPage.spec.ts`, `features/catalog/catalog-views.feature`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; BR-05/BR-06, default view mechanism |
| 0.2.0 | 2026-11-06 | Change us-053-saved-views | Views as snapshots: column keys, `filters` and `filterMode` instead of `activeFilterIds`, `sort`; default view only without view state in the URL; permission rule of the columns; implementation note |
