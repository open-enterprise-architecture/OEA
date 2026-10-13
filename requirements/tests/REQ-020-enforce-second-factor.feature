# Derived from: requirements/req/req-020-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + inspection (default configuration)

@business-rule @should @UC-01
Feature: REQ-020 – Instance-wide enforcement of a second factor for regular logins

  The operator MUST be able to specify during instance commissioning
  that all persons may only log in with a second factor (passkey or
  username/password with TOTP, see REQ-009/REQ-010); when this
  enforcement is active, the minimal variant without a second factor
  (REQ-011) MUST NOT be usable for regular persons. This enforcement
  does NOT apply to the
  [system admin account](../../business-objects/system-admin-account.md)
  from UC-02.

  @AC1
  Scenario: AC1 – an instance on which "enforce second factor" was enabled during commi...
    Given an instance on which "enforce second factor" was enabled during commissioning
    When  an attempt is made to enable REQ-011 (username/password without second factor) for regular persons
    Then  this is rejected as long as the enforcement is active

  @AC2
  Scenario: AC2 – the same instance with active enforcement
    Given the same instance with active enforcement
    When  the system admin account from UC-02 logs in (locally or remotely)
    Then  this login is unaffected by the enforcement and works as specified in UC-02

  @AC3
  Scenario: AC3 – an instance without enabled enforcement (default)
    Given an instance without enabled enforcement (default)
    When  the authentication setup is checked
    Then  REQ-011 can still be enabled according to its own conditions (no default enforcement)
