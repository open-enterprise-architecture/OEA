---
id: REQ-083
title: Audit log retention and query performance
type: non-functional
priority: should
status: proposed
version: 0.3.0
created: 2026-06-27
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-04
  business_objects:
    - architecture
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-016
    - ADR-024
    - ADR-033
supersedes: []
superseded_by: []
---

# REQ-083: Audit log retention and query performance

## Statement

The system MUST persist all security- and compliance-relevant events (login, logout, entity creation/change/deletion, metamodel change, role assignment) in an append-only audit log that neither the application nor administrators can change or delete. Audit entries MUST be retained for an operator-configurable retention period with a non-configurable floor of **1 year** and a default of **7 years**; entries MUST be deleted only by the retention policy after this period. A reduction of the retention period MUST take effect no earlier than **30 days** after it was recorded. Queries over the last **30 days** MUST complete within **5 seconds**.

## Rationale

OEA is used in corporate environments where change tracking for strategic architecture decisions must be traceable (SH-05: CIO, audit evidence; SH-06: SME operator, version history for decision documentation). The audit trail is complemented by the entity version history (ADR-016, ADR-022), which is append-only as well. A default retention of 7 years covers typical retention obligations for decision documentation; the floor of 1 year prevents an administrator from erasing recent traces by lowering the retention setting (security review SEC-13). Owner decisions Q3 and Q10 of 2026-10-13 make this the single binding retention and immutability statement for REQ-083, REQ-151, REQ-174 and ADR-024.

## Context

Table mapping (ADR-033 §10, replacing the ADR-016 terms `entity_versions` / `audit_events`):

- **Audit trail** in schema `audit` (ADR-024): `audit.events` (events: login, logout, entity creation/change/deletion, metamodel change, role assignment, security events) and `audit.event_changes` (old and new values per changed field). The retention period, its floor and the 30-day delay apply to these tables.
- **Entity history** in the application schema: `entity_version_headers`, `entity_property_value_history`, `entity_system_field_history` (content history for UC-14 to UC-16). These tables are append-only for the runtime role as well, but their lifecycle follows the entity: rows are removed only by the purge of soft-deleted entities (REQ-190), not by the audit retention.

The append-only nature (no update/delete on audit entries) is enforced architecturally (BR-14 in entity.md) and at database level by a table owner role different from the application runtime role (REQ-151, REQ-174). The retention period is stored as an append-only series of settings rows; the initial value is 7 years (2555 days). The only deletion path for audit records is the retention job, which removes entries older than the effective retention period; the job runs under its own database role that can only execute the purge function, and every change of the retention setting is audited with old value, new value and effective date (REQ-174, ADR-033 §4). A reduction becomes effective 30 days after it was recorded, so that it is visible to auditors and in the SIEM export before any record is deleted because of it (owner confirmation 2026-10-13); an increase takes effect immediately.

"Last 30 days" is the most frequent query use case (tracing changes in running sprint/project periods). Older entries may be paginated and slower.

## Type-Specific Fields

### For type=non-functional

**Category**: compliance / performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Retention period of audit entries | configurable; default 7 years; floor 1 year (not configurable below) | – | `audit.events` + `audit.event_changes` |
| Delay before a retention reduction takes effect | ≥ 30 days | – | `audit.events` + `audit.event_changes` |
| Query latency (last 30 days, one entity) | ≤ 5s p95 | > 2s | Repository with 100,000 entities, 50 versions/entity |
| Query latency (last 30 days, all events) | ≤ 10s p95 | > 5s | paginated, 100 entries/page |
| Append-only guarantee | 100% | | no UPDATE/DELETE by the runtime role on `audit.events`, `audit.event_changes` or the entity history tables |

## Acceptance Criteria

**AC1** (Append-only):

- Given: the tables `audit.events`, `audit.event_changes`, `entity_version_headers`, `entity_property_value_history` and `entity_system_field_history` in PostgreSQL
- When: The application's database user executes an UPDATE or DELETE command on these tables
- Then: Database rejects the command (table permission with a different owner role; no soft delete)

**AC2** (Retention active):

