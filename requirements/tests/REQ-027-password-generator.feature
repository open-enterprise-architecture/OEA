# Derived from: requirements/req/req-027-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-03
Feature: REQ-027 – Password generator for secure local passwords

  The system MUST provide a cryptographically secure password
  generator that produces passwords from the full printable ASCII
  special character set and automatically satisfies the respectively
  configured password policies (REQ-028); the generator MUST be
  available both in the admin context (setting the initial password,
  REQ-024) and in the user context (changing the password).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – instance configuration enforces uppercase letters, lowercase letters...
    Given instance configuration enforces uppercase letters, lowercase letters, digits and special characters
    When  the generator produces a password
    Then  it contains at least one character of each enforced type

  @AC2
  Scenario: AC2 – special characters are enabled
    Given special characters are enabled
    When  the generator produces 1000 passwords
    Then  each contains at least one special character from the full ASCII special character set (not only `!@#$%` or similar)

  @AC3
  Scenario: AC3 – a generated password
    Given a generated password
    When  it is checked against REQ-028 policies
    Then  it satisfies all active rules (no rework by the caller necessary)

  @AC4
  Scenario: AC4 – CSPRNG is available
    Given CSPRNG is available
    When  1000 passwords of the same length are generated
    Then  all are different (no collisions in this sample)

  @AC5
  Scenario: AC5 – the minimum length configuration is too low for the enforced characte...
    Given the minimum length configuration is too low for the enforced character types
    When  the generator is called
    Then  it returns an error instead of producing a non-compliant password
