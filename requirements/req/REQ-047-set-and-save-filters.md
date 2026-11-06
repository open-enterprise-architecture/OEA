---
id: REQ-047
title: Set filters and save them as a SavedFilter
type: functional
priority: must
status: proposed
version: 0.3.0
created: 2026-06-26
last_updated: 2026-11-06
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
  business_rules:
    - catalog:BR-04
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-047: Set filters and save them as a SavedFilter

## Statement

The system MUST allow all catalog visitors to set filter expressions on attributes of the primary entity and of joined target entities. Supported operators: `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte`, `lte`. Multiple expressions within a filter MUST be combinable via `AND` or `OR`. Filter expressions that have been set MUST be persistable in the catalog as named [SavedFilters](../../business-objects/catalog.md); saved filters MUST be offered as quick selection. (A SavedView keeps its own filter expressions and references no SavedFilter, change us-053-saved-views.)

## Rationale

Without a filter function, the catalog visitor has to scroll through all entities. Since repositories quickly grow to hundreds of entities, filters are indispensable. Persisting as a SavedFilter saves repetitive effort for frequently used restrictions (e.g., "Active systems only", "Business layer only").

## Context

`attributePath` is a column key of the query result (realigned 2026-11-05, us-051-catalog-filters): `status` or another visible primary column, `source.name`/`target.name` in a connection catalog, or `{joinId}.{key}` for a visible column of a join. A filter on a join column restricts both (owner decision 2026-11-05): an entry appears only if it has at least one matching join result, and the join shows only the matching results.

SavedFilters belong to the catalog configuration and are changed under the rule of the columns (REQ-044): `catalog:manage` for an instance catalog, only the creator for a personal catalog; every reader sees them in the quick selection. The `scope` of a SavedFilter was dropped (owner decision 2026-11-05).

## Type-Specific Fields

### For type=functional

**Inputs (create SavedFilter)**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | 1–100 characters, unique within the catalog regardless of case |
| description | string | optional | At most 1000 characters |
| logicalOperator | enum | mandatory | `and` (default) or `or` |
| expressions | FilterExpression[] | mandatory | 1–20 expressions |
| expressions[].attributePath | string | mandatory | Column key of the query result (BR-04) |
| expressions[].operator | enum | mandatory | One of the 12 supported operators |
| expressions[].value | any | optional | Empty for `isNull`/`isNotNull`; array for `in`/`notIn` |

**Processing (save)**:

1. Check the right to configure the catalog (REQ-044); otherwise 403
2. Validate name, description, operator and expressions: `attributePath` must name a column of the query result, the operator must fit the column type (BR-12), the value must be valid; otherwise 422 naming the field, e.g. `expressions[0].attributePath`
3. Check `name` for uniqueness within the catalog (case-insensitive) and the limit of 50 saved filters
4. Persist the SavedFilter in the catalog and write the audit event `catalog.filter-added`

**Processing (apply at runtime, ad hoc)**:

Filters are passed as query parameters to `GET /api/v1/catalogs/{id}/query` (cf. REQ-046): one `filter={key}:{operator}[:{value}]` per filter (at most 20) and `filterMode=and|or`. They act on the live query without changing the catalog; an invalid filter answers 400 `filter`. Activating a saved filter passes its expressions this way (realigned 2026-11-05: replaces `savedFilterIds[]`).

**Outputs**:

- HTTP 201 Created with the updated catalog (including the new SavedFilter with id)
- HTTP 422 Unprocessable Entity on validation errors

## Acceptance Criteria

**AC1** (Create SavedFilter, happy path):

- When: `POST /api/v1/catalogs/{id}/filters` with `{ name: "Active systems only", logicalOperator: "and", expressions: [{attributePath: "status", operator: "eq", value: "active"}] }`
- Then: HTTP 201; SavedFilter in catalog; offered in quick selection on the next query

**AC2** (Apply SavedFilter):

- Given: SavedFilter "Active systems only" with id=filter-1 exists
- When: the reader activates it, i.e. `GET /api/v1/catalogs/{id}/query?filter=status:eq:active`
- Then: only entities with status=active in the response; `totalCount` reduced accordingly

**AC3** (BR-04 – invalid attributePath):

- Given: primaryEntityType ApplicationComponent has no attribute `nonexistent`
- When: FilterExpression with `attributePath: "nonexistent"`
- Then: HTTP 422 naming `expressions[0].attributePath`

**AC4** (Dot notation on join):

- Given: JoinDefinition "Interfaces" with targetEntityType Interface, attribute `protocol`
- When: FilterExpression with `attributePath: "{joinId}.protocol", operator: "eq", value: "REST"`
- Then: HTTP 201; applied, it keeps the entries with a REST interface and filters the join results to protocol=REST

**AC5** (OR combination):

- When: SavedFilter with `logicalOperator: "or"` and two expressions: `status=active` OR `status=deprecated`
- Then: the query returns entities with status=active or status=deprecated

**AC6** (Permission check, realigned 2026-11-05: `scope` dropped):

- Given: Franz has only the visitor role (no `catalog:manage`)
- When: Franz tries to create a SavedFilter in an instance catalog
- Then: HTTP 403; in his own personal catalog the SavedFilter is saved

**AC7** (dropped 2026-11-06, owner decision in us-053-saved-views): a SavedView keeps a snapshot of its filters and references no SavedFilter, so deleting a SavedFilter changes no view.

## Dependencies

- Blocked by: REQ-043 (the catalog must exist)
- Related: REQ-046 (filters are evaluated in the query), REQ-048 (SavedViews reference SavedFilter IDs)

## Implementation Notes

- Endpoints: `POST /api/v1/catalogs/{id}/filters` (`addCatalogFilter`, 201 with the catalog), `DELETE /api/v1/catalogs/{id}/filters/{filterId}` (`removeCatalogFilter`, 200 with the catalog)
- `in`/`notIn` values: as a JSON array without commas in the request body; as a comma-separated value for runtime filters
- Realized by OpenSpec change `us-051-catalog-filters`, commit 2e320f3 (#190) (AC1–AC6; AC7 dropped with us-053-saved-views): column keys as `attributePath`, the operators per column type (catalog BR-12), at most 20 filters per query and per saved filter, at most 50 saved filters per catalog, `available` for saved filters whose column is gone; removing a join removes its expressions (BR-04); audit events `catalog.filter-added`, `catalog.filter-removed`. Tests: `CatalogFilterTests`, `CatalogPage.spec.ts`, `features/catalog/catalog-filters.feature`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; 12 operators, dot notation for join attributes, scope=personal/instance |
| 0.3.0 | 2026-11-06 | Change us-053-saved-views | AC7 dropped: views keep their own filter expressions |
| 0.2.0 | 2026-11-05 | Change us-051-catalog-filters | Column keys instead of dot notation; join filters restrict entries and results; `scope` and AC6 replaced by the permission rule of the columns; runtime parameters `filter` and `filterMode` instead of `savedFilterIds[]`; AC7 follows with saved views |
