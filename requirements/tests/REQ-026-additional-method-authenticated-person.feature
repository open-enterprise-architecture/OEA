# Derived from: requirements/req/req-026-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-03
Feature: REQ-026 – Set up an additional authentication method for a logged-in person

  The system SHOULD enable an already authenticated person to set up
  an additional local authentication method via their own security
  settings without requiring an enrollment token; the session MUST
  serve as proof of authentication in this case.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Kurt is logged in and has not yet set up a TOTP method
    Given Kurt is logged in and has not yet set up a TOTP method
    When  he adds TOTP in the security settings and completes the setup
    Then  TOTP is persisted as an additional method; no enrollment token was required

  @AC2
  Scenario: AC2 – Kurt is logged in and wants to add a second passkey
    Given Kurt is logged in and wants to add a second passkey
    When  he performs the passkey registration
    Then  the new public key is persisted; both passkeys are usable for login

  @AC3
  Scenario: AC3 – Kurt has already set up passkey, TOTP and password
    Given Kurt has already set up passkey, TOTP and password
    When  he opens the security settings
    Then  the system indicates that all methods are already set up (no error)

  @AC4
  Scenario: AC4 – a request to the enrollment endpoint without a valid session
    Given a request to the enrollment endpoint without a valid session
    When  the request arrives
    Then  it is rejected; redirect to login

  @AC5
  Scenario: AC5 – a request without a CSRF token (for a POST-form-based flow)
    Given a request without a CSRF token (for a POST-form-based flow)
    When  the request arrives
    Then  it is rejected with a corresponding error
