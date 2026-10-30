# Spec: openspec identity/passkeys
# Source material: requirements/user-stories/US-022-passkey-enrollment.md (AC1, AC3, AC4),
# US-009-passkey-login.md (AC1–AC3), US-025-additional-method-logged-in-person.md (AC2)

@US-022 @US-009 @US-025 @UC-01 @UC-03
Feature: Passkeys of persons

  A person with a local password registers passkeys on her devices. With a passkey she
  logs in without password, or confirms a password login with it. A passkey login with
  user verification counts as confirmed with a second factor.

  @US-022 @AC1 @AC3 @US-025 @AC2 @US-009 @AC1 @AC3
  Scenario: Register passkeys and log in without password
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin", password login of persons switched on and the public URL "https://oea.example.org"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When "Anna" registers a passkey on the device "Laptop"
    And "Anna" registers a passkey on the device "Phone"
    Then the audit trail contains 2 added passkeys
    And "Anna" logs in without password with the passkey of "Laptop" and her session is confirmed with a second factor
    And "Anna" logs in without password with the passkey of "Phone" and her session is confirmed with a second factor

  @US-022 @AC4
  Scenario: Registration for another origin
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin", password login of persons switched on and the public URL "https://oea.example.org"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    When "Anna" registers a passkey on the device "Laptop" on the site "https://oea-login.example.com"
    Then the request is rejected with status 400 and problem type "invalid-passkey"
    And the audit trail contains the event "auth.factor-enrollment-failed"

  @US-009 @AC2
  Scenario: Unknown passkey
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin", password login of persons switched on and the public URL "https://oea.example.org"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has registered a passkey on the device "Laptop"
    When the passkey of "Laptop" is removed at the instance and a login is attempted with it
    Then the login is rejected like a wrong password

  Scenario: Password and passkey
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin", password login of persons switched on and the public URL "https://oea.example.org"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And the person "Anna" with the e-mail address "anna@example.org" has chosen her own password
    And "Anna" has registered a passkey on the device "Laptop"
    When "Anna" logs in with her password
    Then the login asks for the second factor "passkey"
    And "Anna" confirms the login with the passkey of "Laptop" and her session is confirmed with a second factor
