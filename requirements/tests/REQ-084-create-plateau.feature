# Derived from: requirements/req/req-084-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-11
Feature: REQ-084 – Create plateau

  The system MUST allow the creation of plateaus with the statuses `baseline`,
  `target` and `transition`. Mandatory field: `name`; optional:
  `description` (max.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create initial baseline – a plateau is created without `succeeds` reference and with `status=baseline` ...
    Given the precondition according to the requirement is met
    When  a plateau is created without `succeeds` reference and with `status=baseline`
    Then  the API responds with HTTP 201 and `status=baseline`

  @AC2
  Scenario: AC2 – Create target – a plateau is created with `status=target` and `succeeds`=baseline ID
    Given the precondition according to the requirement is met
    When  a plateau is created with `status=target` and `succeeds`=baseline ID
    Then  the API responds with HTTP 201; `status=target` and `succeeds` are set correctly

  @AC3
  Scenario: AC3 – Reject second baseline – another plateau is created with `status=baseline` although a baseline plateau already exists...
    Given the precondition according to the requirement is met
    When  another plateau is created with `status=baseline` although a baseline plateau already exists
    Then  the API responds with HTTP 422 ("A baseline plateau already exists")
