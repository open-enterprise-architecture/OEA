# Derived from: requirements/req/req-095-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-13
Feature: REQ-095 – Navigation tree items as soft references

  The system MUST allow adding and removing repository contents
  (entities, diagrams, catalogs) as TreeNodeItems to folders. Removing an item MUST delete exclusively the
  navigation entry (TreeNodeItem); the referenced object
  MUST NOT be changed or deleted in the process.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Item removal without data loss – a TreeNodeItem is removed
    Given the precondition according to the requirement is met
    When  a TreeNodeItem is removed
    Then  the TreeNodeItem is deleted; the referenced entity continues to exist (HTTP 200 on the entity)

  @AC2
  Scenario: AC2 – Mark orphaned items – an entity is deleted that is referenced as an item in 2 folders
    Given the precondition according to the requirement is met
    When  an entity is deleted that is referenced as an item in 2 folders
    Then  both items show a warning icon; the entity is not restorable by item removal
