# Derived from: requirements/req/req-012-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@data @should @UC-01
Feature: REQ-012 – Configurable token lifetime for local authentication

  For local authentication (passkey, username/password, with or
  without TOTP – see REQ-009–REQ-011), the validity period of the
  access token (authorization token) and the refresh token MUST be
  configurable instance-wide.

  @AC1
  Scenario: AC1 – an instance with a locally configured access token lifetime of 15 min...
    Given an instance with a locally configured access token lifetime of 15 minutes
    When  a person logs in via a local mechanism (passkey, username/password)
    Then  the access token expires after 15 minutes

  @AC2
  Scenario: AC2 – an expired access token, but a still valid refresh token from local a...
    Given an expired access token, but a still valid refresh token from local authentication
    When  a refresh is requested
    Then  a new access token with the configured lifetime is issued
