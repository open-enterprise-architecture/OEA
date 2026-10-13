---
id: REQ-071
title: Catalog query latency
type: non-functional
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
    - entity
  business_rules: []
  stakeholders:
    - SH-03
    - SH-01
    - SH-07
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-071: Catalog query latency

## Statement

The system MUST complete live catalog queries (REQ-046) within a defined time budget — even with multiple configured joins and activated filters.

## Rationale

The catalog is the primary working surface for all EA users (SH-03, SH-01, SH-07). Every interaction — setting a filter, switching the join mode, paging — triggers a live query. If the response time exceeds 1 second, the perceived immediacy of the query is lost; users lose their workflow. This is the most frequent and thus most latency-critical read path in the system after login.

## Context

Applies to the server-side portion of the catalog query (`GET /api/v1/catalogs/{id}/data`): entity load, join traversal, filter and aggregation time. Network time client↔server is not part of this requirement.

## Type-Specific Fields

### For type=non-functional

**Category**: performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| p95 catalog query without join | < 200ms | 400ms | 800ms | 10,000 primary entities, no filters |
| p95 catalog query with 3 joins | < 500ms | 800ms | 1,500ms | 10,000 primary entities, max. 5 results per row per join |
| p95 catalog query with filter + 3 joins | < 600ms | 1,000ms | 2,000ms | 10,000 primary entities, 1 SavedFilter with 2 conditions |
| p95 paginated call (page 2+) | < 200ms | 400ms | 800ms | same scope; cursor-based pagination assumed |

## Acceptance Criteria

**AC1** (Without join):

- Given: Repository with 10,000 ApplicationComponent entities; catalog without joins; no filter
- When: 95% of the queries are measured
- Then: Server processing time < 200ms

**AC2** (3 joins, aggregate mode):

- Given: Repository with 10,000 ApplicationComponent entities; catalog with 3 JoinDefinitions; on average 5 join results per entity
- When: 95% of the queries are measured
- Then: Server processing time < 500ms

**AC3** (Filter + 3 joins):

- Given: as AC2 + 1 SavedFilter with 2 AND-combined conditions
- When: 95% of the queries are measured
- Then: Server processing time < 600ms

## Verification Method

- [x] Method: test (automated, load test)
- [x] Test setup: synthetic repository with 10,000 entities + 3 JoinDefinitions + SavedFilter
- [x] Measuring tool: load/performance test tool (choice: solution design)
- [x] Pass criterion: p95 values according to table
- [ ] Integrated in CI: yes, as a performance gate after the walking skeleton

## Dependencies

- **Prerequisites**: REQ-043 (Catalog), REQ-046 (catalog query)
- **Conflicts**: REQ-065 (n-connection traversal must not increase latency excessively; join on connection primaries must meet the same SLA)

## Risks of Non-Fulfillment

- Users perceive the catalog as sluggish; acceptance drops; return to Excel inventories

## Trade-offs

- Index strategy on `entityTypeId`, `sourceEntityId`, `targetEntityId` mandatory; query plan must be carefully reviewed
- Caching of join results is contraindicated (live data promise per REQ-046); instead: DB query optimization and cursor pagination

## Implementation Notes

- Database indexes: `(entityTypeId)`, `(sourceEntityId)`, `(targetEntityId)`, `(entityTypeId, name)` mandatory
- Cursor pagination (keyset) instead of OFFSET for stable performance on page N

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
