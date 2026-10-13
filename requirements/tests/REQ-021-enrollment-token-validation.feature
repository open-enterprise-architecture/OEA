# Derived from: requirements/req/req-021-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @skip @UC-03
Feature: REQ-021 – Enrollment token validation and single use

  Before displaying the enrollment interface, the system MUST check
  whether the passed enrollment token exists, has not yet been
  consumed, and has not expired; after successfully completed
  enrollment, the token MUST be marked as consumed and rejected on
  repeated redemption.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – a valid, unconsumed, unexpired enrollment token
    Given a valid, unconsumed, unexpired enrollment token
    When  Kurt opens the setup link
    Then  the system shows the enrollment interface with the available methods

  @AC2
  Scenario: AC2 – an already consumed enrollment token
    Given an already consumed enrollment token
    When  Kurt opens the setup link again
    Then  the system shows a generic error message, no form

  @AC3
  Scenario: AC3 – an expired enrollment token
    Given an expired enrollment token
    When  Kurt opens the setup link
    Then  the system shows the same generic error message as in AC2 (no distinction)

  @AC4
  Scenario: AC4 – a successfully completed enrollment
    Given a successfully completed enrollment
    When  the token is to be redeemed again afterwards
    Then  it is rejected (identical to AC2)

  @AC5
  Scenario: AC5 – a valid token is being processed
    Given a valid token is being processed
    When  the system writes the token value to logs
    Then  the token value is not contained in plain text in the server log (hashed or omitted)
