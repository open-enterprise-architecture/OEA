# Derived from: requirements/req/req-030-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-03
Feature: REQ-030 – Multiple active TOTP credentials per person

  The system MUST allow a person to store multiple active TOTP
  credentials (`LocalCredential.type=totp`) at the same time; at login
  (REQ-010), a valid TOTP code from any of the person's active TOTP
  credentials MUST be accepted; each TOTP credential MUST carry a
  user-assignable label so that the person can distinguish their
  credentials from one another.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – person has two active TOTP credentials (different secrets, different...
    Given person has two active TOTP credentials (different secrets, different devices)
    When  a valid TOTP code from the second credential is entered
    Then  login is successful; `lastUsedAt` of the second credential is updated

  @AC2
  Scenario: AC2 – person has an existing active TOTP credential
    Given person has an existing active TOTP credential
    When  they set up another TOTP credential via UC-03 A3
    Then  they then have two active TOTP credentials; the first is not revoked

  @AC3
  Scenario: AC3 – two active TOTP credentials
    Given two active TOTP credentials
    When  the person opens their security settings
    Then  both credentials are displayed with their label and `createdAt`/`lastUsedAt`

  @AC4
  Scenario: AC4 – person sets up a TOTP without a label
    Given person sets up a TOTP without a label
    When  the credential is created
    Then  it receives a sensible default label (e.g. "TOTP Authenticator" + date)

  @AC5
  Scenario: AC5 – two active TOTP credentials
    Given two active TOTP credentials
    When  an invalid code (not attributable to any of the credentials) is entered
    Then  login fails; no information about which credential was not matched (no leak)
