# Derived from: requirements/req/req-024-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-03
Feature: REQ-024 – Administrator sets initial password

  The system MUST enable an administrator to set an initial password
  for local authentication when creating a person; this password MUST
  be persisted as a secure hash (argon2id or bcrypt) immediately after
  being set and MUST be displayed to the administrator in plain text
  exactly once, and not be retrievable afterwards.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – administrator creates a person and sets an initial password
    Given administrator creates a person and sets an initial password
    When  the request is processed successfully
    Then  the password hash is persisted as `LocalCredential (type=password)`; the plain text is displayed exactly once

  @AC2
  Scenario: AC2 – the initial password was set and displayed
    Given the initial password was set and displayed
    When  the administrator opens the admin UI again or calls the API again
    Then  the password plain text is no longer retrievable (only the hash in the DB)

  @AC3
  Scenario: AC3 – the initial password is set
    Given the initial password is set
    When  the person logs in with this password (UC-01 A5)
    Then  login succeeds and UC-03 (Required Action) is triggered if 2FA is enforced

  @AC4
  Scenario: AC4 – the system processes the password request
    Given the system processes the password request
    When  server logs are written
    Then  the password plain text is not contained in the logs

  @AC5
  Scenario: AC5 – system automatically generates a password
    Given system automatically generates a password
    When  it is generated
    Then  it has at least 16 characters and sufficient entropy (upper/lower case/digits/special characters)
