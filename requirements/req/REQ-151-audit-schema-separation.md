---
id: REQ-151
title: Audit Data Storage Immutable and Schema-Separated
type: compliance
priority: must
status: proposed
version: 0.2.0
created: 2026-06-29
last_updated: 2026-10-13
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

The system **MUST** store all audit events in a separate database schema (`audit`) whose tables are owned by a database role different from the application runtime role; the application runtime role **MUST** have INSERT and SELECT rights exclusively, and UPDATE, DELETE, TRUNCATE, ALTER and DROP on audit tables **MUST NOT** be executable by it. Deletion of audit records **MUST** happen only through the retention policy of REQ-083.

## Rationale

Regulatory requirements (DORA, ISO 27001) demand immutability of audit trails. An application user with UPDATE/DELETE rights on audit tables contradicts this principle and makes audits vulnerable. If the runtime role owns the tables (e.g. because migrations run as that role), a `REVOKE` has no effect for the owner (security review SEC-13). Owner decision Q3 of 2026-10-13: audit records cannot be deleted or changed by the application or by admins; deletion only through the retention policy.

## Context

The audit trail serves compliance purposes (UC-14). The `audit` schema can reside in the same DB or in an external audit DB (configurable via `oea.datasource.audit.url`). Denormalized fields (actor_name, entity_name) keep audit records complete and readable after changes to the referenced data; they do not provide tamper evidence. Tamper evidence in all editions (hash chain or external append-only export), actor identity from authenticated principals only and the retention floor are specified in REQ-174; retention values in REQ-083; pseudonymization of actor data in REQ-189. (ADR-024)

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
- Then: entry in `audit.events` with correct `event_type`, actor reference, `occurred_at`; `old_value`/`new_value` in `audit.event_changes` (values of restricted properties protected per REQ-160; actor identity data per REQ-189)

**AC3**:

- Given: `oea.datasource.audit.url` set (external DB)
- When: application starts
- Then: audit writes go to the external DB; application data remains in the main DB

**AC4**:

- Given: a database set up by the regular migration process with production role configuration
- When: the owner of the tables in schema `audit` is inspected and the runtime role executes `DELETE`, `TRUNCATE`, `ALTER TABLE` or `DROP TABLE` on `audit.events`
- Then: the owner is a role different from the runtime role, and every statement is rejected with "permission denied" (or "must be owner")

## Verification Method

- [ ] Method: test (automated) + inspection
- [ ] Test setup: integration test with Testcontainers and production DB roles (migration role separate from runtime role); check DB grants and table owners via `information_schema` / `pg_tables`
- [ ] Pass criterion: UPDATE/DELETE/TRUNCATE/ALTER/DROP attempts throw an exception; INSERT test writes correctly; owner differs from runtime role
- [ ] Integrated in CI: yes

## Dependencies

- **Related**: REQ-083 (retention), REQ-174 (tamper resistance), REQ-189 (pseudonymous actor)
- **Consequences**: US-139

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-29 | requirements-engineer | Initial draft from ADR-024 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-13 / owner decision Q3: owner role separate from runtime role, no TRUNCATE/ALTER/DROP, deletion only via retention policy (AC4); tamper-evidence claim of denormalized fields corrected |
