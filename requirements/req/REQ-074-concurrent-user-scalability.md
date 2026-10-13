---
id: REQ-074
title: Concurrent users and scalability
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
    - UC-06
  business_objects:
    - entity
    - catalog
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-074: Concurrent users and scalability

## Statement

The system MUST meet the latency targets defined in REQ-008, REQ-071 and REQ-072 under a load of **50 concurrent active users**, without degrading the p95 values by more than a factor of 2. The system SHOULD support an architecture repository with up to **500,000 ArchitectureEntities** (elements and connections combined) without functional restrictions.

## Rationale

OEA is used in corporate groups with large EA teams (SH-05: corporate group with mixed OEA stack) and must remain stable under simultaneous use by multiple domain architects. At the same time, the data volume must scale: a fully modeled corporate IT landscape map (Bebauungsplan) (all applications, interfaces, technologies, personas, capabilities) easily reaches 50,000–200,000 entities.

## Context

"Concurrent active users" means: users who issue at least one API request within the same 60-second window. Read accesses (catalog, diagrams) dominate; write accesses (EntityDeltas, metamodel changes) are rarer. The target value of 50 users covers SMEs up to mid-sized corporate groups; for large corporate groups (> 200 users), horizontal scaling (multiple API instances, load balancer) is the deployment responsibility of the operator.

## Type-Specific Fields

### For type=non-functional

**Category**: scalability

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Max. concurrent active users without latency degradation > 2× | 50 users | 30 users | Standard deployment (1 API instance, 1 DB instance) |
| Max. ArchitectureEntities in the repository | 500,000 | | all types combined (elements + connections) |
| Catalog query p95 with 50 users | ≤ 2× REQ-071 target value | | 50 parallel requests |
| Login p95 with 50 users | ≤ 2× REQ-008 target value | | 50 parallel login operations |
| Write throughput (save EntityDelta) | ≥ 20 writes/s | | with 50 active users |

## Acceptance Criteria

**AC1** (50 concurrent users — catalog):
- Given: 50 virtual users simultaneously send `GET /api/v1/catalogs/{id}/data`; repository with 10,000 entities
- When: Load test runs for 5 minutes
- Then: p95 latency ≤ 2 × 500ms = 1,000ms (REQ-071 target value × 2); no HTTP 5xx; no timeout

**AC2** (50 concurrent users — login):
- Given: 50 virtual users simultaneously redeem an OIDC token
- When: Load test for 1 minute
- Then: p95 login latency ≤ 2 × 300ms = 600ms (REQ-008 target value × 2)

**AC3** (500,000 entities — functionality):
- Given: Repository with 500,000 ArchitectureEntities (various EntityTypes)
- When: Standard catalog query, opening a diagram, lineage query
- Then: No timeouts; no OOM errors; correct results

**AC4** (500,000 entities — catalog latency):
- Given: 500,000 entities; catalog for primaryEntityType with 50,000 instances
- When: Catalog query, first page (50 entries), no join
- Then: p95 < 500ms (thanks to cursor pagination this is independent of total size)

## Verification Method

- [x] Method: test (automated, load test)
- [x] Test setup: k6 / Locust load test; synthetic repository with 10,000–500,000 entities; 50 virtual users
- [x] Measuring tool: k6 or Locust (HTTP load test); DB monitoring (query plan, lock contention)
- [x] Pass criterion: p95 values according to table; 0 HTTP 5xx; no OOM
- [ ] Integrated in CI: no (too resource-intensive for standard CI); as a release gate before a major version

## Dependencies

- **Prerequisites**: REQ-008 (login latency), REQ-071 (catalog latency), REQ-072 (canvas latency) — these NFRs define the single-user baseline
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Corporate group rollout fails: parallel use by multiple EA teams leads to unacceptable response times
- Large EA models (corporate group with thousands of applications) cannot be captured completely

## Trade-offs

- 500,000 entities are achievable with a relational DB (Postgres) + correct indexes; no NoSQL needed
- Horizontal scaling (multiple API instances) for > 50 users: session management must be stateless (JWT, no server-side session state) — this is a design decision for the walking skeleton
- Connection pool configuration (max. DB connections) must be sized for 50 users: ~10–15 connections/instance

## Implementation Notes

- API server must be stateless (no in-memory session state) → horizontal scaling without sticky sessions
- DB connection pool: HikariCP or pgBouncer recommended
- 500,000 entities: consider table partitioning by `entityTypeId` from 1M entries

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
