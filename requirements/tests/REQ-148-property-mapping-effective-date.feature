# Derived from: requirements/req/req-148-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated)

@functional @should @UC-04
Feature: REQ-148 – Property mapping effective date changeable

  The system **MUST** make it possible to change the necessity
  (mandatory/warning/optional) of a property assignment to a
  meta type by effective date, without deleting existing values or
  overwriting historical assignments.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Property P is mapped as `optional` for meta type M since 2026-01-01
    Given property P is mapped as `optional` for meta type M since 2026-01-01
    When  admin creates new mapping P→M with valid_from=2026-07-01, necessity=mandatory
    Then  from 2026-07-01 mandatory applies; entities lacking P receive warning/error on validation

  @AC2
  Scenario: AC2 – A mapping entry for valid_from=2026-01-01 exists
    Given a mapping entry for valid_from=2026-01-01 exists
    When  admin tries to enter the same valid_from again
    Then  409 Conflict
