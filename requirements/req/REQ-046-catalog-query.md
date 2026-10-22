---
id: REQ-046
title: Execute a catalog query (live data)
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-10-22
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/20-entities/06-core-entity-types.md
  adrs:
    - ADR-013
    - ADR-019
    - ADR-032
    - ADR-044
supersedes: []
superseded_by: []
---

# REQ-046: Execute a catalog query (live data)

## Statement

On demand, the system MUST execute a live query against the architecture repository for a [Catalog](../../business-objects/catalog.md) and return the entities of the `primaryEntityType` as a paginated list. For each entity, the values of the configured `columns` (attributes) as well as the results of all `joinDefinitions` MUST be included. Depending on the active `joinMode`, join results MUST be presented either as row expansion (`rows`) or as an aggregated entry (`aggregate`). Active filters (SavedFilter or ad hoc filter) MUST be applicable to the primary entity and to join results.

## Rationale

The catalog is a stored query configuration; the actual value arises only when the system delivers live data. The results must always reflect the current state of the repository, not a cached snapshot. Supporting both join modes (`rows` / `aggregate`) directly in the query endpoint is necessary so that the frontend can do without its own aggregation logic.

## Context

The query logic is a **read-only** operation on the entity repository. The catalog endpoint is a query endpoint, not a mutation endpoint. The repository contains entities from the aggregated current state (all `implemented` solutions in project mode) or from the current plateau (in plateau mode). Join traversal uses the stored connection instances in the repository.

## Type-Specific Fields

### For type=functional

**Inputs (query parameters)**:

| Parameter | Type | Optional | Description |
|---|---|---|---|
| page | integer | optional | Page (0-based), default: 0 |
| pageSize | integer | optional | Entries per page, default: 50, max: 200 |
| sort | string | optional | Exactly one `{column}:{asc\|desc}` pair; the column is a key of the returned `columns`; default: `name` ascending. Rows without a value in the sort column come last |
| joinMode | object | optional | `{ joinDefinitionId: "rows" \| "aggregate", ... }` – overrides catalog/JoinDefinition defaults for this call |
| filter | object | optional | Ad hoc FilterExpressions as JSON; in addition to active SavedFilters |
| savedFilterIds | string[] | optional | IDs of SavedFilters to be applied additionally |

**Processing**:

1. Load all entities of the `primaryEntityType` from the repository
2. Apply active filters (savedFilterIds + ad hoc `filter`) as an AND combination (AND between filter sets; within a SavedFilter according to its `logicalOperator`)
3. For each filtered entity: resolve the configured `columns` attributes; return unknown or missing attribute values as `null`. As long as a catalog has no configured columns (REQ-044), the columns are `name` and, if the primary entity type maps a property named `Status` with visibility `public`, that property
4. For each JoinDefinition: traverse the connection graph; structure results according to the active joinMode:
   - `rows`: each combination (primary entity × join result) is delivered as its own row; the primary entity with attributes repeats
   - `aggregate`: primary entity once; join results as an array in the field `joinResults[joinName]`
5. Apply the sort (after filtering, before pagination); ties are ordered by name
6. Apply pagination; the response contains the `columns` (key, label, sortable), the `rows`, the page metadata `page` (`page`, `pageSize`, `totalElements`, `totalPages`) and the applied `sort` (`null` for the default order)

**Outputs**:

```json
{
  "columns": [
    { "key": "name", "label": "Name", "sortable": true },
    { "key": "status", "label": "Status", "sortable": true }
  ],
  "page": { "page": 0, "pageSize": 50, "totalElements": 42, "totalPages": 1 },
  "sort": null,
  "rows": [
    {
      "entityId": 17,
      "values": { "name": "CRM-System", "status": "active" },
      "joinResults": {
        "Interfaces": [
          { "name": "REST-API", "protocol": "REST" },
          { "name": "SFTP-Export", "protocol": "SFTP" }
        ]
      }
    }
  ]
}
```

In `rows` mode for the same catalog:

```json
{
  "rows": [
    { "entityId": 17, "values": { "name": "CRM-System", "status": "active" }, "joinResults": { "Interfaces": { "name": "REST-API", "protocol": "REST" } } },
    { "entityId": 17, "values": { "name": "CRM-System", "status": "active" }, "joinResults": { "Interfaces": { "name": "SFTP-Export", "protocol": "SFTP" } } }
  ]
}
```

## Acceptance Criteria

**AC1** (Happy path, aggregate mode):

- Given: catalog "Application Inventory", primaryEntityType=ApplicationComponent, 2 columns (name, status), 1 join (DataFlow→Interface, aggregate); the repository contains CRM-System (status=active) with 2 interfaces
- When: `GET /api/v1/catalogs/{id}/query`
- Then: the response contains 1 row for CRM-System; `joinResults.Interfaces` is an array with 2 entries

**AC2** (rows mode):

- Given: as AC1
- When: `GET /api/v1/catalogs/{id}/query?joinMode[join-id]=rows`
- Then: the response contains 2 rows (one per interface); `values.name` = "CRM-System" in both rows

**AC3** (Filter):

- Given: the repository contains 5 ApplicationComponents, 2 of them with status=active
- When: `GET /api/v1/catalogs/{id}/query?filter[status][eq]=active`
- Then: `page.totalElements=2`; only the 2 active entities in `rows`

**AC4** (Entity without join results):

- Given: ERP-Core (ApplicationComponent) has no DataFlow connections
- When: query in aggregate mode
- Then: ERP-Core appears in the response with `joinResults.Interfaces = []`; is NOT excluded from the result set (LEFT JOIN semantics)

**AC5** (Sorting and pagination):

- Given: 60 ApplicationComponents
- When: `GET /api/v1/catalogs/{id}/query?page=1&pageSize=20&sort=name:asc`
- Then: `page.totalElements=60`; `rows` contains entries 21–40 alphabetically by name

**AC6** (Empty repository):

- Given: primaryEntityType has no entities in the repository
- When: query
- Then: HTTP 200 with `page.totalElements=0`, `rows=[]`; no error

## Dependencies

- Blocked by: REQ-043 (the catalog must exist), REQ-044 (columns configured), REQ-039 (repository data basis in project mode)
- Enables: REQ-049 (join mode switch uses this endpoint with joinMode override)

## Implementation Notes

- Endpoint: `GET /api/v1/catalogs/{id}/query` (`queryCatalog` in `api/openapi.yaml`); paging and sort follow the contract conventions `PageParam`, `PageSizeParam`, `SortParam` and `PageMeta` (change us-049-show-catalog-data, design D1)
- Implemented in change us-049-show-catalog-data without joins and filters (AC5, AC6); AC1–AC4 follow with the join and filter stories; `joinResults` is added to the rows then
- Invalid paging or sort parameters are answered with 400 (`validation-failed`, `invalidFields`); another person's personal catalog with 404 (ADR-032); soft-deleted entities are not returned (ADR-019); only properties with visibility `public` appear until REQ-160
- LEFT JOIN semantics: entities without join hits are returned anyway (not filtered)
- Filter expression serialization as a query parameter: `filter[attributeName][operator]=value`; for the `in` operator as CSV or array
- Performance: for large repositories an indexed lookup on EntityType + FilterAttributes is necessary; NFR not yet defined (to follow)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; rows/aggregate modes, LEFT JOIN semantics, pagination |
| 0.2.0 | 2026-10-22 | Change us-049-show-catalog-data | Paging and sort aligned with the contract conventions (`page`, `pageSize` ≤ 200, `sort`, `PageMeta`; design D1); response with `columns`, `rows`, `sort`; default columns |
