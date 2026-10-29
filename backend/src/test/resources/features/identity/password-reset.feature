# Spec: openspec identity/password-reset
# Source material: requirements/user-stories/US-030-password-reset-by-admin.md (AC1–AC4)

@US-030 @UC-03
Feature: Password reset by an administrator

  A person who forgot the password gets a new temporary password from an administrator.
  The old password and every session of the person end at once, and the person must
  choose an own password at the next login.

  @US-030 @AC1 @AC2 @AC3 @AC4
  Scenario: Password reset
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When the system admin resets the password of "Anna"
    Then the answer contains the new password once, and the session of "Anna" has ended
    And a login of "Anna" with her own password fails
    And "Anna" must change her password after logging in with the new password
    And the audit trail contains the event "person.password-reset" without the password
    And no log line contains a generated password
