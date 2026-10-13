# Derived from: requirements/req/req-119-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-20
Feature: REQ-119 – Standards drift analysis (prohibited and deviation report)

  The system MUST offer a standards drift analysis that reports all
  entities whose `instanceOfSBBId` points to an SBB with
  `governanceStatus=prohibited` or `deprecated`. In addition,
  the analysis MUST show entities whose `instanceOfSBBId` does not match the
  `preferredStandard` of the assigned TRM category
  (deviation report).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Prohibited entities – 3 entities have `instanceOfSBBId=prohibited-SBB-UUID`
    Given the precondition according to the requirement is met
    When  3 entities have `instanceOfSBBId=prohibited-SBB-UUID`
    Then  all 3 are in the drift list; `prohibited` is highlighted in red

  @AC2
  Scenario: AC2 – Deviation report – an entity uses an SBB that does not match the `preferredStandard` of its...
    Given the precondition according to the requirement is met
    When  an entity uses an SBB that does not match the `preferredStandard` of its TRM category
    Then  the entity appears in the deviation list

  @AC3
  Scenario: AC3 – Direct link – an entry in the drift list is clicked
    Given the precondition according to the requirement is met
    When  an entry in the drift list is clicked
    Then  the entity detail view opens
