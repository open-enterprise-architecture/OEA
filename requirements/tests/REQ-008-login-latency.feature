# Derived from: requirements/req/req-008-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated, load test)

@non-functional @performance @should @UC-01
Feature: REQ-008 – Login latency

  The system SHOULD complete the login process (from redeeming the
  authorization code or validating the API key until session
  confirmation) within a defined time budget.

  @AC1
  Scenario: AC1 – a repository with 10,000 persons and 1,000 roles
    Given a repository with 10,000 persons and 1,000 roles
    When  95% of OIDC login processes are measured (server-side portion)
    Then  the processing time is below 300ms

  @AC2
  Scenario: AC2 – a repository with 10,000 active API keys
    Given a repository with 10,000 active API keys
    When  95% of API key validations are measured
    Then  the processing time is below 100ms
