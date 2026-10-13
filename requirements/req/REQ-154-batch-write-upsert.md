---
id: REQ-154
title: Batch Write for Entities and Connections
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
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

## Verification Method

- [ ] Method: test (automated)
- [ ] Test setup: integration test; 100-item batch with error scenarios
- [ ] Pass criterion: correct summary counts; dryRun does not write
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-153 (external_id + upsert), REQ-149 (validation)
- **Consequences**: US-141

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-026 |
