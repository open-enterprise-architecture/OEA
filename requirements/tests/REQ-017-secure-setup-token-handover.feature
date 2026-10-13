# Derived from: requirements/req/req-017-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + inspection

@constraint @must @UC-02
Feature: REQ-017 – Secure handover of the setup token

  The initial setup token or the prompt interface for password
  assignment MUST NOT be delivered via a channel that can be reached
  without prior privileged access to the instance (e.g.

  @AC1
  Scenario: AC1 – a freshly installed instance
    Given a freshly installed instance
    When  the setup token is generated
    Then  it can only be retrieved via a channel that already requires privileged access (e.g. CLI output at startup, local log file, container exec)

  @AC2
  Scenario: AC2 – the initial setup web interface (if UI-based)
    Given the initial setup web interface (if UI-based)
    When  it is accessed without a valid setup token
    Then  it refuses password assignment
