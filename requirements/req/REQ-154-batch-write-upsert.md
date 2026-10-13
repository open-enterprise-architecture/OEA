---
id: REQ-154
title: Batch Write for Entities and Connections
type: functional
priority: should
status: proposed
version: 0.2.0
created: 2026-06-29
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-22
  adrs:
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-154: Batch Write for Entities and Connections

## Statement

The system **MUST** provide an endpoint that creates or updates up to 500 entities and 500 connections in a single HTTP request with a configurable upsert strategy, and **MUST** support a `dryRun` mode that performs all validations without persisting.

## Rationale

Code scanners and import tools analyze entire repositories or CMDB snapshots and produce many objects at once. Individual HTTP requests per object are inefficient (high latency, many connections) and error-prone (partial failure without atomicity).

## Context

`POST /api/v1/entities/batch`. Strategies: upsert (default), create-only, skip-existing. Atomicity optional (atomic: true/false). (ADR-026)

Security review SEC-04, SEC-13, SEC-23 (2026-10-13): the limit of 500 entities and 500 connections per request is the single maximum batch size for all batch endpoints (REQ-147, REQ-186); batch writes are subject to per-service-account rate limits (REQ-186), property write protection (REQ-162), `external_id` namespace ownership (REQ-177) and the scoping-connection permission (REQ-161); the audit `agent_id` is taken from the authenticated `client_id`, never from a request header (REQ-174).

## Type-Specific Fields

**Inputs**:

- BatchWriteRequest (entities[], connections[], strategy, atomic)
- `?dryRun=true` query parameter

**Processing**:

- For each entity: validation, then create/update/skip according to the strategy
- Connections: processed after entities (referential integrity)
- With `atomic=true`: one error → rollback of all changes
- With `dryRun=true`: no persisting; ValidationResult per item

**Outputs**:

- BatchWriteResponse (summary, items[]) with status per item

**Error Cases**:

- `atomic=true` + at least one error → 422; all rolled back
- `atomic=false` + partial errors → 200 with errors in items[]
- More than 500 entities or more than 500 connections → 422; no item processed
- Item changes a property the service account may not write → item status `error` (or 422 and full rollback with `atomic=true`); property unchanged

## Acceptance Criteria

**AC1**:

- Given: 100 entities in the batch, 5 with a mandatory property violation, `atomic=false`
- When: POST /api/v1/entities/batch
- Then: 200 OK; summary.created=95, summary.errors=5; faulty items with status=error

**AC2**:

- Given: 100 entities, `dryRun=true`
- When: POST /api/v1/entities/batch?dryRun=true
- Then: ValidationResult; no entity in DB; response shows what would be created

**AC3**:

- Given: batch with a connection whose `sourceExternalId` does not exist
- When: POST /api/v1/entities/batch (entities + connections in the same request)
- Then: entities created first; connection then resolved (sourceExternalId from this batch)

**AC4**:

- Given: a batch with 501 entities
- When: POST /api/v1/entities/batch
- Then: 422; no item processed

**AC5**:

- Given: a service account request with header `X-Agent-Id: human-ui`
- When: the batch is persisted
- Then: the audit `agent_id` equals the authenticated `client_id`; the header value is stored only as an untrusted informational field

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test; 100-item batch with error scenarios
- [ ] Pass criterion: correct summary counts; dryRun does not write
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-153 (external_id + upsert), REQ-149 (validation), REQ-162 (write protection), REQ-174 (audit actor identity), REQ-177 (external_id ownership), REQ-186 (rate limits and quotas)
- **Consequences**: US-141

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-026 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-04/13/23: 500 as single max batch size (AC4); write protection; `agent_id` from authenticated client only (AC5) |
