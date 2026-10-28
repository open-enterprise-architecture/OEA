---
id: REQ-186
title: Rate limiting and resource quotas
type: non-functional
priority: must
status: proposed
version: 0.4.0
created: 2026-10-13
last_updated: 2026-10-28
author: requirements-engineer
references:
  use_cases:
    - UC-06
    - UC-22
  business_objects: []
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-026
    - ADR-034
supersedes: []
superseded_by: []
---

# REQ-186: Rate Limiting and Resource Quotas

## Statement

The system **MUST** enforce configurable per-principal rate limits and per-request resource limits on the API with the default values listed below.

## Rationale

A single low-privileged user or leaked service account can otherwise saturate the single PostgreSQL instance with deep traversals, large batches or search loops and make the tool unavailable for everybody (REQ-073).

## Context

Source: security review SEC-23 (proposal PR-30); owner decision Q2 (portal may be internet-exposed → priority must). The maximum batch size was inconsistent (REQ-147: 1,000; REQ-154 and ADR-026: 500) and is harmonized to 500 here. Login throttling is specified separately in REQ-168. Raw analytics limits (ADR-034 §5) are listed below; statement timeout, row and result size limits are specified in REQ-179. Per-principal analytics limits use the same shared rate-limit store as all other limits; the global analytics concurrency is additionally capped by the connection limit of the analytics database role.

**Implementation (change s3-throttling-and-denials, 2026-10-28):** the per-principal request limit (600 / min) and the batch limit (10 / min) are implemented for every authenticated principal; because service accounts do not exist yet (US-142), the batch limit applies to every principal. Both limits are configurable (`oea.web.rate-limit.*`), counted per backend instance in a fixed one-minute window from the first request, and answered with 429, the problem type `urn:oea:problem:too-many-requests` and `Retry-After`. Search, traversal, event-stream and analytics limits follow with their endpoints (spec `platform/request-limits`).

## Type-Specific Fields

### For type=non-functional

**Category**: availability / security

**Measurable target values**:

| Metric | Target value (default, configurable) | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Requests per principal | 600 / min | – | – | all API operations |
| Search/autocomplete requests per principal | 60 / min | – | – | search endpoints |
| Batch write requests per service account | 10 / min | – | – | batch endpoints |
| Items per batch | max 500 entities and 500 connections | – | – | all batch endpoints |
| Request body size | 1 MB; 10 MB for the entity batch and the package import; larger bodies are answered with 413 before they are parsed | – | – | all operations with a request body |
| Page size | max 200 | – | – | all list endpoints |
| Graph traversal depth | max 5 | – | – | impact, lineage, traversal endpoints |
| Concurrent event streams per principal | max 5 | – | – | event stream endpoint |
| Analytics queries per principal | 30 / min | – | – | raw analytics endpoint (REQ-179) |
| Concurrent analytics queries per principal | max 2 | – | – | raw analytics endpoint (REQ-179) |
| Analytics connection pool (global concurrency) | 4 connections; waiting > 5 s → 429 | – | – | raw analytics endpoint, separate from the application pool |
| p95 catalog latency of other principals while one principal is throttled | within REQ-071 | – | above REQ-071 | repository per REQ-071 |

## Acceptance Criteria

**AC1**:

- Given: a principal that exceeds its rate limit
- When: it sends the next request
- Then: the response is 429 with `Retry-After`, and other principals are unaffected (p95 latency of their catalog queries stays within REQ-071)

**AC2**:

- Given: a batch with 501 entities
- When: submitted
- Then: it is rejected with 422 without processing any item

**AC3**:

- Given: a traversal request with depth 6
- When: submitted
- Then: it is rejected with 400

**AC4**:

- Given: a list request with page size 500
- When: submitted
- Then: it is rejected with 400 or capped at 200 as documented in the contract

**AC5** (Analytics limits):

- Given: a principal with `analytics:query` that has submitted 30 analytics queries in the current minute, or has 2 analytics queries running
- When: it submits the next analytics query
- Then: the response is 429 with `Retry-After`, and catalog queries of other principals stay within REQ-071

## Verification Method

- [ ] Method: test (automated, load test)
- [ ] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes (functional limits); load test as release gate

## Dependencies

- **Prerequisites**: REQ-071, REQ-073
- **Follow-on effects**: REQ-147, REQ-154, REQ-179
- **Conflicts**: REQ-147 (previously 1,000 items per batch; aligned 2026-10-13)

## Risks of Non-Fulfillment

- Risk 1: denial of service by a single principal – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-23 (PR-30), owner decision Q2 |
| 0.2.0 | 2026-10-13 | requirements-engineer | ADR-034 §5: analytics rows (30 queries/min, 2 concurrent per principal, pool 4) in the target value table; AC5 added |
| 0.3.0 | 2026-10-23 | Change s2b-backend-hardening | Request body size row (security re-review WS-02, #73); implemented with spec `platform/request-limits` |
| 0.4.0 | 2026-10-28 | Change s3-throttling-and-denials | Per-principal request and batch limits implemented (security re-review WS-02, #74); batch limit per principal until service accounts exist |
