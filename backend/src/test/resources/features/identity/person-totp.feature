# Spec: openspec identity/person-totp
# Source material: requirements/user-stories/US-021-totp-enrollment.md (AC1–AC4),
# US-010-totp-login.md (AC1, AC2), US-029-multiple-totp-credentials.md (AC1–AC3),
# US-024-audit-log-enrollment.md (AC1, AC2, AC4)

@US-021 @US-010 @US-029 @US-024 @UC-01 @UC-03
Feature: TOTP second factor of persons

  A person with a local password sets up one or more authenticator apps in the running
  session and then logs in with password and code. The session counts as confirmed with a
  second factor. Enrollments and their failures are audited without secrets.

  @US-021 @AC1 @AC2 @AC4 @US-010 @AC1 @US-024 @AC1 @AC4
  Scenario: Set up an authenticator and log in with it
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When "Anna" sets up an authenticator app with the label "Phone"
    Then the enrollment showed the secret and a key URI for "anna@example.org" once, and "Phone" is active
    And the audit trail contains the event "auth.factor-added"
    When "Anna" logs in with her password and a code of "Phone"
    Then the session of "Anna" is confirmed with a second factor
    And no log line or audit event contains a secret of "Anna"

  @US-021 @AC3 @US-024 @AC2
  Scenario: Wrong code during the enrollment
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When "Anna" starts setting up an authenticator app with the label "Phone" and enters a wrong code
    Then the request is rejected with status 400 and problem type "invalid-totp-code"
    And the audit trail contains the event "auth.factor-enrollment-failed"
    When "Anna" enters a valid code of "Phone"
    Then "Phone" is active

  @US-010 @AC2
  Scenario: Wrong code at the login
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has set up an authenticator app with the label "Phone"
    When "Anna" logs in with her password and a wrong code
    Then the login is rejected with the same answer as an unknown login transaction

  @US-029 @AC1 @AC2 @AC3
  Scenario: Second authenticator
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has set up an authenticator app with the label "Phone"
    When "Anna" sets up an authenticator app with the label "Backup phone"
    And "Anna" logs in with her password and a code of "Backup phone"
    Then the session of "Anna" is confirmed with a second factor
    And the second factors of "Anna" are "Phone, Backup phone"
