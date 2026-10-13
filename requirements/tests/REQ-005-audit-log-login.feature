# Derived from: requirements/req/req-005-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@compliance @must @UC-01
Feature: REQ-005 – Audit log entry for every login attempt

  The system MUST create an audit log entry with timestamp, the
  authentication mechanism used, and the result for every login
  attempt – successful or not.

  @AC1
  Scenario: AC1 – a login attempt (OIDC or API key) is made
    Given a login attempt (OIDC or API key) is made
    When  it is successful
    Then  the audit log contains an entry with timestamp, mechanism, result `success` and identity reference

  @AC2
  Scenario: AC2 – a login attempt fails (E1, E2 or E4)
    Given a login attempt fails (E1, E2 or E4)
    When  the failure occurs
    Then  the audit log contains an entry with timestamp, mechanism, result `failure` and – as far as possible without disclosing security-relevant details – the failure reason
