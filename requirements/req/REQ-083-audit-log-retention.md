---
id: REQ-083
title: Audit log retention and query performance
type: non-functional
priority: should
status: proposed
version: 0.2.0
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
supersedes: []
superseded_by: []
---

# REQ-083: Audit log retention and query performance

## Statement

The system MUST persist all security- and compliance-relevant events (login, logout, entity creation/change/deletion, metamodel change, role assignment) in an append-only audit log that neither the application nor administrators can change or delete. Audit entries MUST be retained for an operator-configurable retention period with a non-configurable floor of **1 year** and a default of **7 years**; entries MUST be deleted only by the retention policy after this period. Queries over the last **30 days** MUST complete within **5 seconds**.

## Rationale

OEA is used in corporate environments where change tracking for strategic architecture decisions must be traceable (SH-05: CIO, audit evidence; SH-06: SME operator, version history for decision documentation). At the same time, the audit log is the basis for the entity version history (ADR-016: `entity_versions` + `audit_events`). A default retention of 7 years covers typical retention obligations for decision documentation; the floor of 1 year prevents an administrator from erasing recent traces by lowering the retention setting (security review SEC-13). Owner decisions Q3 and Q10 of 2026-10-13 make this the single binding retention and immutability statement for REQ-083, REQ-151, REQ-174 and ADR-024.

## Context

"Audit log" comprises two tables (ADR-016):

- `entity_versions`: property snapshots before every entity update (content history)
- `audit_events`: structural events (deletion, type change, login, role assignment)

Retention applies to both. The append-only nature (no update/delete on audit entries) is enforced architecturally (BR-14 in entity.md) and at database level by a table owner role different from the application runtime role (REQ-151, REQ-174). The only deletion path is the retention job, which removes entries older than the configured retention period; the job runs under its own database role, and changes of the retention setting are audited with old and new value (REQ-174).

Note: ADR-024 has since moved audit data into a separate `audit` schema (`audit.events`, `audit.event_changes`); the table names `entity_versions` and `audit_events` above refer to ADR-016 and need alignment by the solution architect.

"Last 30 days" is the most frequent query use case (tracing changes in running sprint/project periods). Older entries may be paginated and slower.

## Type-Specific Fields

### For type=non-functional

**Category**: compliance / performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Retention period of audit entries | configurable; default 7 years; floor 1 year (not configurable below) | – | `entity_versions` + `audit_events` |
| Query latency (last 30 days, one entity) | ≤ 5s p95 | > 2s | Repository with 100,000 entities, 50 versions/entity |
| Query latency (last 30 days, all events) | ≤ 10s p95 | > 5s | paginated, 100 entries/page |
| Append-only guarantee | 100% | | no UPDATE/DELETE on `entity_versions` or `audit_events` |

## Acceptance Criteria

**AC1** (Append-only):

- Given: `audit_events` and `entity_versions` tables in PostgreSQL
- When: The application's database user executes an UPDATE or DELETE command on these tables
- Then: Database rejects the command (table permission with a different owner role; no soft delete)

**AC2** (Retention active):

- Given: default retention (7 years) and an audit entry dated `NOW() - INTERVAL '6 years'`
- When: the retention job has run and a standard query over `audit_events` for this period is executed
- Then: the entry is present and retrievable; only entries older than the configured retention period have been removed

**AC3** (Query performance last 30 days):

- Given: `entity_versions` with 5,000,000 entries (100,000 entities × 50 versions); B-tree index on `changed_at`
- When: `SELECT * FROM entity_versions WHERE entity_id = ? AND changed_at >= NOW() - INTERVAL '30 days' ORDER BY version DESC`
- Then: p95 ≤ 5s; result correct and complete

**AC4** (API endpoint):

- Given: `GET /api/v1/entities/{id}/history?days=30`
- When: Call for an entity with 50 versions in the last 30 days
- Then: HTTP 200, complete version list, p95 ≤ 5s

**AC5** (Retention floor):

- Given: an administrator with settings permission
- When: they set the audit retention to less than 1 year via the admin UI or API
- Then: the change is rejected; an accepted change of the retention setting creates an audit event with old and new value

**AC6** (No admin deletion):

- Given: an administrator with every application permission
- When: they try to delete or modify audit entries through any API or UI function
- Then: no such function exists or the request is rejected; the audit entries are unchanged

## Verification Method

- [x] Method: test (automated) + inspection (DB permissions)
- [x] Test setup: synthetic `entity_versions` table with 5M entries; query plan analysis via `EXPLAIN ANALYZE`; permission test (UPDATE rejected)
- [x] Pass criterion: Latency ≤ 5s p95; UPDATE/DELETE rejected; entries within the configured retention present; retention floor enforced
- [ ] Integrated in CI: permission test yes; performance test as release gate

## Dependencies

- **Prerequisites**: ADR-016 (entity_versions, audit_events table structure); ADR-024 (audit schema separation); REQ-005 (login audit log); REQ-151 (schema separation); REQ-174 (tamper resistance)
- **Downstream effects**: Index on `entity_versions.changed_at` and `audit_events.occurred_at` is mandatory (in init migration)

## Risks of Non-Fulfillment

- Compliance violation (GDPR Art. 30, ISO 27001) for customers with audit requirements
- Traceability of architecture decisions is lost (who changed what when?)

## Trade-offs

- Default 7 years vs. storage costs: operators may reduce the retention down to the 1-year floor. For instances with many changes (> 1M events/year), the table grows to > 50 GB — partitioning by `changed_at` (monthly) recommended from v2.0
- Retention vs. GDPR: identifiable actor data in long-retained audit entries is handled by pseudonymization (REQ-189)
- No automatic archiving in v1.0: entries remain in the same tables; cold-storage archiving is a v2.0 feature

## Implementation Notes

- PostgreSQL row-level security or separate DB user without DELETE rights on `entity_versions` and `audit_events`
- Partial indexes on `changed_at >= NOW() - INTERVAL '30 days'` for frequent queries
- `GET /api/v1/entities/{id}/history` — paginated endpoint (cursor-based, not offset)

## Implementation

- ADR(s): ADR-016
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-27 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-13 / owner decisions Q3, Q10: retention configurable with 1-year floor and 7-year default; deletion only via retention policy; no admin deletion (AC2 rewritten, AC5, AC6 added) |
