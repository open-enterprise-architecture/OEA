# Derived from: requirements/req/req-090-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-12
Feature: REQ-090 – Create, edit and delete viewpoint

  The system MUST allow creating, editing and deleting user-defined
  `ViewpointDefinition` entries. A viewpoint consists
  of: `name` (mandatory, unique in the instance), `notation` (mandatory:
  `archimate3|uml|bpmn20`), `allowedEntityTypes[]`,
  `allowedConnectionTypes[]` and `notationMappings[]` (per entity type:
  `notationElement`, `defaultWidth`, `defaultHeight`).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create viewpoint – a new viewpoint is created with 3 entity types
    Given the precondition according to the requirement is met
    When  a new viewpoint is created with 3 entity types
    Then  the API responds with HTTP 201; the viewpoint is immediately selectable for new diagrams

  @AC2
  Scenario: AC2 – Remove entity type with warning – when editing, an entity type is removed from `allowedEntityTypes` that is used in existing diagrams...
    Given the precondition according to the requirement is met
    When  when editing, an entity type is removed from `allowedEntityTypes` that is used in existing diagrams
    Then  the system shows a warning "N diagrams affected"; editing is not blocked

  @AC3
  Scenario: AC3 – Delete viewpoint – a user-defined viewpoint is deleted
    Given the precondition according to the requirement is met
    When  a user-defined viewpoint is deleted
    Then  it is removed from the `MetamodelConfiguration`
