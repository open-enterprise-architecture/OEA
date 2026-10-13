# Derived from: requirements/req/req-028-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-03
Feature: REQ-028 – Operator configures password policies

  The system MUST enable the operator to configure instance-wide
  password policies that are enforced on every password setting or
  change; the policies MUST include at least the following rules:
  minimum length, character type enforcement (uppercase letters,
  lowercase letters, digits, special characters – individually
  activatable), expiry period, and password history.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – operator enables `requireUppercase`, `requireLowercase`, `requireDig...
    Given operator enables `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` and sets `minLength=16`
    When  a person sets a password that contains only lowercase letters
    Then  it is rejected with an error message naming all unmet rules

  @AC2
  Scenario: AC2 – operator sets `expiryDays=90`
    Given operator sets `expiryDays=90`
    When  a person logs in 91 days after the last password change
    Then  Required Action "Change password" is triggered; no full access until the change

  @AC3
  Scenario: AC3 – operator sets `historyCount=5`
    Given operator sets `historyCount=5`
    When  a person tries to set a password that matches one of the last 5 passwords
    Then  it is rejected with a notice about the reuse prohibition

  @AC4
  Scenario: AC4 – operator sets an inconsistent configuration (`minLength=3`, all 4 ch...
    Given operator sets an inconsistent configuration (`minLength=3`, all 4 character types enforced)
    When  the configuration is to be saved
    Then  it is rejected with a specific error message (minimum length must be ≥ 4)

  @AC5
  Scenario: AC5 – all rules are at default (false / 0)
    Given all rules are at default (false / 0)
    When  a password with minimum length is set
    Then  it is accepted even if it contains only lowercase letters

  @AC6
  Scenario: AC6 – `requireSpecialChars=true`
    Given `requireSpecialChars=true`
    When  a password is checked
    Then  all printable ASCII special characters are accepted as valid (no restricted subset check)
