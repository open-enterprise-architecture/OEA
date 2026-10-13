# Derived from: requirements/req/req-073-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: monitoring (production operation)

@non-functional @performance @must @UC-01 @UC-05 @UC-06
Feature: REQ-073 – System availability (uptime SLA)

  For productive OEA instances, the system MUST maintain a minimum
  availability of **99.5%** per calendar month, measured at the API
  endpoint `GET /health`. Planned maintenance windows (maximum 4
  hours per month, announced ≥ 24 hours in advance) are excluded from
  the calculation.

  @AC1
  Scenario: AC1 – Uptime monitoring – productive OEA instance has been running for 30 days
    Given productive OEA instance has been running for 30 days
    When  monitoring tool checks `GET /health` every 60 seconds
    Then  availability ≥ 99.5% of the measurement period (excl. announced maintenance windows)

  @AC2
  Scenario: AC2 – Automatic restart – OEA API process crashes unexpectedly
    Given OEA API process crashes unexpectedly
    When  container orchestration responds (Kubernetes/Docker Compose with restart policy)
    Then  `/health` responds with HTTP 200 again after 30 minutes at the latest

  @AC3
  Scenario: AC3 – Health endpoint – `GET /health` called
    Given the precondition according to the requirement is met
    When  `GET /health` called
    Then  HTTP 200 with full functionality; HTTP 503 if the database is unreachable or on critical error; response time < 100ms
