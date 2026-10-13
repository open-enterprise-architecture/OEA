# Derived from: requirements/req/req-009-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-01
Feature: REQ-009 – Passkey login (local authentication without external IdP)

  The system SHOULD allow persons to log in using a passkey
  (WebAuthn/FIDO2) when the OEA instance is operated without an
  external OIDC identity provider. For passkeys registered as a
  discoverable credential (resident key), login SHOULD be possible
  without prior entry of username or password.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – a person with a previously registered passkey
    Given a person with a previously registered passkey
    When  they log in with this passkey
    Then  they receive a session according to their active role assignments

  @AC2
  Scenario: AC2 – a failed passkey verification
    Given a failed passkey verification
    When  the login attempt is completed
    Then  no session is created and the error message is indistinguishable from other error cases (e.g. E1, E2)

  @AC3
  Scenario: AC3 – a person with a passkey registered as a discoverable credential
    Given a person with a passkey registered as a discoverable credential
    When  they log in
    Then  no prior entry of username or password is required
