# US-065: Lineage Query — Query Data Path Upstream/Downstream

**ID**: US-065
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Data Architect, I want to query via API all source systems (upstream) and all consumers (downstream) for a data object – so that I immediately see which path a data object takes through the system landscape and which systems would be affected by a schema change.

## Reference

**Use Case**: [UC-08](../use-cases/UC-08-model-data-lineage.md)
**Persona**: Lukas – Senior Data Architect (SH-02)
**Requirements**: [REQ-062](../req/REQ-062-lineage-graph-api.md)

## Acceptance Criteria

**AC1** (downstream end-to-end):
- Given: SAP ERP(1) →[data-flow:5]→ DWH(2) →[data-flow:6]→ BI Tool(14); both flows carry DataObject 42 via carries-data
- When: `GET /api/v1/lineage?entityId=42&direction=downstream`
- Then: Response contains SAP ERP, DWH, BI Tool in correct order; `path=[1,2,14]`; `cycleDetected=false`

**AC2** (upstream):
- Given: same configuration
- When: `GET /api/v1/lineage?entityId=42&direction=upstream`
- Then: Response contains SAP ERP as source; path backwards correct

**AC3** (impact mode):
- When: `direction=impact` for DataObject 42
- Then: same nodes as downstream; all annotated with `impact: true`

**AC4** (cycle detection):
- Given: Cycle A→B→C→A, all with DataObject 42
- When: Lineage query
- Then: `cycleDetected=true`; no timeout; traversed subgraph returned

**AC5** (no results):
- Given: DataObject 42 not referenced in any carries-data
- When: Lineage query
- Then: `nodes=[]`, `edges=[]`, HTTP 200

## Technical Notes

- BFS traversal via carries-data → data-flow → system; max 50 hops
- Cycle detection via visited set

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: downstream, upstream, impact, cycle, empty
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
