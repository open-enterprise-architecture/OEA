# Spec: openspec metamodel/starter-packages (BPMN starter package), diagram/bpmn-process-diagrams (us-074-bpmn-process-diagrams)
# Source material: requirements/user-stories/US-074-create-process-diagram-and-place-bpmn-elements.md; REQ-076; UC-10; owner decision 2026-11-27
# The scenarios share one instance of their own (the import changes its metamodel, design D9) and run in file order.
# The editor scenarios (symbols, palette groups, containers, flow warnings, the section on SCR-030) are covered by the
# frontend specs; this feature covers the API.

@US-074 @REQ-076 @UC-10 @diagram @bpmn
Feature: BPMN starter package and process diagrams

  The starter package "oea-starter-bpmn" brings the BPMN types, the flows between
  them and the viewpoint "Process Model (BPMN 2.0)". It is imported on top of the
  TOGAF starter package. A flow to an element that its connection type does not
  allow is rejected (BR-01).

  Background:
    Given an instance of its own with the TOGAF starter package, an enterprise architect and a metamodel administrator for BPMN

  Scenario: Name taken
    Given the organization has the BPMN metatype "Task"
    When the metamodel administrator imports the BPMN starter package
    Then the BPMN import is rejected with 409 naming "Task"
    And no metatype of the BPMN starter package exists
    And the organization metatype "Task" is removed

  Scenario: BPMN imported next to TOGAF
    When the metamodel administrator imports the BPMN starter package
    Then the BPMN import succeeds with 31 created metatypes
    And 26 element metatypes and 5 connection types exist with scope "imported" and the origin "oea-starter-bpmn"
    And the viewpoint "Process Model (BPMN 2.0)" can be chosen for a new diagram
    And the 8 metatypes of the TOGAF starter package are unchanged
    And no metatype of the package is the composition
    And the BPMN starter package is listed as imported

  Scenario Outline: Subtypes of the BPMN types
    Then the base type of the BPMN metatype "<type>" is "<base>"

    Examples:
      | type                | base        |
      | User Task           | Task        |
      | Business Rule Task  | Task        |
      | Message Start Event | Start Event |
      | Timer Start Event   | Start Event |
      | Message End Event   | End Event   |
      | Terminate End Event | End Event   |

  Scenario: Sequence flow between tasks
    Given a BPMN diagram "Order handling"
    When the enterprise architect saves the tasks "Check order" and "Ship order" with a "Sequence Flow"
    Then the BPMN save is accepted with 1 edge

  Scenario: Sequence flow to a non-BPMN element
    Given a BPMN diagram "Order to SAP" that also allows "Application Component"
    When the enterprise architect saves the task "Check order" and the component "SAP ERP" with a "Sequence Flow"
    Then the BPMN save is rejected with 422 and the code "TARGET_NOT_ALLOWED"
