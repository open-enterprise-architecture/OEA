# Derived from: requirements/req/req-091-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-12
Feature: REQ-091 – Viewpoint notation mapping consistency

  The `notationElement` dropdown in the viewpoint editor MUST
  show exclusively elements of the selected notation
  (prefix filter: `archimate3:*`, `uml:*`, `bpmn:*`). Selecting an
  element with the wrong prefix MUST be rejected on save with HTTP 422.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Dropdown filter by notation – `notation=archimate3` is selected
    Given the precondition according to the requirement is met
    When  `notation=archimate3` is selected
    Then  the dropdown shows only `archimate3:*` elements

  @AC2
  Scenario: AC2 – API validation – an API call wants to save a `bpmn:Task` element in an `archimate3` viewpoint...
    Given the precondition according to the requirement is met
    When  an API call wants to save a `bpmn:Task` element in an `archimate3` viewpoint
    Then  the API responds with HTTP 422

  @AC3
  Scenario: AC3 – Dropdown reset on notation change – the notation is changed in the form
    Given the precondition according to the requirement is met
    When  the notation is changed in the form
    Then  the dropdown contents update immediately; already selected incompatible elements are reset
