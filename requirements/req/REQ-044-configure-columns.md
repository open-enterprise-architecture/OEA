---
id: REQ-044
title: Configure the columns of a catalog
type: functional
priority: must
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
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-044: Configure the columns of a catalog

## Statement

The system MUST allow the catalog manager to define and update the `columns` list of a [Catalog](../../business-objects/catalog.md). Each `ColumnConfig` entry defines for an attribute of the `primaryEntityType`: `attributeName` (mandatory, must be a valid attribute of the type), `displayLabel` (optional; default = attributeName), `visible` (boolean, default: true), `sortOrder` (integer, 0-based), and `sortable` (boolean, default: true). At least one column with `visible=true` MUST be present at all times.

## Rationale

Without column configuration, a catalog delivers either all or no attributes. Both are impractical: too many columns overwhelm the viewer; too few add no value. The targeted column selection with customizable label and fixed order is the primary differentiator of a catalog compared to a generic entity list.

## Context

Attributes of the `primaryEntityType` come from two sources: (a) built-in attributes of all EntityTypes (e.g., `id`, `name`, `createdAt`, `status`) and (b) custom attributes from the EntityTypeDefinition in the MetamodelConfiguration (e.g., `vendor`, `supportEndDate`). Both can be used in the column configuration.

The column configuration can be replaced completely (PUT) or supplemented/changed individually (PATCH). On update, existing SavedViews that refer to deleted columns must be cleaned up (remove the affected columns from the `columnOrder` lists of the SavedViews).

## Type-Specific Fields

### For type=functional

**Inputs** (column configuration via PUT):

| Field | Type | Optional | Description |
|---|---|---|---|
| columns | ColumnConfig[] | mandatory | Complete new column list |
| columns[].attributeName | string | mandatory | Attribute name from the primaryEntityType (built-in or custom) |
| columns[].displayLabel | string | optional | Overrides the column header; default = attributeName |
| columns[].visible | boolean | optional | Default: true |
| columns[].sortOrder | integer | mandatory | Order (0 = first column) |
| columns[].sortable | boolean | optional | Default: true |
| columns[].defaultSortDirection | enum | optional | `asc` or `desc`; null = no default sorting |

**Processing**:

1. Validate each `attributeName` against the EntityType in the MetamodelConfiguration; 422 on unknown attribute
2. Check: at least one entry with `visible=true`; otherwise 422
3. Check `sortOrder` for uniqueness (no duplicate positions); optional: renumber automatically
4. Inspect existing SavedViews: remove `columnOrder` entries that refer to now-missing attribute names; inform the manager in the response with a warning list

**Outputs**:

- HTTP 200 OK with the updated catalog object; optional field `warnings: [{type: "savedViewColumnRemoved", savedViewId, removedColumns: [...]}]`
- HTTP 422 Unprocessable Entity on validation errors
- HTTP 403 Forbidden on missing write permission

## Acceptance Criteria

**AC1** (Set columns, happy path):

- Given: catalog "Application Inventory" with primaryEntityType=ApplicationComponent
- When: PUT `/api/v1/catalogs/{id}/columns` with `[{attributeName: "name", sortOrder: 0}, {attributeName: "status", sortOrder: 1, displayLabel: "Operating status"}]`
- Then: HTTP 200; the catalog has 2 columns; the second column has displayLabel "Operating status"

**AC2** (Invalid attribute):

- Given: ApplicationComponent has no attribute `nonExistingField`
- When: PUT with `attributeName: "nonExistingField"`
- Then: HTTP 422 "The attribute ‹nonExistingField› is not defined for the type ‹ApplicationComponent›"

**AC3** (No visible column):

- When: PUT with `visible: false` entries only
- Then: HTTP 422 "At least one column must be visible"

**AC4** (SavedView cleanup):

- Given: SavedView "Compact view" contains `columnOrder: ["name", "vendor"]`; column "vendor" is removed from columns
- When: PUT without the `vendor` column
- Then: HTTP 200; response contains `warnings: [{type: "savedViewColumnRemoved", savedViewId: "...", removedColumns: ["vendor"]}]`; SavedView `columnOrder` is cleaned up to `["name"]`

**AC5** (Order):

- When: columns with sortOrder [2, 0, 1] are sent
- Then: the system orders columns by sortOrder; the first displayed column is the one with sortOrder=0

## Dependencies

- Blocked by: REQ-043 (the catalog must exist)
- Enables: REQ-046 (columns flow into the query response)

## Implementation Notes

- Endpoint: `PUT /api/v1/catalogs/{id}/columns`
- Attribute validation: load the EntityType attribute list from the MetamodelConfiguration; built-in attributes (`id`, `name`, `createdAt`, `status`, `description`) always available
- SavedView cleanup: execute synchronously when removing columns; no separate background process needed

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
