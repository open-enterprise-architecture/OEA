# Derived from: requirements/req/req-029-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-01 @UC-03
Feature: REQ-029 – Operator configures role-based 2FA exemption

  If the instance-wide 2FA enforcement is active (REQ-020), the
  operator MUST additionally be able to configure which OEA roles are
  exempt from this obligation; persons whose active role(s) are
  entirely contained in the exemption list MUST be able to satisfy
  REQ-020 without a second factor. The system admin account (UC-02)
  is independent of this configuration – it is always exempt
  (REQ-020).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – `enforce2FA=true` and `twoFactorExemptRoles=['administrator']`
    Given `enforce2FA=true` and `twoFactorExemptRoles=['administrator']`
    When  a person with role `administrator` logs in with a password and has no 2FA credential
    Then  they receive access directly, without UC-03 Required Action

  @AC2
  Scenario: AC2 – the same configuration
    Given the same configuration
    When  a person without role `administrator` logs in with a password and has no 2FA credential
    Then  UC-03 Required Action is triggered (behavior of REQ-020 unchanged)

  @AC3
  Scenario: AC3 – `twoFactorExemptRoles=['administrator']` and `enforce2FA=true`
    Given `twoFactorExemptRoles=['administrator']` and `enforce2FA=true`
    When  a person has both role `administrator` and `editor`
    Then  the person is exempt (at least one exempt role suffices)

  @AC4
  Scenario: AC4 – `twoFactorExemptRoles` contains a non-existent role ID
    Given `twoFactorExemptRoles` contains a non-existent role ID
    When  the operator wants to save
    Then  the configuration is rejected with a specific error message (unknown role ID)

  @AC5
  Scenario: AC5 – system admin account from UC-02 and any `twoFactorExemptRoles` config...
    Given system admin account from UC-02 and any `twoFactorExemptRoles` configuration
    When  system admin account logs in
    Then  it is always exempt, regardless of this configuration
