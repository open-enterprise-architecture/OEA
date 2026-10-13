---
id: REQ-045
title: Add a join definition to a catalog
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

The system MUST allow the catalog manager to add, edit, and remove `JoinDefinition` entries on a [Catalog](../../business-objects/catalog.md). A `JoinDefinition` consists of: `name` (mandatory), `connectionType` (mandatory; EntityType with `isConnection=true` – BR-02), `joinDirection` (mandatory: `outbound` | `inbound` | `both`), `targetEntityType` (mandatory; must be reachable according to `allowedSourceTypes`/`allowedTargetTypes` of the ConnectionType – BR-03), `targetColumns` (ColumnConfig[]; attributes of the target entity), `defaultJoinMode` (optional; `rows` | `aggregate`; default = Catalog.defaultJoinMode), and `aggregateLabel` (optional; label of the aggregate cell).

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
| connectionType | string | mandatory | EntityType name; must have `isConnection=true` (BR-02) |
| joinDirection | enum | mandatory | `outbound`, `inbound`, `both` |
| targetEntityType | string | mandatory | EntityType of the target entity; must be reachable via connectionType (BR-03) |
| targetColumns | ColumnConfig[] | mandatory | At least 1 entry; `attributeName` must be an attribute of targetEntityType |
| defaultJoinMode | enum | optional | `rows` or `aggregate`; default = Catalog.defaultJoinMode |
| aggregateLabel | string | optional | Max. 255 characters; label of the aggregated cell; default = name |

**Processing**:

1. Validate `connectionType` against the MetamodelConfiguration: must exist and have `isConnection=true` (BR-02); otherwise 422
2. Validate `targetEntityType` against `allowedSourceTypes` and `allowedTargetTypes` of the ConnectionType (taking `joinDirection` into account); 422 if not reachable (BR-03)
3. Validate `targetColumns[].attributeName` against the attributes of the `targetEntityType`; 422 on unknown attribute names
4. Persist the JoinDefinition with a generated `id` in the catalog

**Outputs**:

- HTTP 201 Created with the updated catalog object (including the new JoinDefinition with id)
- HTTP 422 Unprocessable Entity on validation errors
- HTTP 403 Forbidden on missing write permission

## Acceptance Criteria

**AC1** (Add join, happy path):

- Given: catalog "Application Inventory" with primaryEntityType=ApplicationComponent; the MetamodelConfiguration has ConnectionType `DataFlow` (isConnection=true, allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
- When: `POST /api/v1/catalogs/{id}/joins` with `{ name: "Interfaces", connectionType: "DataFlow", joinDirection: "outbound", targetEntityType: "Interface", targetColumns: [{attributeName: "name", sortOrder: 0}], defaultJoinMode: "aggregate" }`
- Then: HTTP 201; the catalog contains the new JoinDefinition with a generated id

**AC2** (BR-02 – connectionType is not a connection type):

- Given: `ApplicationComponent` has `isConnection=false`
- When: `connectionType: "ApplicationComponent"` in the request
- Then: HTTP 422 "The type ‹ApplicationComponent› is not a connection type (isConnection=false)"

**AC3** (BR-03 – targetEntityType not reachable):

- Given: DataFlow has `allowedTargetTypes=[Interface]`
- When: `targetEntityType: "TechnologyComponent"` with `joinDirection=outbound`
- Then: HTTP 422 "The EntityType ‹TechnologyComponent› is not reachable via ‹DataFlow› in direction outbound"

**AC4** (Remove join):

- When: `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- Then: HTTP 200; JoinDefinition removed; existing SavedViews are cleaned of JoinModeOverrides for this join

**AC5** (targetColumns validation):

- Given: Interface has no attribute `nonExistingProp`
- When: targetColumns contains `attributeName: "nonExistingProp"`
- Then: HTTP 422 "The attribute ‹nonExistingProp› is not defined for the type ‹Interface›"

## Dependencies

- Blocked by: REQ-043 (the catalog must exist), REQ-036 (connection EntityTypes must be defined in the metamodel)
- Enables: REQ-046 (join results in query), REQ-049 (switch join mode)

## Implementation Notes

- Endpoints: `POST /api/v1/catalogs/{id}/joins`, `PUT /api/v1/catalogs/{id}/joins/{joinId}`, `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
- `joinDirection=both`: traverse outbound + inbound; deduplicate results by entityId
- Order of joins: by creation order; optionally reorderable by the manager

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
