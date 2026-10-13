# Derived from: requirements/req/req-019-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@business-rule @should @UC-02
Feature: REQ-019 – Ability to disable the local system admin account when a remote mapping is active

  As soon as at least one functional remote bootstrapping mapping
  (REQ-014) is configured for an instance, the local system admin
  account (REQ-013) SHOULD be selectively deactivatable by a system
  admin.

  @AC1
  Scenario: AC1 – an active local system admin account and a functional remote mapping
    Given an active local system admin account and a functional remote mapping
    When  a system admin deactivates the local account
    Then  login via the local account is no longer possible afterwards, while the remote route continues to work

  @AC2
  Scenario: AC2 – no functional remote mapping configured
    Given no functional remote mapping configured
    When  an attempt is made to deactivate the local account
    Then  the system warns of a possible complete lockout, but does not necessarily block the action (operator decision)
