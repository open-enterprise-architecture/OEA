# Derived from: requirements/req/req-006-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@constraint @must @UC-01
Feature: REQ-006 – No disclosure of account existence on failed login

  The system MUST NOT reveal, through different error messages or
  response times, whether a requested identity (email address,
  subject ID) exists in the repository or not.

  @AC1
  Scenario: AC1 – a login attempt with a non-existent identity
    Given a login attempt with a non-existent identity
    When  the login fails
    Then  the externally visible error message is identical to that for an existing but incorrectly authenticated identity

  @AC2
  Scenario: AC2 – two failed login attempts, one with an existing and one with a non-ex...
    Given two failed login attempts, one with an existing and one with a non-existent identity
    When  the response times are compared
    Then  they do not differ in a way an attacker could exploit (no timing side channel)
