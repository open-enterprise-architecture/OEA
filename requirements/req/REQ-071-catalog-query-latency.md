---
id: REQ-071
title: Catalog query latency
type: non-functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-26
last_updated: 2026-11-07
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

Applies to the server-side portion of the catalog query (`GET /api/v1/catalogs/{id}/query`, and the portal query `GET /api/v1/portal/catalogs/{id}/query`): count, entity load, join traversal, filter and aggregation time. Network time client↔server, the HTTP layer and the JSON mapping are not part of this requirement.

## Type-Specific Fields

### For type=non-functional

**Category**: performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| p95 catalog query without join | < 200ms | 400ms | 800ms | 10,000 primary entities, no filters |
| p95 catalog query with 3 joins | < 500ms | 800ms | 1,500ms | 10,000 primary entities, max. 5 results per row per join |
| p95 catalog query with filter + 3 joins | < 600ms | 1,000ms | 2,000ms | 10,000 primary entities, 1 SavedFilter with 2 conditions |
| p95 paginated call (page 2+) | < 200ms | 400ms | 800ms | same scope; the page after page 150 through the cursor (`cursor` and `nextCursor`), sorted by name and by a property |

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

**AC4** (deep page through the cursor):

- Given: as AC1
- When: 95% of the queries of the page after page 150 through the `nextCursor` of page 150 are measured, sorted by name and by a property
- Then: Server processing time < 200ms

## Verification Method

- [x] Method: test (automated, load test)
- [x] Test setup: synthetic repository with 10,000 entities + 3 JoinDefinitions + SavedFilter
- [x] Measuring tool: the timer `oea.catalog.query` of the backend, read before and after each query (change s9-catalog-query-performance, design D5, D8)
- [x] Pass criterion: p95 values according to table
- [x] Integrated in CI: `CatalogQueryLatencyIT` in the Maven `verify` phase of the backend, 5 warm-up and 30 measured queries per scenario

## Dependencies

- **Prerequisites**: REQ-043 (Catalog), REQ-046 (catalog query)
- **Conflicts**: REQ-065 (n-connection traversal must not increase latency excessively; join on connection primaries must meet the same SLA)

## Risks of Non-Fulfillment

- Users perceive the catalog as sluggish; acceptance drops; return to Excel inventories

## Trade-offs

- Index strategy on `entityTypeId`, `sourceEntityId`, `targetEntityId` mandatory; query plan must be carefully reviewed
- Caching of join results is contraindicated (live data promise per REQ-046); instead: DB query optimization and cursor pagination

## Implementation Notes

- Database indexes: `entities_active (metatype_id)`, `entity_connections_source`, `entity_connections_target` and `entities_active_name (metatype_id, lower(name), id)` for the default order and its keyset seek (migration V22, change s9-catalog-query-performance D7); the measured plans needed no further index
- Keyset paging through the optional `cursor` with `page`, next to `OFFSET` for jumps (design D2, D3); the total is still counted on every query and is part of the measured time
- Time limit: a catalog or portal query is cancelled after `oea.catalog.query-timeout` (10 s by default) and answered with 503 `query-timeout`; every statement of the application has the limit `oea.database.statement-timeout` (60 s by default, REQ-186)

## Implementation

- ADR(s): –
- Implemented by: OpenSpec change `s9-catalog-query-performance` (#66)
- Tests: `CatalogQueryLatencyIT` (AC1–AC4), `CatalogCursorTests`, `CatalogCursorsTests`, `CatalogQueryTimeoutTests`, `StatementTimeoutTests`, `CatalogQueryIndexSchemaTests`
- Verification status: verified
- Measured 95th percentiles (2026-11-07, local run with 10,000 applications, 3 joins with 5 connections per application and join): AC1 6 ms, AC2 20 ms, AC3 84 ms, AC4 5 ms sorted by name and 18 ms sorted by status

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-11-07 | Change s9-catalog-query-performance | Realigned: operation path `/query`, cursor parameter, AC4, CI verification, implementation and measured values |
