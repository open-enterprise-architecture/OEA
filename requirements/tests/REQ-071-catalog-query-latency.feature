# Derived from: requirements/req/req-071-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated, load test)

@non-functional @performance @must @UC-06
Feature: REQ-071 – Catalog query latency

  The system MUST complete live catalog queries (REQ-046) within a
  defined time budget — even with multiple configured joins and
  enabled filters.

  @AC1
  Scenario: AC1 – Without join – repository with 10,000 ApplicationComponent entities; ...
    Given repository with 10,000 ApplicationComponent entities; catalog without joins; no filter
    When  95% of queries measured
    Then  server processing time < 200ms

  @AC2
  Scenario: AC2 – 3 joins, aggregate mode – repository with 10,000 ApplicationComponent...
    Given repository with 10,000 ApplicationComponent entities; catalog with 3 JoinDefinitions; on average 5 join results per entity
    When  95% of queries measured
    Then  server processing time < 500ms

  @AC3
  Scenario: AC3 – Filter + 3 joins – as AC2 + 1 SavedFilter with 2 AND-combined conditions
    Given as AC2 + 1 SavedFilter with 2 AND-combined conditions
    When  95% of queries measured
    Then  server processing time < 600ms
