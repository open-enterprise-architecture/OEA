---
id: REQ-047
title: Set filters and save them as a SavedFilter
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

The system MUST allow all catalog visitors to set filter expressions on attributes of the primary entity and of joined target entities. Supported operators: `eq`, `neq`, `contains`, `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`, `gte`, `lte`. Multiple expressions within a filter MUST be combinable via `AND` or `OR`. Filter expressions that have been set MUST be persistable in the catalog as named [SavedFilters](../../business-objects/catalog.md); saved filters MUST be offered as quick selection. Deleting a SavedFilter that is referenced in a SavedView MUST remove the reference from the SavedView.

## Rationale

Without a filter function, the catalog visitor has to scroll through all entities. Since repositories quickly grow to hundreds of entities, filters are indispensable. Persisting as a SavedFilter saves repetitive effort for frequently used restrictions (e.g., "Active systems only", "Business layer only").

## Context

`attributePath` can be either an attribute of the primary entity (simple name, e.g., `status`) or an attribute of a joined target entity (dot notation, e.g., `Interfaces.protocol`, where `Interfaces` is the `name` of the JoinDefinition). Filters on join attributes filter the join results, not the primary entity (LEFT JOIN logic is retained; a primary entity without matching join results after the filter has an empty joinResults array but is not hidden).

A SavedFilter with `scope=instance` (visible to all visitors) can only be created by a catalog manager. Visitors without write permission can only create `scope=personal` SavedFilters.

## Type-Specific Fields

### For type=functional

**Inputs (create SavedFilter)**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | Readable name, unique within the catalog |
| description | string | optional | Short description |
| logicalOperator | enum | mandatory | `and` (default) or `or` |
| expressions | FilterExpression[] | mandatory | At least 1 expression |
| expressions[].attributePath | string | mandatory | Attribute name or "JoinName.attributeName" (BR-04) |
| expressions[].operator | enum | mandatory | One of the 12 supported operators |
| expressions[].value | any | optional | Empty for `isNull`/`isNotNull`; array for `in`/`notIn` |

**Processing (save)**:

1. Validate `attributePath`: a simple attribute name must exist in the primaryEntityType; dot notation must refer to a valid JoinDefinition and its target entity attribute (BR-04); otherwise 422
2. Check `name` for uniqueness within the catalog (case-insensitive)
3. Check `scope`: `instance` only if the actor has catalog manager permission; otherwise only `personal`
4. Persist the SavedFilter in the catalog

**Processing (apply at runtime, ad hoc)**:

Filters are passed as query parameters to `GET /api/v1/catalogs/{id}/query` (cf. REQ-046) and act on the live query without changing the catalog.

**Outputs**:

- HTTP 201 Created with the updated catalog (including the new SavedFilter with id)
- HTTP 422 Unprocessable Entity on validation errors

## Acceptance Criteria

**AC1** (Create SavedFilter, happy path):

- When: `POST /api/v1/catalogs/{id}/filters` with `{ name: "Active systems only", logicalOperator: "and", expressions: [{attributePath: "status", operator: "eq", value: "active"}] }`
- Then: HTTP 201; SavedFilter in catalog; offered in quick selection on the next query

**AC2** (Apply SavedFilter):

- Given: SavedFilter "Active systems only" with id=filter-1 exists
- When: `GET /api/v1/catalogs/{id}/query?savedFilterIds[]=filter-1`
- Then: only entities with status=active in the response; `totalCount` reduced accordingly

**AC3** (BR-04 – invalid attributePath):

- Given: primaryEntityType ApplicationComponent has no attribute `nonexistent`
- When: FilterExpression with `attributePath: "nonexistent"`
- Then: HTTP 422 "The attribute ‹nonexistent› is not defined for the type ‹ApplicationComponent›"

**AC4** (Dot notation on join):

- Given: JoinDefinition "Interfaces" with targetEntityType Interface, attribute `protocol`
- When: FilterExpression with `attributePath: "Interfaces.protocol", operator: "eq", value: "REST"`
- Then: HTTP 201; the filter filters join results to protocol=REST

**AC5** (OR combination):

- When: SavedFilter with `logicalOperator: "or"` and two expressions: `status=active` OR `status=deprecated`
- Then: the query returns entities with status=active or status=deprecated

**AC6** (Scope check):

- Given: Franz has only the visitor role (no write permission)
- When: Franz tries to create a SavedFilter with scope=instance
- Then: HTTP 403; Franz can only create SavedFilters with scope=personal

**AC7** (Delete SavedFilter and clean up SavedView):

- Given: SavedView "Compact view" references SavedFilter id=filter-1
- When: `DELETE /api/v1/catalogs/{id}/filters/filter-1`
- Then: HTTP 200; SavedFilter deleted; SavedView cleaned up (filter-1 removed from `activeFilterIds`)

## Dependencies

- Blocked by: REQ-043 (the catalog must exist)
- Related: REQ-046 (filters are evaluated in the query), REQ-048 (SavedViews reference SavedFilter IDs)

## Implementation Notes

- Endpoints: `POST /api/v1/catalogs/{id}/filters`, `DELETE /api/v1/catalogs/{id}/filters/{filterId}`
- Dot notation validation: extract `joinName` from the first segment; look up the JoinDefinition with this `name`; then check the attribute on the `targetEntityType`
- `in`/`notIn` values: as a JSON array in the request body; as a comma-separated query parameter for runtime filters

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; 12 operators, dot notation for join attributes, scope=personal/instance |
