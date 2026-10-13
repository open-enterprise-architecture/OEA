---
id: REQ-083
title: Audit log retention and query performance
type: non-functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-27
last_updated: 2026-06-27
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
supersedes: []
superseded_by: []
---

# REQ-083: Audit log retention and query performance

## Statement

The system MUST persist all security- and compliance-relevant events (login, logout, entity creation/change/deletion, metamodel change, role assignment) in an append-only audit log. Audit entries MUST be retained for at least **2 years**. Queries over the last **30 days** MUST complete within **5 seconds**.

## Rationale

OEA is used in corporate environments where change tracking for strategic architecture decisions must be traceable (SH-05: CIO, audit evidence; SH-06: SME operator, version history for decision documentation). At the same time, the audit log is the basis for the entity version history (ADR-016: `entity_versions` + `audit_events`). A 2-year retention covers typical compliance requirements (GDPR Art. 30, ISO 27001 A.12.4) without excessive storage effort.

## Context

"Audit log" comprises two tables (ADR-016):
- `entity_versions`: property snapshots before every entity update (content history)
- `audit_events`: structural events (deletion, type change, login, role assignment)

Retention applies to both. The append-only nature (no update/delete on audit entries) is enforced architecturally (BR-14 in entity.md).

"Last 30 days" is the most frequent query use case (tracing changes in running sprint/project periods). Older entries may be paginated and slower.

## Type-Specific Fields

### For type=non-functional

**Category**: compliance / performance

**Measurable Target Values**:

| Metric | Target value | Threshold (warning) | Scope |
|---|---|---|---|
| Retention period of audit entries | ≥ 2 years | < 1 year | `entity_versions` + `audit_events` |
| Query latency (last 30 days, one entity) | ≤ 5s p95 | > 2s | Repository with 100,000 entities, 50 versions/entity |
| Query latency (last 30 days, all events) | ≤ 10s p95 | > 5s | paginated, 100 entries/page |
| Append-only guarantee | 100% | | no UPDATE/DELETE on `entity_versions` or `audit_events` |

## Acceptance Criteria

**AC1** (Append-only):
- Given: `audit_events` and `entity_versions` tables in PostgreSQL
- When: The application's database user executes an UPDATE or DELETE command on these tables
- Then: Database rejects the command (row-level security or table permission; no soft delete)

**AC2** (Retention active):
- Given: Audit entry dated `NOW() - INTERVAL '2 years'`
- When: Standard query over `audit_events` for this period
- Then: Entry is present and retrievable; no automatic deletion by the system

**AC3** (Query performance last 30 days):
- Given: `entity_versions` with 5,000,000 entries (100,000 entities × 50 versions); B-tree index on `changed_at`
- When: `SELECT * FROM entity_versions WHERE entity_id = ? AND changed_at >= NOW() - INTERVAL '30 days' ORDER BY version DESC`
- Then: p95 ≤ 5s; result correct and complete

**AC4** (API endpoint):
- Given: `GET /api/v1/entities/{id}/history?days=30`
- When: Call for an entity with 50 versions in the last 30 days
- Then: HTTP 200, complete version list, p95 ≤ 5s

## Verification Method

- [x] Method: test (automated) + inspection (DB permissions)
- [x] Test setup: synthetic `entity_versions` table with 5M entries; query plan analysis via `EXPLAIN ANALYZE`; permission test (UPDATE rejected)
- [x] Pass criterion: Latency ≤ 5s p95; UPDATE/DELETE rejected; 2-year entries present
- [ ] Integrated in CI: permission test yes; performance test as release gate

## Dependencies

- **Prerequisites**: ADR-016 (entity_versions, audit_events table structure); REQ-005 (login audit log)
- **Downstream effects**: Index on `entity_versions.changed_at` and `audit_events.occurred_at` is mandatory (in init migration)

## Risks of Non-Fulfillment

- Compliance violation (GDPR Art. 30, ISO 27001) for customers with audit requirements
- Traceability of architecture decisions is lost (who changed what when?)

## Trade-offs

- Unlimited retention vs. 2 years: 2 years is a compromise between compliance and storage costs. For instances with many changes (> 1M events/year), the table grows to > 50 GB — partitioning by `changed_at` (monthly) recommended from v2.0
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
