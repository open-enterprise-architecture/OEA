---
id: REQ-054
title: Configure and validate CatalogQuery DataSource
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
    - catalog
  business_rules: []
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-054: Configure and validate CatalogQuery DataSource

## Statement

The system MUST validate a `CatalogQueryDataSource` configuration for `table` widgets and delegate to the catalog query API (REQ-046) during data calculation (REQ-055). The DataSource MUST require `catalogId` as a mandatory field and MUST check at write time whether the referenced [catalog](../../business-objects/catalog.md) is visible to the requesting user. Optional `savedViewId` and `additionalFilters` supplement the base query.

## Rationale

Embedding catalog data in a dashboard table widget avoids duplicating the query logic. The catalog remains the only configured data source; the dashboard delegates and renders the result in its grid layout.

## Context

`CatalogQueryDataSource` is valid exclusively for `table` widgets (BR-04 in REQ-052). The visibility of the referenced catalog depends on its `scope`: a `scope=personal` catalog of another user must not be used as a DataSource.

## Type-Specific Fields

### DataSource Configuration Fields

| Field | Type | Optional | Description |
|---|---|---|---|
| catalogId | string | required | UUID of the referenced catalog; must be visible to the requester |
| savedViewId | string | optional | UUID of a SavedView in the referenced catalog; null = default view |
| additionalFilters | FilterExpression[] | optional | Additional filters; combined with the catalog's own filters using AND |
| pageSize | integer | optional | Rows per page for the table display; default from widget configuration |

### Validation Rules at Write Time

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | `catalogId` must reference an existing catalog that is visible to the requester | onCreate, onUpdate |
| BR-02 | `savedViewId` (if set) must be a SavedView in the referenced catalog | onCreate, onUpdate |
| BR-03 | If the referenced catalog is deleted, the widget returns an isolated error status on the next data retrieval; the dashboard as a whole remains functional | onCatalogDelete |
| BR-04 | A `scope=personal` catalog of another user must not be used as a DataSource → 403 | onCreate, onUpdate |

### Output During Data Calculation (REQ-055)

The output corresponds to the catalog query response from REQ-046, restricted to `page=0` and `pageSize` from the widget configuration:

```json
{
  "totalCount": 47,
  "page": 0,
  "pageSize": 10,
  "columns": ["name", "status", "Interfaces"],
  "data": [
    { "columns": { "name": "CRM-System", "status": "active" }, "joinResults": { "Interfaces": [...] } }
  ]
}
```

## Acceptance Criteria

**AC1** (Valid DataSource):

- Given: Catalog "Application Inventory" (scope=instance) exists; Kurt is logged in
- When: Create widget with type=table, catalogId=<id>
- Then: HTTP 201; DataSource saved

**AC2** (Invalid catalogId):

- When: Create widget with non-existent catalogId
- Then: HTTP 422 "Catalog not found or not visible"

**AC3** (Another user's personal catalog):

- Given: Franz has a scope=personal catalog
- When: Kurt tries to use this catalog as a DataSource
- Then: HTTP 403 "Catalog not visible"

**AC4** (savedViewId valid):

- Given: Catalog has SavedView "Compact view"
- When: Create widget with savedViewId=<view-id>
- Then: HTTP 201; on data retrieval, the columns and filters of the SavedView are applied

**AC5** (Catalog deleted later):

- Given: Widget with CatalogQueryDataSource exists; catalog is deleted afterwards
- When: GET /api/v1/dashboards/{id}/data
- Then: this widget returns `{ "status": "error", "error": "Catalog not found" }`; other widgets render normally

## Dependencies

- Delegates query logic to: REQ-046 (catalog query)
- Validation at write time: catalog API GET /api/v1/catalogs/{id}
- Used by: REQ-052 (widget validation), REQ-055 (data calculation)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; delegation to catalog API, error isolation, BR-01–04 |
