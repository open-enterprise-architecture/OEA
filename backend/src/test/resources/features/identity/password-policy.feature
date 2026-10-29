# Spec: openspec identity/password-policy
# Source material: requirements/user-stories/US-027-configure-password-policies.md (AC1–AC4),
# US-026-password-generator.md (AC1–AC4)

@US-027 @US-026 @UC-03
Feature: Password policy and password generator

  The administrators of an instance set the rules every local password must follow:
  minimum length, character types, expiry and history, never below the floors of the
  operator. Compliant passwords can be generated instead of invented.

  @US-027 @AC1 @AC4
  Scenario: Policy set and enforced
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    When the system admin sets a password policy with minimum length 2 and all four character types
    Then the request is rejected with status 422 and problem type "validation-failed"
    When the system admin sets a password policy with minimum length 16 that requires uppercase letters, digits and special characters
    Then the audit trail contains the event "settings.password-policy-changed"
    When the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    And the system admin sets the initial password "abcdefghijklmnop" for "Anna"
    Then the request is rejected with status 422 and problem type "password-policy-violation"
    And the rejection names the rules "requireUppercase, requireDigits, requireSpecialChars"

  @US-027 @AC2 @AC3
  Scenario: Expiry and history
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has set a password policy with an expiry of 90 days and a history of 5
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When "Anna" sets a password she used before
    Then the request is rejected with status 422 and problem type "password-policy-violation"
    And the rejection names the rules "historyCount"
    When 91 days have passed since "Anna" chose her password
    Then "Anna" must change her password after logging in with her own password

  @US-026 @AC1 @AC2 @AC3
  Scenario: Generated initial passwords
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has set a password policy that requires special characters
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    And the system admin has invited the person "Ben" with the e-mail address "ben@example.org"
    When the system admin has the initial passwords of "Anna" and "Ben" generated
    Then each answer contains a password that contains a special character, and the two passwords differ
    And no log line contains a generated password

  @US-026 @AC4
  Scenario: Suggestion for the own change
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the system admin has set a password policy that requires special characters
    And the system admin has invited the person "Anna" with the e-mail address "anna@example.org"
    And the system admin has set the initial password of "Anna"
    When "Anna" logs in with her e-mail address and the initial password
    And "Anna" asks for a password suggestion
    Then the suggestion contains a special character, and "Anna" changes her password to it
