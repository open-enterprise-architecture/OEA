---
id: REQ-151
title: Audit Data Storage Immutable and Schema-Separated
type: compliance
priority: must
status: proposed
version: 0.1.0
created: 2026-06-29
last_updated: 2026-06-29
author: requirements-engineer
references:
  use_cases:
    - UC-14
  adrs:
    - ADR-024
supersedes: []
superseded_by: []
---

# REQ-151: Audit Data Storage Immutable and Schema-Separated

## Statement

The system **MUST** store all audit events in a separate database schema (`audit`) for which the application user has INSERT rights exclusively; UPDATE and DELETE on audit tables **MUST NOT** be executable by the application user.

## Rationale

Regulatory requirements (DORA, ISO 27001) demand immutability of audit trails. An application user with UPDATE/DELETE rights on audit tables contradicts this principle and makes audits vulnerable.

## Context

The audit trail serves compliance purposes (UC-14). The `audit` schema can reside in the same DB or in an external audit DB (configurable via `oea.datasource.audit.url`). Denormalized fields (actor_name, entity_name) ensure tamper evidence. (ADR-024)

## Type-Specific Fields

**Regulatory Framework**: ISO 27001, DORA Art. 9, internal compliance requirements

**Specific Provision**: immutability of audit logs (log integrity)

**Evidence Requirement**: DB permission concept documented; INSERT-only demonstrable via SQL grants

**Audit Relevance**: yes; annual review recommended

## Acceptance Criteria

**AC1**:

- Given: OEA application user `oea_app` active
- When: attempt to execute `UPDATE audit.events SET event_type='manipulated'`
- Then: database error "permission denied"; no record changed

**AC2**:

- Given: entity change via UI
- When: change persisted
- Then: entry in `audit.events` with correct `event_type`, `actor_name`, `actor_email`, `occurred_at`; `old_value`/`new_value` in `audit.event_changes`

**AC3**:

- Given: `oea.datasource.audit.url` set (external DB)
- When: application starts
- Then: audit writes go to the external DB; application data remains in the main DB

## Verification Method

- [ ] Method: test (automated) + inspection
- [ ] Test setup: integration test; check DB grants via `information_schema`
- [ ] Pass criterion: UPDATE attempt throws an exception; INSERT test writes correctly
- [ ] Integrated in CI: yes

## Dependencies

- **Consequences**: US-139

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-024 |
