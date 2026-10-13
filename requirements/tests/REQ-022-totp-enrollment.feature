# Derived from: requirements/req/req-022-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-03
Feature: REQ-022 – TOTP secret enrollment (set up authenticator app)

  The system SHOULD enable setting up a TOTP secret (RFC 6238) as a
  local authentication method by generating a secret, displaying it as
  a QR code (key URI format) and in plain text, accepting a
  verification code, and persisting the secret in encrypted form only
  after successful verification.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Kurt has chosen TOTP as the method
    Given Kurt has chosen TOTP as the method
    When  the system generates the secret
    Then  a QR code (key URI format) and the secret in plain text are displayed

  @AC2
  Scenario: AC2 – Kurt scans the QR code and enters the current TOTP code
    Given Kurt scans the QR code and enters the current TOTP code
    When  the code is correct (within a time tolerance of ±1 step)
    Then  the secret is persisted in encrypted form and linked to Kurt's person; login with this TOTP is possible immediately (UC-01 A4)

  @AC3
  Scenario: AC3 – Kurt enters a wrong TOTP code
    Given Kurt enters a wrong TOTP code
    When  the verification fails
    Then  no secret is persisted, the enrollment token remains valid, Kurt can try again

  @AC4
  Scenario: AC4 – enrollment has been completed
    Given enrollment has been completed
    When  the system writes log entries
    Then  the TOTP secret is contained in logs neither in plain text nor as a hash

  @AC5
  Scenario: AC5 – the TOTP secret is persisted
    Given the TOTP secret is persisted
    When  it is stored in the database
    Then  it is encrypted (not readable in plain text without a decryption key)
