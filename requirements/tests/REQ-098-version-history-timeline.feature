# Derived from: requirements/req/req-098-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-14
Feature: REQ-098 – Version history as chronological timeline

  The system MUST display a chronological version history (timeline)
  for every ArchitectureEntity. For each version the following
  information MUST be visible: version number, timestamp (UTC), name
  of the person making the change (linked to profile), changed fields (from
  `EntityVersion.changedFields`) and optional change reason.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Timeline with versions – an entity with 5 versions is opened
    Given the precondition according to the requirement is met
    When  an entity with 5 versions is opened
    Then  the timeline shows 5 entries, newest first

  @AC2
  Scenario: AC2 – Filter by actor – the timeline is filtered by actor "Michael"
    Given the precondition according to the requirement is met
    When  the timeline is filtered by actor "Michael"
    Then  only versions created by Michael are displayed

  @AC3
  Scenario: AC3 – Entity without history – an entity without change history is opened
    Given the precondition according to the requirement is met
    When  an entity without change history is opened
    Then  the tab shows "No changes since creation. Created on [date] by [person]."
