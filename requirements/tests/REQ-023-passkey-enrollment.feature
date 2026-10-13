# Derived from: requirements/req/req-023-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-03
Feature: REQ-023 – Passkey enrollment (WebAuthn credential registration)

  The system SHOULD enable registering a passkey (WebAuthn Level 2 /
  FIDO2) as a local authentication method by generating a registration
  challenge, verifying the authenticator response, and persistently
  linking the public key of the registered credential to the person
  object.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Kurt has chosen passkey as the method and has a compatible authenticator
    Given Kurt has chosen passkey as the method and has a compatible authenticator
    When  he completes the authenticator confirmation
    Then  the public key is persistently stored and login via passkey (UC-01 A3) is possible immediately

  @AC2
  Scenario: AC2 – Kurt cancels the authenticator confirmation
    Given Kurt cancels the authenticator confirmation
    When  the cancellation occurs
    Then  no credential is stored, the enrollment token remains valid

  @AC3
  Scenario: AC3 – a person already has a registered passkey
    Given a person already has a registered passkey
    When  they register another passkey (e.g. second device)
    Then  both credentials are stored and individually usable for login

  @AC4
  Scenario: AC4 – a registration response with a wrong origin or RP ID
    Given a registration response with a wrong origin or RP ID
    When  the system processes it
    Then  it is rejected, no credential is persisted
