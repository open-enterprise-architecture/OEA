# Spec: openspec identity/person-password
# Source material: requirements/user-stories/US-011-password-minimal.md (AC1–AC3),
# US-023-initial-password-admin.md (AC1, AC3, AC4), US-006-generic-error-message.md (AC1)

@US-011 @UC-01 @UC-03
Feature: Password login of persons

  An operator who runs OEA without an identity provider switches on the password
  login of persons. An administrator sets an initial password, which the person
  must change at the first login. Failed logins reveal nothing about accounts.

  @US-011 @AC2
  Scenario: Off on a fresh instance
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    When the system admin sets the initial password of "Anna"
    Then the request is rejected with status 409 and problem type "conflict"

  @US-023 @AC1 @AC3 @AC4 @US-011 @AC1 @AC3
  Scenario: Initial password, first login and own password
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    When the system admin sets the initial password of "Anna"
    Then the answer names when the initial password expires but not the password
    When "Anna" logs in with her e-mail address and the initial password
    Then "Anna" is active and must change her password before anything else
    When "Anna" changes her password
    Then "Anna" logs in with her e-mail address and the new password without a second factor
    And the audit trail contains the event "person.password-set"
    And the audit trail contains the event "auth.password-changed"
    And no log line contains a password of "Anna"

  @US-006 @AC1
  Scenario: Unknown and existing accounts
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    And the system admin has set the initial password of "Anna"
    When a login is attempted with the unknown e-mail address "nobody@example.org" and with a wrong password for "Anna"
    Then both logins are rejected with the same status, problem type and body
