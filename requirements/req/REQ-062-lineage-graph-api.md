---
id: REQ-062
title: Lineage graph API (traversable paths, upstream/downstream/impact)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
  business_rules: []
  stakeholders:
    - SH-02
  concept:
    - concept/20-entities/06-core-entity-types.md
    - concept/30-dynamics/13-business-technology-linking.md
  adrs:
    - ADR-010
supersedes: []
superseded_by: []
---

# REQ-062: Lineage graph API (traversable paths, upstream/downstream/impact)

## Statement

The system MUST provide a dedicated lineage endpoint (`GET /api/v1/lineage`) that, for a given ArchitectureEntity ID, calculates all upstream and/or downstream connected entities by traversing the ArchitectureEntity graph via `carries-data` connections and adjacent `data-flow` connections; the traversal MUST detect cycles (max. 50 hops) and return a complete path as a structured JSON graph.

## Rationale

A lineage diagram without a query API is merely a static visualization — not queryable by tooling, CI pipelines or compliance systems. The lineage API turns the modeled data flows into a genuine data foundation for impact analysis, compliance reports and change management.

## Context

The traversal follows this pattern:
1. Given: DataObject entity with id=X
2. Find all `carries-data` connections whose `targetEntityId=X`
3. For each found `carries-data` connection: `sourceEntityId` is a DataFlow
4. From the DataFlow: read `sourceEntityId` (source system) and `targetEntityId` (target system)
5. Recursively: continue searching from the target system (downstream) or backwards from the source system (upstream)

`direction=impact` returns the same traversal as `downstream`, but annotates each node with `impact: true` — useful for "which systems are affected if this data object changes?"

## Type-Specific Fields

### Query Parameters

| Parameter | Type | Optional | Default | Description |
|---|---|---|---|---|
| entityId | integer | required | | ID of the start entity (typically: data-object or system) |
| direction | enum | optional | `both` | `upstream` (sources), `downstream` (consumers), `both`, `impact` |
| maxDepth | integer | optional | 50 | Maximum traversal depth; minimum 1, maximum 100 |
| entityTypeFilter | string[] | optional | | List only nodes of these EntityType IDs in the result |

### Response Structure

```json
{
  "startEntity": { "id": 42, "name": "Customer master data", "entityTypeId": "data-object" },
  "direction": "both",
  "cycleDetected": false,
  "nodes": [
    { "id": 1, "name": "SAP ERP", "entityTypeId": "application-component" },
    { "id": 2, "name": "Data Warehouse", "entityTypeId": "application-component" },
    { "id": 14, "name": "BI Tool", "entityTypeId": "application-component" }
  ],
  "edges": [
    { "id": 5, "entityTypeId": "data-flow", "sourceEntityId": 1, "targetEntityId": 2,
      "viaCarriesData": 103 },
    { "id": 6, "entityTypeId": "data-flow", "sourceEntityId": 2, "targetEntityId": 14,
      "viaCarriesData": 104 }
  ],
  "path": [1, 2, 14]
}
```

**Error Cases**:
- `entityId` not found → HTTP 404
- `maxDepth` exceeded → HTTP 400
- No permission for the entity → HTTP 403

## Acceptance Criteria

**AC1** (Downstream end-to-end):
- Given: SAP ERP(1) →[data-flow:5, carries-data:103→DataObject:42]→ DWH(2) →[data-flow:6, carries-data:104→DataObject:42]→ BI Tool(14)
- When: `GET /api/v1/lineage?entityId=42&direction=downstream`
- Then: `nodes` contains id=1 (SAP ERP), id=2 (DWH), id=14 (BI Tool); `path=[1,2,14]`; `cycleDetected=false`

**AC2** (Upstream):
- Given: same configuration as AC1
- When: `GET /api/v1/lineage?entityId=42&direction=upstream`
- Then: `nodes` contains id=1 (SAP ERP); DWH only as an intermediate node; path backwards

**AC3** (Cycle detection):
- Given: Cycle A→B→C→A with DataObject 42 in all flows
- When: `GET /api/v1/lineage?entityId=42&direction=downstream`
- Then: `cycleDetected=true`; response contains the traversed subgraph; no timeout/endless loop

**AC4** (EntityType filter):
- When: `entityTypeFilter=application-component`
- Then: Response contains only nodes with `entityTypeId=application-component`; DataFlows appear only in `edges`

**AC5** (Empty result):
- Given: DataObject id=42 is not referenced by any DataFlow via carries-data
- When: Lineage query for id=42
- Then: `nodes=[]`, `edges=[]`, HTTP 200; no error

## Verification Method

- [x] Method: test (automated)
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, as soon as implementation exists

## Dependencies

- **Prerequisites**: REQ-061 (carries-data connections must exist for a meaningful traversal)
- **Downstream effects**: REQ-063 (canvas uses the lineage API to set the 3-dot indicator)

## Implementation Notes

- BFS algorithm (breadth-first search); visited set for cycle detection
- The carries-data connection is the key: the traversal follows `carries-data.source → dataFlow → dataFlow.target`
- No ORM joins over n levels; graph traversal in the service layer (not in SQL)
- Caching: no cache in v1.0 (live query); in case of performance problems, optionally a Redis cache with TTL

## Implementation

- ADR(s): [ADR-010](../../adrs/ADR-010-n-connection-data-lineage.md) (proposed)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
