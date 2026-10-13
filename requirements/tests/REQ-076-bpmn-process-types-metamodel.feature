# Derived from: requirements/req/req-076-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated, API and integration tests)

@functional @must @UC-10 @UC-04
Feature: REQ-076 – BPMN process elements and OrganizationalUnit in the metamodel

  The system MUST provide a complete set of built-in
  `EntityTypeDefinitions` for BPMN 2.0 process modeling (pool, lane,
  task variants, events, gateways, SequenceFlow, MessageFlow).
  Additionally, `organizational-unit` MUST be available as a built-in
  EntityType.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Built-in BPMN types present – OEA instance started; admin opens MetamodelConfiguration
    Given the precondition according to the requirement is met
    When  OEA instance started; admin opens MetamodelConfiguration
    Then  all 19 built-in BPMN EntityTypes, `organizational-unit` and 10 built-in connections (4 BPMN, 1 org unit, 5 assignment) are visible (isBuiltIn=true); immutable in their base properties

  @AC2
  Scenario: AC2 – Extensibility – admin creates `EntityTypeDefinition { id: "approval-t...
    Given the precondition according to the requirement is met
    When  admin creates `EntityTypeDefinition { id: "approval-task", extends: "bpmn-user-task" }`
    Then  `approval-task` inherits all attributes of `bpmn-user-task`; appears in BPMN palette; can be used in process diagrams

  @AC3
  Scenario: AC3 – OrganizationalUnit type – admin opens MetamodelConfiguration
    Given the precondition according to the requirement is met
    When  admin opens MetamodelConfiguration
    Then  `organizational-unit` visible as built-in EntityType; extensible via `extends` to `department`, `team`, `business-unit`, etc.

  @AC4
  Scenario: AC4 – Pool/lane nested layout – process diagram with pool → lane → task hie...
    Given process diagram with pool → lane → task hierarchy via `bpmn-contained-in` connections
    When  canvas loads the diagram
    Then  pool appears as outer container; lanes as swim lanes; tasks within the correct lanes; layout correctly nested

  @AC5
  Scenario: AC5 – SequenceFlow constraint – user tries to draw a `bpmn-sequence-flow` f...
    Given the precondition according to the requirement is met
    When  user tries to draw a `bpmn-sequence-flow` from a BPMN task to a non-BPMN entity (e.g. ApplicationComponent)
    Then  API returns HTTP 422 (BR-01); UI shows error message

  @AC6
  Scenario: AC6 – BPMN viewpoint – user creates a new diagram and selects the built-in...
    Given the precondition according to the requirement is met
    When  user creates a new diagram and selects the built-in viewpoint `bpmn-process-view`
    Then  palette shows exclusively BPMN-compliant elements; pool/lane mode is active (container drop possible)
