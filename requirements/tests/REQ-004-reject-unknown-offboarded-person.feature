# Derived from: requirements/req/req-004-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-01
Feature: REQ-004 – Rejection of session creation for unknown or offboarded persons

  The system MUST refuse session creation if a valid identity token
  cannot be mapped to an active
  [Person](../../business-objects/person.md) in the repository or if
  the mapped person has the lifecycle status `offboarded`.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – a valid identity token that cannot be mapped to a person in the repos...
    Given a valid identity token that cannot be mapped to a person in the repository
    When  the login process is completed
    Then  no session is created

  @AC2
  Scenario: AC2 – a valid identity token whose mapped person has the status `offboarded`
    Given a valid identity token whose mapped person has the status `offboarded`
    When  the login process is completed
    Then  no session is created
