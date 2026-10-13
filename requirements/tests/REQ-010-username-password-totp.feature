# Derived from: requirements/req/req-010-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-01
Feature: REQ-010 – Username/password login with TOTP as second factor

  The system SHOULD allow persons to log in with username/password and
  a TOTP code as second factor when the OEA instance offers local
  authentication without passkey support.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – a person with a correct password and a valid TOTP code
    Given a person with a correct password and a valid TOTP code
    When  they log in
    Then  they receive a session according to their active role assignments

  @AC2
  Scenario: AC2 – a correct password, but a wrong TOTP code
    Given a correct password, but a wrong TOTP code
    When  the login attempt is completed
    Then  no session is created, and the error message does not reveal that the password was correct
