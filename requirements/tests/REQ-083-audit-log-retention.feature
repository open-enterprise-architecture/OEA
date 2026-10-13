# Derived from: requirements/req/req-083-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + inspection (DB permissions)

@non-functional @performance @should @UC-01 @UC-04
Feature: REQ-083 – Audit log retention and query performance

  The system MUST persist all security- and compliance-relevant
  events (login, logout, entity creation/change/deletion,
  metamodel change, role assignment) in an append-only audit log.
  Audit entries MUST be retained for at least **2 years**.

  @AC1
  Scenario: AC1 – Append-only – `audit_events` and `entity_versions` tables in PostgreSQL
    Given `audit_events` and `entity_versions` tables in PostgreSQL
    When  the application's database user executes an UPDATE or DELETE statement on these tables
    Then  the database rejects the statement (row-level security or table permission; no soft delete)

  @AC2
  Scenario: AC2 – Retention active – audit entry dated `NOW() - INTERVAL '2 years'`
    Given audit entry dated `NOW() - INTERVAL '2 years'`
    When  standard query over `audit_events` for this period
    Then  entry is present and retrievable; no automatic deletion by the system

  @AC3
  Scenario: AC3 – Query performance last 30 days – `entity_versions` with 5,000,000 entries (100,000 entities × 50 versions)...
    Given `entity_versions` with 5,000,000 entries (100,000 entities × 50 versions); B-tree index on `changed_at`
    When  `SELECT * FROM entity_versions WHERE entity_id = ? AND changed_at >= NOW() - INTERVAL '30 days' ORDER BY version DESC`
    Then  p95 ≤ 5s; result correct and complete

  @AC4
  Scenario: AC4 – API endpoint – `GET /api/v1/entities/{id}/history?days=30`
    Given `GET /api/v1/entities/{id}/history?days=30`
    When  call for an entity with 50 versions in the last 30 days
    Then  HTTP 200, complete version list, p95 ≤ 5s
