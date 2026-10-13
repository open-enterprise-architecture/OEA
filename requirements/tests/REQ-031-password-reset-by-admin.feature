# Derived from: requirements/req/req-031-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-03
Feature: REQ-031 – Password reset by administrator

  The system MUST enable an administrator to reset the local password
  of any person; in doing so, the previous password credential MUST be
  revoked immediately, a new password credential MUST be set
  (compliant with REQ-028) and displayed to the administrator once in
  plain text, and the Required Action "Change password at next login"
  MUST be set automatically for the affected person, blocking all
  application access until it has been performed.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – person has an active password credential and can currently log in
    Given person has an active password credential and can currently log in
    When  an administrator resets the password
    Then  the old password credential is `revoked`; a new one is `active`; the new password plain text appears once

  @AC2
  Scenario: AC2 – password was reset
    Given password was reset
    When  the person logs in with the new password
    Then  Required Action `password_change` is detected; the person receives no application access until the password has been changed

  @AC3
  Scenario: AC3 – password was reset
    Given password was reset
    When  the person tries to log in with the old password
    Then  login fails (old credential is revoked)

  @AC4
  Scenario: AC4 – administrator resets password
    Given administrator resets password
    When  the audit log is evaluated
    Then  it contains admin person ID, target person ID, timestamp, action `password_reset`; no password plain text in the log

  @AC5
  Scenario: AC5 – the process fails after step 4 (e.g. DB error)
    Given the process fails after step 4 (e.g. DB error)
    When  the error occurs
    Then  neither is the old credential revoked nor a new one created (complete rollback)

  @AC6
  Scenario: AC6 – system admin account is to be reset
    Given system admin account is to be reset
    When  the admin makes the request
    Then  it is rejected with an error; no credential changed
