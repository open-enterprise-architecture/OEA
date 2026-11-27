---
id: REQ-205
title: Server memory footprint
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-11-27
last_updated: 2026-11-27
author: Lukas Mathis
references:
  use_cases:
    - UC-02
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-06
    - SH-05
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-012
    - ADR-016
supersedes: []
superseded_by: []
---

# REQ-205: Server memory footprint

## Statement

A standard deployment of OEA (one backend instance and one PostgreSQL instance on one server) SHOULD run within the memory of the sizing classes published in the user manual: 4 GB for a small, 8 GB for a medium to large and 16 GB for a very large repository, without swapping and without an out-of-memory termination.

## Rationale

Operators (SH-06) size the server before installing OEA, and the CIO (SH-05) budgets it. The user manual gives sizing guidance ("Hardware sizing" in the installation page). Until now that guidance has been estimated from ADR-012 (JVM footprint of about 250 to 400 MB) and ADR-016 (about 500 MB of EA data for 50,000 entities). This requirement makes the guidance measurable, so that the manual can state measured values.

## Context

The measurement covers the resident memory of the backend process and of the PostgreSQL server, together with the operating system, in the standard deployment of `compose.yaml`. A self-hosted identity provider is not part of it. The load follows REQ-074 (50 concurrent active users in the standard deployment).

## Type-Specific Fields

### For type=non-functional

**Category**: capacity

**Measurable Target Values**:

| Metric | Target value | Threshold (critical) | Scope |
|---|---|---|---|
| Peak resident memory of the backend | ≤ 1.5 GB | 2 GB | 50,000 entities, 50 concurrent users with the load mix of REQ-074 |
| Peak resident memory of PostgreSQL | ≤ 4 GB | 5 GB | same scope, 2 parallel analytics queries (ADR-034) |
| Server memory, small class | 4 GB, no swap, no OOM | – | 10,000 entities, 20 concurrent users |
| Server memory, medium to large class | 8 GB, no swap, no OOM | – | 50,000 entities, 50 concurrent users |
| Server memory, very large class | 16 GB, no swap, no OOM | – | 100,000 entities, 50 concurrent users, 4 parallel analytics queries |

## Acceptance Criteria

**AC1** (Backend footprint):

- Given: a standard deployment with 50,000 entities
- When: 50 concurrent users run the load mix of REQ-074 for 30 minutes
- Then: the peak resident memory of the backend process is at most 1.5 GB, and no out-of-memory error occurs

**AC2** (Sizing classes):

- Given: a server limited to the memory of one sizing class, with the repository size and load of that class
- When: the load runs for 30 minutes
- Then: the server does not swap, no process is terminated for lack of memory, and the latency targets of REQ-071 and REQ-008 stay within the factor 2 of REQ-074

## Verification Method

- [ ] Method: test (load test, manual or scheduled; not part of every CI run)
- [ ] Test setup: the standard deployment with memory limits per sizing class, a synthetic repository of the class size, and the load mix of REQ-074
- [ ] Measuring tool: the container memory statistics (peak resident memory) of the backend and the database, and the JVM metrics of the backend
- [ ] Pass criterion: the values of the table
- [ ] Result: the measured values replace the estimated ones in the user manual ("Hardware sizing")

## Dependencies

- **Prerequisites**: REQ-074 (concurrent users and their load mix), REQ-075 (deployment)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Operators size servers too small and see out-of-memory terminations, or too large and pay for unused memory

## Trade-offs

- A smaller JVM heap reduces the footprint but increases garbage collection under load; the heap is set as a share of the container memory (`-XX:MaxRAMPercentage`)

## Implementation

- ADR(s): ADR-012, ADR-016
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-11-27 | Lukas Mathis | Initial draft: sizing classes of the user manual as measurable targets |
