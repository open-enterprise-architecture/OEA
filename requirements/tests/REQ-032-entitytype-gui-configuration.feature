# Derived from: requirements/req/req-032-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated) + exploration (UI test)

@functional @must @UC-04
Feature: REQ-032 – Configure custom EntityTypes via GUI

  The system MUST enable persons with metamodel editor permission to
  create, edit, and delete custom EntityTypes (including properties,
  relations, optional base type, and stereotypes) via a graphical
  form; built-in EntityTypes MUST be visible but NOT editable or
  deletable.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Kurt has metamodel editor permission
    Given Kurt has metamodel editor permission
    When  he creates a new EntityType `SecurityZone` with property `trustLevel` (enum, mandatory) and relation `contains → TechnologyComponent (0..n)` via the form
    Then  `SecurityZone` appears in the type list; when creating a new entity, `SecurityZone` is selectable

  @AC2
  Scenario: AC2 – Kurt tries to create an EntityType with the name `ApplicationComponen...
    Given Kurt tries to create an EntityType with the name `ApplicationComponent` (built-in)
    When  he saves
    Then  an error message "Name 'ApplicationComponent' already taken (built-in)" appears; no entry is created

  @AC3
  Scenario: AC3 – custom EntityType `SecurityZone` has 5 entity instances in the repository
    Given custom EntityType `SecurityZone` has 5 entity instances in the repository
    When  Kurt tries to delete `SecurityZone`
    Then  an error message appears with "5 entities of type SecurityZone must first be deleted or migrated"; no deletion

  @AC4
  Scenario: AC4 – Kurt edits a custom EntityType (adds an optional property)
    Given Kurt edits a custom EntityType (adds an optional property)
    When  he saves
    Then  the new property is visible; existing entities of this type do not become invalid (optional property, no breaking change)

  @AC5
  Scenario: AC5 – built-in EntityType `ApplicationComponent` in the list
    Given built-in EntityType `ApplicationComponent` in the list
    When  Kurt clicks on it
    Then  it is displayed as read-only; no edit or delete buttons
