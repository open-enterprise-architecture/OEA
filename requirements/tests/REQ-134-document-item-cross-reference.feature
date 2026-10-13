# Derived from: requirements/req/req-134-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-09
Feature: REQ-134 – DocumentItem cross-reference via {{ in the WYSIWYG editor

  In the WYSIWYG editor of DocumentItems, the system MUST recognize typing
  `{{` as the trigger for an autocomplete dropdown that searches
  DocumentItems of the **current DocumentCollection**.
  The search covers both `alias` and `name`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Autocomplete trigger – user types `{{` in the free-text area
    Given the precondition according to the requirement is met
    When  user types `{{` in the free-text area
    Then  dropdown opens with all DocumentItems of the current collection; search over `alias` and `name`; first result preselected

  @AC2
  Scenario: AC2 – Insert – user selects a DocumentItem from the dropdown
    Given the precondition according to the requirement is met
    When  user selects a DocumentItem from the dropdown
    Then  `{{Chapter name|item:uuid}}` is inserted at the cursor position; dropdown closes

  @AC3
  Scenario: AC3 – Rendering — existing name – DocumentItem with cross-reference is rendered and the target exists
    Given the precondition according to the requirement is met
    When  DocumentItem with cross-reference is rendered and the target exists
    Then  clickable inline link displayed with the current `name` of the target item; click navigates to the target chapter

  @AC4
  Scenario: AC4 – Rendering — renamed target – target item is renamed
    Given the precondition according to the requirement is met
    When  target item is renamed
    Then  all cross-references to this item automatically show the new name (ID resolution)

  @AC5
  Scenario: AC5 – Rendering — deleted target – target item is deleted
    Given the precondition according to the requirement is met
    When  target item is deleted
    Then  reference shows `[deleted chapter|item:uuid]` in red font; no navigation possible

  @AC6
  Scenario: AC6 – Code block – user types `{{` in a Mermaid or PlantUML block and selects
    Given the precondition according to the requirement is met
    When  user types `{{` in a Mermaid or PlantUML block and selects
    Then  plain `name` text is saved (no `|item:uuid`); no link in the rendering
