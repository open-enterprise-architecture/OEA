# Derived from: requirements/req/req-016-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@compliance @must @UC-02
Feature: REQ-016 – Audit log entry for the bootstrapping process

  The system MUST record every completed or rejected bootstrapping
  process in the audit log with timestamp, chosen mode (local/remote),
  and result.

  @AC1
  Scenario: AC1 – a successfully completed local or remote bootstrapping process
    Given a successfully completed local or remote bootstrapping process
    When  it is completed
    Then  the audit log contains an entry with timestamp, mode and result `success`

  @AC2
  Scenario: AC2 – a rejected bootstrapping attempt (e.g. due to REQ-015)
    Given a rejected bootstrapping attempt (e.g. due to REQ-015)
    When  the rejection occurs
    Then  the audit log contains an entry with timestamp and result `rejected`
