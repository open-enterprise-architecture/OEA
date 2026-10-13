---
id: REQ-049
title: Switch the join mode of a catalog at runtime
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
  business_rules: []
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

# REQ-049: Switch the join mode of a catalog at runtime

## Statement

The system MUST allow all catalog visitors to change the join mode (`rows` | `aggregate`) per JoinDefinition at runtime without changing the stored catalog configuration. The table MUST be updated after switching without a page reload. The runtime override is passed as a parameter to the query endpoint (REQ-046 `joinMode` parameter). The visitor MAY optionally persist the current mode as a SavedView (REQ-048).

## Rationale

The optimal join mode depends on the analysis context: the enterprise architect wants a compact overview (aggregate) to see many applications at a glance; the data architect wants all interface rows individually (rows) to create a CSV export or to sort the table by interface attributes. The same catalog should satisfy both needs without requiring two separate configurations.

## Context

The runtime override affects only the current query and session of the visitor. Other visitors who open the same catalog at the same time are not affected. The override is held in the frontend state (not persisted on the server) until the visitor explicitly creates a SavedView.

## Type-Specific Fields

### For type=functional

**Inputs (query parameters on the REQ-046 endpoint)**:

| Parameter | Type | Description |
|---|---|---|
| `joinMode[{joinDefinitionId}]` | enum `rows\|aggregate` | Overrides the mode for this JoinDefinition for this call |

Example: `GET /api/v1/catalogs/{id}/query?joinMode[abc-123]=rows&joinMode[def-456]=aggregate`

**Processing**:

1. For each JoinDefinition in the catalog: if `joinMode[joinDefinitionId]` is present in the request parameter, use this mode for the query (overrides `JoinDefinition.defaultJoinMode` and `Catalog.defaultJoinMode`)
2. JoinDefinitions that are not overridden use their configured default
3. The catalog configuration (stored `defaultJoinMode`) REMAINS UNCHANGED

**Outputs**:

- HTTP 200 OK with the query response per REQ-046; join results structured according to the chosen mode
- HTTP 400 Bad Request if `joinDefinitionId` is unknown

## Acceptance Criteria

**AC1** (rows mode via parameter):

- Given: catalog "Application Inventory" with JoinDefinition id=join-1 (DataFlow→Interface), defaultJoinMode=aggregate; CRM-System has 2 interfaces
- When: `GET /api/v1/catalogs/{id}/query?joinMode[join-1]=rows`
- Then: the response contains 2 rows for CRM-System (one per interface); Catalog.joinDefinitions[0].defaultJoinMode remains `aggregate` (unchanged)

**AC2** (aggregate mode, stored default applies):

- Given: as AC1
- When: `GET /api/v1/catalogs/{id}/query` (without joinMode parameter)
- Then: the response contains 1 row for CRM-System with joinResults.Interfaces as an array

**AC3** (No mutation of the catalog):

- After AC1: `GET /api/v1/catalogs/{id}` (retrieve the catalog configuration)
- Then: `joinDefinitions[0].defaultJoinMode = "aggregate"` (unchanged)

**AC4** (Unknown joinDefinitionId):

- When: `joinMode[nonexistent-id]=rows`
- Then: HTTP 400 "JoinDefinition ‹nonexistent-id› not found"

**AC5** (Override several joins at once):

- Given: the catalog has 2 JoinDefinitions (join-1 defaultMode=aggregate, join-2 defaultMode=aggregate)
- When: `?joinMode[join-1]=rows&joinMode[join-2]=aggregate`
- Then: join-1 delivers row expansion; join-2 remains aggregated

## Dependencies

- Blocked by: REQ-045 (JoinDefinitions must be configured), REQ-046 (query endpoint)
- Related: REQ-048 (the runtime override can be saved as a SavedView)

## Implementation Notes

- No separate endpoint needed; the parameter is passed directly to the REQ-046 endpoint
- Frontend: toggle control per JoinDefinition (label: "Rows" / "Compact" or "rows" / "aggregate"); changes the query parameter and triggers a new fetch
- No server-side session state needed; the override lives in the frontend state

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft |
