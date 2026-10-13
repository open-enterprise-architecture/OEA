---
id: REQ-073
title: System availability (uptime SLA)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-05
    - UC-06
  business_objects:
    - architecture
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

# REQ-073: System availability (uptime SLA)

## Statement

The system MUST maintain a minimum availability of **99.5%** per calendar month for production OEA instances, measured at the API endpoint `GET /health`. Planned maintenance windows (maximum 4 hours per month, announced ≥ 24 hours in advance) are excluded from the calculation.

## Rationale

OEA is a collaborative EA tool that provides strategic decision bases for SH-05 (CIO) and is often the only available EA platform for SH-06 (SME operator). An outage during critical planning phases — e.g., shortly before an investment decision or during an audit preparation process — can have direct business consequences. 99.5% corresponds to a maximum of ~3.6 hours of unplanned downtime per month — a realistic target for a containerized, self-hosted instance without geo-redundancy.

## Context

Applies to production OEA instances (not to development/staging). Includes: API server, database reachability, auth stack. Does not include: outages caused by external identity providers (OIDC), network outages at the operator's infrastructure level.

The target value is graded by deployment scenario:

| Scenario | Availability target | Rationale |
|---|---|---|
| Self-hosted (SME, single node) | 99.0% (~7.3 h/month) | No redundant setup expected |
| Self-hosted (corporate group, HA setup) | 99.5% (~3.6 h/month) | Redundancy possible and expected |
| SaaS (if OEA is offered as a service) | 99.9% (~43 min/month) | Geo-redundancy, load balancing |

This REQ describes the **default target value** (self-hosted, HA setup / SaaS). The SME value is considered the minimum.

## Type-Specific Fields

### For type=non-functional

**Category**: availability

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Monthly availability (`/health` HTTP 200) | ≥ 99.5% | < 99.5% | Production instance, HA setup |
| Unplanned downtime per month | ≤ 3.6 hours | > 2 hours | excl. announced maintenance windows |
| Planned maintenance windows | ≤ 4 hours/month | | at least 24 h advance notice |
| Recovery time objective (RTO) for unplanned disruption | ≤ 30 minutes | | automatic restart via container orchestration |

## Acceptance Criteria

**AC1** (Uptime monitoring):
- Given: Production OEA instance has been running for 30 days
- When: Monitoring tool checks `GET /health` every 60 seconds
- Then: Availability ≥ 99.5% of the measurement period (excl. announced maintenance windows)

**AC2** (Automatic restart):
- Given: OEA API process crashes unexpectedly
- When: Container orchestration reacts (Kubernetes/Docker Compose with restart policy)
- Then: `/health` responds with HTTP 200 again after 30 minutes at the latest

**AC3** (Health endpoint):
- When: `GET /health` called
- Then: HTTP 200 at full functionality; HTTP 503 if the database is unreachable or on a critical error; response time < 100ms

## Verification Method

- [x] Method: monitoring (production operation)
- [x] Measuring tool: external monitoring service or Prometheus/Alertmanager (operator's choice)
- [x] Pass criterion: monthly uptime report ≥ 99.5%
- [ ] Integrated in CI: no (production measurement; in CI: `GET /health` smoke test)

## Dependencies

- **Prerequisites**: Deployment concept (ADR for the deployment stack still open — group A ADRs)
- **Downstream effects**: Backup strategy and data loss NFR (REQ-082) must be consistent

## Risks of Non-Fulfillment

- CIO (SH-05) does not trust the tool as a reliable decision basis; return to PowerPoint architecture
- SME operator (SH-06) without an EA tool in a critical planning phase

## Trade-offs

- 99.5% is achievable without geo-redundancy; 99.9% requires an HA setup with failover — deliberately not chosen as the default target value, to make SME self-hosting realistically possible
- Database outages (Postgres restart) dominate unplanned downtime; short restart times (< 30s) are realistic

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
