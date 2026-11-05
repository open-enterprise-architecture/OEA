---
id: REQ-045
title: Add a join definition to a catalog
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-05
author: requirements-engineer
references:
  use_cases:
    - UC-06
  business_objects:
    - catalog
    - metamodel-configuration
  business_rules:
    - catalog:BR-02
    - catalog:BR-03
    - catalog:BR-04
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-045: Add a join definition to a catalog

## Statement

The system MUST allow the catalog manager to add, edit, and remove `JoinDefinition` entries on a [Catalog](../../business-objects/catalog.md). A `JoinDefinition` consists of: `name` (mandatory), `connectionMetatypeId` (mandatory; metatype with `isConnection=true` – BR-02), `joinDirection` (mandatory: `outbound` | `inbound` | `both`), `targetMetatypeId` (mandatory; must be reachable through the connection metatype in the direction by its allowed endpoints – BR-03), `targetColumns` (ColumnConfig[]; attributes of the target metatype), and `defaultJoinMode` (optional; `rows` | `aggregate`; default = Catalog.defaultJoinMode).

## Rationale

Joins are the decisive feature of the catalog that distinguishes it from a simple entity list. Via connections, related entities (e.g., all interfaces of an application) can appear directly in the table view without the viewer having to navigate. Without valid join configuration, faulty queries would arise that do not fit the repository model.

## Context

The join logic in OEA is not based on SQL JOINs across database tables but on the graph traversal of the entity repository: the catalog looks for all connection instances of the chosen type in which the primary entity appears as source (outbound) or target (inbound), and follows the connection to the other endpoint.

**Example – outbound**: ApplicationComponent → DataFlow → Interface: for each ApplicationComponent, all DataFlow connections in which ApplicationComponent is the source are searched; the Interface objects at the other end are the join results.

`joinDirection=both` combines outbound and inbound; duplicate hits (if an entity is both source and target) are deduplicated.

## Type-Specific Fields

### For type=functional

**Inputs**:

| Field | Type | Optional | Description |
|---|---|---|---|
| name | string | mandatory | Readable label of the join (e.g., "Interfaces") |
| connectionMetatypeId | UUID | mandatory | Metatype; must have `isConnection=true` (BR-02) |
| joinDirection | enum | mandatory | `outbound`, `inbound`, `both` |
| targetMetatypeId | UUID | mandatory | Metatype of the target entities; must be reachable via the connection metatype (BR-03) |
| targetColumns | ColumnConfig[] | mandatory | 1–20 entries, at least one visible; `field` or `propertyDefId` of the target metatype as in REQ-044, without default sort |
| defaultJoinMode | enum | optional | `rows` or `aggregate`; default = Catalog.defaultJoinMode |

**Processing**:

1. Validate `connectionMetatypeId` against the MetamodelConfiguration: must exist and have `isConnection=true` (BR-02); otherwise 422
2. Validate `targetMetatypeId` against the allowed endpoints of the connection metatype (taking `joinDirection` into account); 422 if not reachable (BR-03)
3. Validate `targetColumns` against the attributes of the target metatype; 422 naming the column, e.g. `targetColumns[1].propertyDefId`
4. Persist the JoinDefinition with a generated `id` in the catalog

**Outputs**:

- HTTP 201 Created with the updated catalog object (including the new JoinDefinition with id)
- HTTP 422 Unprocessable Entity on validation errors
- HTTP 403 Forbidden on missing write permission

## Acceptance Criteria

**AC1** (Add join, happy path):

- Given: catalog "Application Inventory" with primaryEntityType=ApplicationComponent; the MetamodelConfiguration has ConnectionType `DataFlow` (isConnection=true, allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
- When: `POST /api/v1/catalogs/{id}/joins` with `{ name: "Interfaces", connectionMetatypeId: <DataFlow>, joinDirection: "outbound", targetMetatypeId: <Interface>, targetColumns: [{field: "name"}], defaultJoinMode: "aggregate" }`
- Then: HTTP 201; the catalog contains the new JoinDefinition with a generated id

**AC2** (BR-02 – connectionType is not a connection type):

- Given: `ApplicationComponent` has `isConnection=false`
- When: `connectionMetatypeId: <ApplicationComponent>` in the request
- Then: HTTP 422 naming `connectionMetatypeId`

**AC3** (BR-03 – targetEntityType not reachable):

- Given: DataFlow has `allowedTargetTypes=[Interface]`
- When: `targetMetatypeId: <TechnologyComponent>` with `joinDirection=outbound`
- Then: HTTP 422 naming `targetMetatypeId`

**AC4** (Remove join):

- When: `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- Then: HTTP 200; JoinDefinition removed; existing SavedViews are cleaned of JoinModeOverrides for this join

**AC5** (targetColumns validation):

- Given: Interface does not map the property `nonExistingProp`
- When: targetColumns contains `propertyDefId: <nonExistingProp>`
- Then: HTTP 422 naming the column, e.g. `targetColumns[0].propertyDefId`

## Dependencies

- Blocked by: REQ-043 (the catalog must exist), REQ-036 (connection EntityTypes must be defined in the metamodel)
- Enables: REQ-046 (join results in query), REQ-049 (switch join mode)

## Implementation Notes

- Endpoints: `POST /api/v1/catalogs/{id}/joins`, `PUT /api/v1/catalogs/{id}/joins/{joinId}`, `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- `joinDirection=both`: traverse outbound + inbound; deduplicate results by entityId
- Order of joins: by creation order; optionally reorderable by the manager
- Realized by OpenSpec change `us-048-catalog-joins`, commit 6c54edb (#183): operations `addCatalogJoin` (201 with the catalog), `updateCatalogJoin` and `removeCatalogJoin` (200 with the catalog); at most 10 joins; permissions as for the columns (REQ-044); audit events `catalog.join-added`, `catalog.join-changed`, `catalog.join-removed`; the dialog "Joins" in SCR-020. Tests: `CatalogJoinsTests`, `CatalogPage.spec.ts`, `features/catalog/catalog-joins.feature`

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
| 0.2.0 | 2026-11-05 | Change us-048-catalog-joins | Metatype IDs instead of names, target columns as in REQ-044, `aggregateLabel` dropped, 422 naming the field, DELETE answers the catalog; implementation note |
