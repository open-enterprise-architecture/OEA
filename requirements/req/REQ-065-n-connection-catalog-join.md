---
id: REQ-065
title: Catalog join for connection-type primaries and n-connection traversal
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
    - UC-08
  business_objects:
    - catalog
    - metamodel-configuration
    - entity
  business_rules:
    - catalog:BR-01
    - catalog:BR-02
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/20-entities/06-core-entity-types.md
  adrs:
    - ADR-010
supersedes: []
superseded_by: []
---

# REQ-065: Catalog join for connection-type primaries and n-connection traversal

## Statement

For [Catalog](../../business-objects/catalog.md) instances, the system MUST support two extensions of the join logic (REQ-045/046):

1. **Connection type as primary**: `primaryEntityType` MAY be set to an EntityType with `isConnection=true` (e.g., a `data-flow` catalog). In this case, the system returns all connection instances of this type as rows.
2. **n-connection traversal**: If `JoinDefinition.connectionType` is a type whose `sourceEntityId` field may point to connection instances (`allowsConnectionAsSource=true` of the respective source type), the system traverses this join exactly like a normal join — that is: find all instances of the `connectionType` where `sourceEntityId` equals the primary entity (outbound).

## Rationale

With ADR-010 (n-connection), connections can themselves be the source of further connections (e.g., `data-flow` → `carries-data` → `data-object`). For this structure to become queryable in catalogs — e.g., "Show all data flows with the data objects they transport" — two adjustments must take effect:

- A catalog may have `primaryEntityType=data-flow`
- The join traversal must find `carries-data` connections even if their source is a connection instance

Without this extension, n-connection would exist purely in the model but could not be cataloged — a substantial application potential (GDPR report, lineage tables) would remain closed off.

## Context

REQ-045 defines the configuration of JoinDefinitions. REQ-046 defines the query execution. This REQ extends both by the n-connection case without changing their basic structure.

**Distinction from the lineage API (REQ-062)**: The lineage API traverses to arbitrary depth (BFS). This extension is restricted to 1-hop joins in catalogs — starting from the primary entity. Chained joins over more than one hop are NOT the subject of this REQ (planned for v2.0).

## Type-Specific Fields

### Extension for Connection Type as Primary (supplement to REQ-046, step 1)

```text
Step 1 (adjusted): Load all entities of the `primaryEntityType`.
  - If `primaryEntityType.isConnection=false`: all non-connection instances (existing logic)
  - If `primaryEntityType.isConnection=true`: all connection instances of this type
  - Sorting for connection primaries: default by `name` of the connection; alternatively `sourceEntityName` or `targetEntityName` configurable
```

### Extension for n-Connection Traversal (supplement to REQ-046, step 4)

```text
Step 4 (adjusted): Join traversal for each primary entity:
  - For joinDirection=outbound: find all instances of JoinDefinition.connectionType
    WHERE sourceEntityId = primary-entity.id
  - For joinDirection=inbound: find all instances
    WHERE targetEntityId = primary-entity.id
  - Also applies unchanged if the primary entity is itself a connection instance
    (no special case needed — the entity ID model is uniform)
```

### New Validation in REQ-045 (BR-04)

If `primaryEntityType.isConnection=true` is set, at least one JoinDefinition must also be configured whose `connectionType` allows this connection as a source — or an explicit note must appear in the UI that a connection catalog without joins only shows the raw connections (not an error, but a warning in the UI).

## Acceptance Criteria

**AC1** (Connection type as primary):

- Given: MetamodelConfiguration with EntityType `data-flow` (isConnection=true)
- When: Catalog manager creates a catalog with `primaryEntityType=data-flow`
- Then: HTTP 201; catalog is valid; query returns all data-flow instances as rows

**AC2** (n-connection join outbound):

- Given: DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, targetEntityId=42)
- Given: Catalog `Data Flow Overview` (primaryEntityType=data-flow) with JoinDefinition: `{ connectionType: carries-data, joinDirection: outbound, targetEntityType: data-object, targetColumns: [{attributeName: "name"}] }`
- When: Catalog query
- Then: Row for DataFlow id=5; joinResults contains DataObject id=42 with name "Customer master data"

**AC3** (Non-n-connection remains untouched):

- Given: normal catalog with primaryEntityType=application-component and join data-flow outbound
- When: Catalog query
- Then: Result identical to REQ-046 behavior; no regression error

**AC4** (Multiple n-connections aggregated):

- Given: DataFlow id=5 has 2 carries-data connections (data-objects 42 and 43)
- When: Catalog query, aggregate mode
- Then: One row for DataFlow id=5; joinResults contains an array with both data-objects

**AC5** (Connection primary: source and target entity available as columns):

- When: Catalog with primaryEntityType=data-flow; columns `sourceEntityName`, `targetEntityName` configured
- Then: Columns show the names of the source or target entity of each data flow

## Dependencies

- **Extends**: REQ-045 (configure join definition), REQ-046 (catalog query)
- **Prerequisite**: ADR-010 (accepted), REQ-061 (carries-data), entity.md BR-04 (relaxed)
- **Enables**: REQ-064 (GDPR filter on data-flow catalog), UC-08 lineage catalogs

## Implementation Notes

- Entity model: all ArchitectureEntities (elements and connections) share the same integer ID space → no special handling needed in the query
- `sourceEntityName`/`targetEntityName` as virtual columns: JOIN on the entities with `id=sourceEntityId` or `id=targetEntityId` when building the query
- For v2.0: chained joins (app-component → data-flow → data-object in one row via 2 JoinDefinitions in series)

## Implementation

- ADR(s): ADR-010
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