- Given: default retention (7 years) and an audit entry dated `NOW() - INTERVAL '6 years'`
- When: the retention job has run and a standard query over `audit.events` for this period is executed
- Then: the entry is present and retrievable; only entries older than the effective retention period have been removed

**AC3** (Query performance last 30 days):

- Given: entity history with 5,000,000 versions (100,000 entities × 50 versions); B-tree index on entity ID and change timestamp of `entity_version_headers`
- When: the versions of one entity changed in the last 30 days are queried, newest first
- Then: p95 ≤ 5s; result correct and complete

**AC4** (API endpoint):

- Given: `GET /api/v1/entities/{id}/history?days=30`
- When: Call for an entity with 50 versions in the last 30 days
- Then: HTTP 200, complete version list, p95 ≤ 5s

**AC5** (Retention floor):

- Given: an administrator with settings permission
- When: they set the audit retention to less than 1 year via the admin UI or API
- Then: the change is rejected; an accepted change of the retention setting creates an audit event with old value, new value and effective date

**AC6** (No admin deletion):

- Given: an administrator with every application permission
- When: they try to delete or modify audit entries through any API or UI function
- Then: no such function exists or the request is rejected; the audit entries are unchanged

**AC7** (Reduction effective after 30 days):

- Given: a retention of 7 years in force and an audit entry dated `NOW() - INTERVAL '3 years'`
- When: an administrator reduces the retention to 2 years and the retention job runs on the same day and again 29 days later
- Then: the entry is still present after both runs; the retention setting shows the pending reduction with an effective date 30 days after the change, and the entry is removed only by a run on or after that date

## Verification Method

- [x] Method: test (automated) + inspection (DB permissions)
- [x] Test setup: synthetic entity history with 5M versions; query plan analysis via `EXPLAIN ANALYZE`; permission test (UPDATE rejected); retention job test with an injectable clock
- [x] Pass criterion: Latency ≤ 5s p95; UPDATE/DELETE rejected; entries within the effective retention present; retention floor enforced; reductions effective only after 30 days
- [ ] Integrated in CI: permission test yes; performance test as release gate

## Dependencies

- **Prerequisites**: ADR-016 (entity history); ADR-024 (audit schema separation); ADR-033 (table mapping, retention mechanics); REQ-005 (login audit log); REQ-151 (schema separation); REQ-174 (tamper resistance); REQ-190 (purge of soft-deleted entities and their history)
- **Downstream effects**: Indexes on the change timestamp of the entity history and on `audit.events.occurred_at` are mandatory (in init migration)

## Risks of Non-Fulfillment

- Compliance violation (GDPR Art. 30, ISO 27001) for customers with audit requirements
- Traceability of architecture decisions is lost (who changed what when?)

## Trade-offs

- Default 7 years vs. storage costs: operators may reduce the retention down to the 1-year floor. For instances with many changes (> 1M events/year), the table grows to > 50 GB — partitioning by `changed_at` (monthly) recommended from v2.0
- Retention vs. GDPR: identifiable actor data in long-retained audit entries is handled by pseudonymization (REQ-189)
- No automatic archiving in v1.0: entries remain in the same tables; cold-storage archiving is a v2.0 feature

## Implementation Notes

- Separate owner role; runtime role with INSERT and SELECT only on the audit and entity history tables (ADR-033 §1, §10)
- Retention floor enforced in the database (`CHECK (retention_days >= 365)` and in the purge function), not only in the API (ADR-033 §4)
- `GET /api/v1/entities/{id}/history` — paginated endpoint (cursor-based, not offset)

## Implementation

- ADR(s): ADR-016, ADR-024, ADR-033
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-13 / owner decisions Q3, Q10: retention configurable with 1-year floor and 7-year default; deletion only via retention policy; no admin deletion (AC2 rewritten, AC5, AC6 added) |
| 0.3.0 | 2026-10-13 | requirements-engineer | ADR-033: table names aligned to `audit.events`/`audit.event_changes` and the entity history tables (§10); retention applies to the audit trail, entity history follows REQ-190; reductions effective after 30 days (statement, target value, AC7); AC5 records the effective date |
