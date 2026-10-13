---
id: REQ-186
title: Rate limiting and resource quotas
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
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
supersedes: []
superseded_by: []
---

# REQ-186: Rate Limiting and Resource Quotas

## Statement

The system **MUST** enforce configurable per-principal rate limits and per-request resource limits on the API with the default values listed below.

## Rationale

A single low-privileged user or leaked service account can otherwise saturate the single PostgreSQL instance with deep traversals, large batches or search loops and make the tool unavailable for everybody (REQ-073).

## Context

Source: security review SEC-23 (proposal PR-30); owner decision Q2 (portal may be internet-exposed → priority must). The maximum batch size was inconsistent (REQ-147: 1,000; REQ-154 and ADR-026: 500) and is harmonized to 500 here. Login throttling is specified separately in REQ-168.

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
| Page size | max 200 | – | – | all list endpoints |
| Graph traversal depth | max 5 | – | – | impact, lineage, traversal endpoints |
| Concurrent event streams per principal | max 5 | – | – | event stream endpoint |
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

## Verification Method

- [ ] Method: test (automated, load test)
- [ ] Pass criterion: AC1–AC4 green
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
