---
id: REQ-048
title: Create a SavedView and apply it on open
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
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

A SavedView does not change the stored `columns` configuration of the catalog; it is an overlay applied on open. The `columnOrder` in the SavedView overrides the `sortOrder` of the `ColumnConfig`; only the columns named in `columnOrder` are visible. Columns that do not exist in the catalog's `columns` configuration are not allowed in `columnOrder` (BR-05).

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | Readable name, unique within the catalog |
| description | string | optional | |
| columnOrder | string[] | optional | Ordered list of attributeNames; only from `catalog.columns`; missing columns = hidden |
| activeFilterIds | string[] | optional | IDs of SavedFilters of this catalog |
| joinModeOverrides | JoinModeOverride[] | optional | Per JoinDefinitionId: `rows` or `aggregate` |
| isDefault | boolean | mandatory | true = activated automatically when the catalog is opened |

**Processing**:

1. Validate `columnOrder` entries against the `catalog.columns.attributeName` list (BR-05); 422 on unknown attribute names
2. Validate `activeFilterIds` against the `catalog.savedFilters` IDs; 422 on unknown IDs
3. If `isDefault=true`: set all other SavedViews of this catalog to `isDefault=false` (BR-06)
4. Validate `joinModeOverrides[].joinDefinitionId` against `catalog.joinDefinitions`; 422 on unknown IDs
5. Persist the SavedView in the catalog

**Outputs**:

- HTTP 201 Created with the updated catalog (including the new SavedView with id)
- HTTP 200 OK on update (PUT)
- HTTP 422 Unprocessable Entity on validation errors

## Acceptance Criteria

**AC1** (Create SavedView, happy path):
- Given: the catalog has columns [name, status, vendor] and SavedFilter "Active systems only"
- When: `POST /api/v1/catalogs/{id}/views` with `{ name: "Compact view", columnOrder: ["name", "status"], activeFilterIds: ["filter-1"], isDefault: true }`
- Then: HTTP 201; SavedView saved; isDefault=true

**AC2** (BR-06 – default switch):
- Given: SavedView A with isDefault=true exists
- When: SavedView B is created with isDefault=true
- Then: HTTP 201; SavedView B isDefault=true; SavedView A isDefault=false (automatically)

**AC3** (BR-05 – invalid columnOrder entry):
- Given: the catalog has no column "unknown"
- When: `columnOrder: ["name", "unknown"]`
- Then: HTTP 422 "The attribute ‹unknown› is not in the catalog's column configuration"

**AC4** (Apply default view on open):
- Given: the catalog has SavedView "Compact view" with isDefault=true; columnOrder=[name, status]; activeFilterIds=[filter-active-systems]
- When: a visitor opens the catalog
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

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; BR-05/BR-06, default view mechanism |
