# Derived from: requirements/req/req-111-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-17
Feature: REQ-111 – Write protection for imported and built-in continuum building blocks

  All continuum building blocks (ABB, SBB, pattern, reference architecture)
  with `scope=imported` or `scope=built-in` MUST be locked for all
  write operations. In the UI, edit and delete actions
  for these building blocks MUST NOT be visible;
  API attempts MUST be rejected with HTTP 422.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – API write protection – a PUT or DELETE request is issued on an ABB with `scope=imported`...
    Given the precondition according to the requirement is met
    When  a PUT or DELETE request is issued on an ABB with `scope=imported`
    Then  the system responds with HTTP 422

  @AC2
  Scenario: AC2 – UI write protection and badge – a building block with `scope=imported` is displayed in the UI
    Given the precondition according to the requirement is met
    When  a building block with `scope=imported` is displayed in the UI
    Then  edit and delete buttons are not visible; a badge "Imported – TOGAF 10 TRM" is visible

  @AC3
  Scenario: AC3 – SBB assignments editable – a TRM category with `scope=imported` is opened
    Given the precondition according to the requirement is met
    When  a TRM category with `scope=imported` is opened
    Then  SBB assignments (`preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`) are editable; name and parent category are locked
