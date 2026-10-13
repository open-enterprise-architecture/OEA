# Derived from: requirements/req/req-074-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated, load test)

@non-functional @performance @should @UC-05 @UC-06
Feature: REQ-074 – Concurrent users and scalability

  Under a load of **50 concurrent active users**, the system MUST
  meet the latency targets defined in REQ-008, REQ-071, and REQ-072
  without degrading the p95 values by more than a factor of 2. The
  system SHOULD support an architecture repository with up to
  **500,000 ArchitectureEntities** (elements and connections
  combined) without functional restrictions.

  @AC1
  Scenario: AC1 – 50 concurrent users — catalog – 50 virtual users simultaneously send...
    Given 50 virtual users simultaneously send `GET /api/v1/catalogs/{id}/data`; repository with 10,000 entities
    When  load test runs for 5 minutes
    Then  p95 latency ≤ 2 × 500ms = 1,000ms (REQ-071 target × 2); no HTTP 5xx; no timeout

  @AC2
  Scenario: AC2 – 50 concurrent users — login – 50 virtual users simultaneously send OIDC token redemption
    Given 50 virtual users simultaneously send OIDC token redemption
    When  load test for 1 minute
    Then  p95 login latency ≤ 2 × 300ms = 600ms (REQ-008 target × 2)

  @AC3
  Scenario: AC3 – 500,000 entities — functionality – repository with 500,000 ArchitectureEntities (various EntityTypes)
    Given repository with 500,000 ArchitectureEntities (various EntityTypes)
    When  standard catalog query, diagram opening, lineage query
    Then  no timeouts; no OOM errors; correct results

  @AC4
  Scenario: AC4 – 500,000 entities — catalog latency – 500,000 entities; catalog for pr...
    Given 500,000 entities; catalog for primaryEntityType with 50,000 instances
    When  catalog query, first page (50 entries), no join
    Then  p95 < 500ms (due to cursor pagination this is independent of total size)
