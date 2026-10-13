# Derived from: requirements/req/req-013-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-02
Feature: REQ-013 – Local bootstrapping when no system admin account exists

  The system MUST detect when no
  [system admin account](../../business-objects/system-admin-account.md)
  exists yet for an instance and, in this case, offer a local
  bootstrapping process that works without an external identity
  provider.

  Background:
    Given a freshly installed OEA instance without a system admin account

  @AC1
  Scenario: AC1 – a freshly installed instance without a system admin account
    Given a freshly installed instance without a system admin account
    When  it is started or accessed for the first time
    Then  the system offers the local bootstrapping process without requiring an external IdP

  @AC2
  Scenario: AC2 – a completed local bootstrapping process
    Given a completed local bootstrapping process
    When  the operator logs in with the credential that was set
    Then  they receive access with system admin permissions
