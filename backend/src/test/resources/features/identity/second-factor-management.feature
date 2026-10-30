# Spec: openspec identity/second-factor-management
# Source material: requirements/req/REQ-173-account-recovery-temporary-passwords.md (AC5),
# owner decision 2026-10-30 (removal of own second factors)

@REQ-173 @UC-03
Feature: Managing second factors

  A person removes the authenticator of a lost device. A person who lost every device gets
  the second factors reset by an administrator and logs in with the password alone.

  Scenario: Remove the authenticator of a lost phone
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has set up an authenticator app with the label "Phone"
    And "Anna" has set up an authenticator app with the label "Backup phone"
    When "Anna" removes the second factor "Phone"
    Then the second factors of "Anna" are "Backup phone"
    And the audit trail contains the event "auth.factor-removed"

  @AC5
  Scenario: Every device lost
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin" and password login of persons switched on
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has set up an authenticator app with the label "Phone"
    When the system admin resets the second factors of "Anna"
    Then the session of "Anna" has ended
    And "Anna" logs in with her password alone
    And the audit trail contains the event "person.second-factors-reset"
