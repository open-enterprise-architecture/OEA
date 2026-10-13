# Derived from: requirements/req/req-007-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@business-rule @should @UC-01
Feature: REQ-007 – Lifecycle transition invited→active on first successful login

  The system SHOULD change the lifecycle status of a
  [Person](../../business-objects/person.md) from `invited` to `active`
  as soon as their first successful login process is completed.

  @AC1
  Scenario: AC1 – a person with status `invited`
    Given a person with status `invited`
    When  they log in successfully for the first time
    Then  their status changes to `active`

  @AC2
  Scenario: AC2 – a person with status `active`
    Given a person with status `active`
    When  they log in again
    Then  their status remains `active` (no repeated transition, no side effects)
