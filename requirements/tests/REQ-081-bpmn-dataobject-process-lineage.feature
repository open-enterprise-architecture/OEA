# Derived from: requirements/req/req-081-*.md
# V-model system test specification – created 2026-06-30

@functional @should
Feature: REQ-081 – BPMN DataObject integration and process data lineage

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – DataObject in the process diagram – Business analyst can drag `bpmn-data-object` and `bpmn-data-store` from the BPMN palette...
    # TODO: AC without standard structure – refine manually
    # - Business analyst can drag `bpmn-data-object` and `bpmn-data-store` from the BPMN palette onto the process diagram
    # - DataObjects can be placed within lanes or freely at process level
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC2
  Scenario: AC2 – Create input/output association – Business analyst draws a dashed line (BPMN notation) from a DataObject...
    # TODO: AC without standard structure – refine manually
    # - Business analyst draws a dashed line (BPMN notation) from a DataObject to a task (input) or from the task to a DataObject (output)
    # - On creation the UI asks for: direction (input / output) and optionally `transformationType` (create / enrich / overwrite / delete / read-only)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC3
  Scenario: AC3 – Enrichment annotation – Detail panel of a `bpmn-data-output-association` shows fields: `transformationType`...
    # TODO: AC without standard structure – refine manually
    # - Detail panel of a `bpmn-data-output-association` shows fields: `transformationType` (dropdown), `affectedAttributes` (tag input), `condition` (free text)
    # - These properties are stored in the repository and can be queried via API
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC4
  Scenario: AC4 – Reference existing data-object – Business analyst can select a `data-object` already present in the EA repository when creating a `bpmn-data-object`...
    # TODO: AC without standard structure – refine manually
    # - When creating a `bpmn-data-object`, the business analyst can select a `data-object` already present in the EA repository (autocomplete via `[[` trigger, REQ-070)
    # - The BPMN element then shows name and ID of the referenced object; no duplicate is created
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC5
  Scenario: AC5 – Link DataStore to technical infrastructure – `bpmn-data-store` can be linked via `realizes` connection to a `data-component`...
    # TODO: AC without standard structure – refine manually
    # - `bpmn-data-store` can be linked via a `realizes` connection to a `data-component` from the EA repository
    # - Linking: context menu "Realized by" → search in the EA repository
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC6
  Scenario: AC6 – Lineage query through process – `GET /api/v1/entities/{id}/lineage?direction=downstream` for a `data-object`...
    # TODO: AC without standard structure – refine manually
    # - `GET /api/v1/entities/{id}/lineage?direction=downstream` for a `data-object` also returns BPMN tasks that enrich this object via `bpmn-data-output-association`
    # - Analogous upstream: tasks that read it as input
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC7
  Scenario: AC7 – Catalog join on process data – Catalog with `data-object` as primaryEntityType can show via join on `bpmn-...
    # TODO: AC without standard structure – refine manually
    # - A catalog with `data-object` as primaryEntityType can show via join on `bpmn-data-output-association` which process tasks create or enrich each DataObject
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC8
  Scenario: AC8 – Web portal read-only – BPMN diagram with DataObjects is readable in the web portal; DataObject names...
    # TODO: AC without standard structure – refine manually
    # - BPMN diagram with DataObjects is readable in the web portal; DataObject names and transformationType annotations are visible
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement
