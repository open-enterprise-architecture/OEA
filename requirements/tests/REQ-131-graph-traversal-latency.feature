# Derived from: requirements/req/req-131-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: load-test (k6 or Gatling; synthetic graph with 500k entities, 500 scope connections per user)

@non-functional @performance @must @UC-21
Feature: REQ-131 – Graph traversal latency (ABAC connection-scoped)

  The graph traversal for the `connection-scoped` visibility check
  (REQ-127) MAY increase the response time of an entity query by at most **200
  ms** (p95), measured as additional latency compared to an
  identical query without `connection-scoped` properties. This applies
  to entities with up to **500 directly connected scope entities**
  per user via the configured `scopingConnectionType`.

  @AC1
  Scenario: AC1 – Cold traversal ≤ 200 ms – user has 500 directly connected scope entities; cache empty
    Given user has 500 directly connected scope entities; cache empty
    When  entity query with one `connection-scoped` property executed
    Then  p95 latency of the traversal portion ≤ 200 ms (measured via APM trace)

  @AC2
  Scenario: AC2 – Warm cache ≤ 10 ms – traversal result is in the cache (TTL not yet expired)
    Given traversal result is in the cache (TTL not yet expired)
    When  further entity query of the same user executed
    Then  traversal portion ≤ 10 ms (cache lookup)

  @AC3
  Scenario: AC3 – Cache invalidation – user has cached traversal result
    Given user has cached traversal result
    When  new `DomainAssignment` connection is created for this user
    Then  at the latest after 1 second the next entity query returns the updated traversal result

  @AC4
  Scenario: AC4 – Load test – 50 concurrent users (REQ-074) with 500 scope connections each
    Given 50 concurrent users (REQ-074) with 500 scope connections each
    When  all users simultaneously send entity queries with connection-scoped properties
    Then  p95 latency (total, incl. traversal) ≤ REQ-071 target value + 200 ms
