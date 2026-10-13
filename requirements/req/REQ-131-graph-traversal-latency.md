---
id: REQ-131
title: Graph Traversal Latency (ABAC connection-scoped)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - entity
    - metamodel-configuration
    - role
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
  concept:
    - concept/06-core-entity-types.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-131: Graph Traversal Latency (ABAC connection-scoped)

## Statement

The graph traversal for the `connection-scoped` visibility check (REQ-127) MAY increase the response time of an entity query by at most **200 ms** (p95), measured as additional latency compared to an identical query without `connection-scoped` properties. This applies to entities with up to **500 directly connected scope entities** per user via the configured `scopingConnectionType`.

The traversal result MUST be cached server-side (TTL ≤ 60 seconds per user and ConnectionType). The cache MUST be invalidated for the affected user on changes to relevant connections (creation, deletion).

## Rationale

The `connection-scoped` mode requires a graph traversal at runtime, which without caching can significantly increase the overall latency of the entity API for large graphs. The goal of 200 ms additional latency is a conservative value that is reliably achievable with a depth-1 traversal at typical EA repository size (≤ 500k entities, ≤ 500 scope connections per user). REQ-127 defines the functional requirement; this REQ sets the binding performance framework.

## Context

**Scope**: depth-1 traversal (user → directly connected entities via `scopingConnectionType`); depth-2+ traversal is planned as a future extension and has its own NFR definition

**Data volume**: up to 500k total entities; up to 500 directly connected scope entities per user

**Cache**: server-side, per user ID + ConnectionType ID; TTL 60 seconds; invalidation on connection change

## Type-Specific Fields

### For type=non-functional

**Category**: performance

**Measurable Target Values**:

| Metric | Target Value | Scope |
|---|---|---|
| Additional latency from traversal (p95) | ≤ 200 ms | entity query with connection-scoped properties, cold cache |
| Additional latency from traversal (p95) | ≤ 10 ms | entity query with connection-scoped properties, warm cache |
| Cache hit rate after warm-up | ≥ 90 % | normal usage (user reads several entities in succession) |
| Cache invalidation after connection change | ≤ 1 second | consistency after write |
| Scope: number of directly connected entities | ≤ 500 | validity range of the target value |

## Acceptance Criteria

**AC1** (Cold traversal ≤ 200 ms):
- Given: user has 500 directly connected scope entities; cache empty
- When: an entity query with one `connection-scoped` property is executed
- Then: p95 latency of the traversal portion ≤ 200 ms (measured via APM trace)

**AC2** (Warm cache ≤ 10 ms):
- Given: the traversal result is in the cache (TTL not yet expired)
- When: another entity query by the same user is executed
- Then: traversal portion ≤ 10 ms (cache lookup)

**AC3** (Cache invalidation):
- Given: user has a cached traversal result
- When: a new `DomainAssignment` connection is created for this user
- Then: at the latest after 1 second, the next entity query returns the updated traversal result

**AC4** (Load test):
- Given: 50 concurrent users (REQ-074) with 500 scope connections each
- When: all users simultaneously send entity queries with connection-scoped properties
- Then: p95 latency (total, incl. traversal) ≤ REQ-071 target value + 200 ms

## Verification Method

- [x] Method: load-test (k6 or Gatling; synthetic graph with 500k entities, 500 scope connections per user)
- [x] Method: apm-trace (isolate OpenTelemetry span for the traversal portion)
- [ ] Integrated in CI: no (load test in dedicated performance gates)

## Dependencies

- **Prerequisites**: REQ-127 (connection-scoped enforcement implemented), REQ-074 (scalability NFR)
- **Consequences**: the cache invalidation mechanism must be considered for connection write operations (UC-04, UC-17)

## Risks of Non-Fulfillment

- Noticeable latency degradation on entity detail pages in the catalog (UC-06) when many `connection-scoped` properties are configured
- Domain architects (SH-04) experience a sluggish interface in their main work

## Trade-offs

- TTL 60 seconds: short enough for consistency, long enough for effective caching; can be lowered to 10–30 seconds for higher consistency requirements (performance cost increases)
- Depth-1 traversal is sufficient for v1.0; deeper hierarchies (domain → sub-domain → entity) require a graph DB or a materialized path index

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
