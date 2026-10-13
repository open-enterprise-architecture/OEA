# Derived from: requirements/req/req-070-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-09
Feature: REQ-070 – Entity mention via [[ in the WYSIWYG editor (autocomplete + linking)

  Everywhere in the WYSIWYG editor — in Markdown free-text sections,
  in Mermaid/PlantUML code blocks, and in Draw.io shape labels — the
  system MUST open an autocomplete dropdown when typing `[[` that
  searches entities in the architecture repository live. After an
  entity is selected, the system MUST insert the mention depending on
  context: as a structured wiki link `[[Name|entity:ID]]` in free
  text, as plain text name in all code block contexts.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Dropdown opens in free text – Michael types `[[` in the Markdown free text of the editor
    Given the precondition according to the requirement is met
    When  Michael types `[[` in the Markdown free text of the editor
    Then  autocomplete dropdown appears with top 10 entities; no page load needed

  @AC2
  Scenario: AC2 – Live search filters results – Michael types `[[CRM`
    Given the precondition according to the requirement is met
    When  Michael types `[[CRM`
    Then  dropdown shows only entities whose name contains "CRM" (case-insensitive); results update with every further character

  @AC3
  Scenario: AC3 – Mention in free text — ID-stable – Michael selects "CRM-System" (id=1) in the dropdown
    Given the precondition according to the requirement is met
    When  Michael selects "CRM-System" (id=1) in the dropdown
    Then  `[[CRM-System|entity:1]]` is inserted; rendering shows clickable badge "CRM-System"

  @AC4
  Scenario: AC4 – Rename is reflected in rendering – entity id=1 is renamed from "CRM-S...
    Given entity id=1 is renamed from "CRM-System" to "Salesforce CRM"
    When  Michael opens the Arc42 answer again
    Then  badge shows "Salesforce CRM"; raw text in `content` remains `[[CRM-System|entity:1]]` (unchanged raw value)

  @AC5
  Scenario: AC5 – Deleted entity – entity id=1 was deleted
    Given entity id=1 was deleted
    When  Arc42 answer with `[[CRM-System|entity:1]]` opened
    Then  badge shows `[deleted]` in red text; no link; no exception

  @AC6
  Scenario: AC6 – Dropdown in Mermaid block – Michael types `[[CRM` inside a ` ```merma...
    Given the precondition according to the requirement is met
    When  Michael types `[[CRM` inside a ` ```mermaid ` code block (in edit mode)
    Then  autocomplete dropdown appears; after selection only the text name `CRM-System` is inserted (no link markup)

  @AC7
  Scenario: AC7 – Dropdown in PlantUML block – As AC6, for PlantUML code block
    # TODO: AC without standard structure – refine manually
    # - As AC6, for PlantUML code block
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC8
  Scenario: AC8 – ESC closes dropdown without change – Michael types `[[CR` and presses ESC
    Given the precondition according to the requirement is met
    When  Michael types `[[CR` and presses ESC
    Then  dropdown closes; `[[CR` remains as raw text

  @AC9
  Scenario: AC9 – Web portal: mentions rendered, no edit – CIO opens Arc42 documentation in the web portal
    Given the precondition according to the requirement is met
    When  CIO opens Arc42 documentation in the web portal
    Then  mentions rendered as badges (with current name); no dropdown trigger on clicking a badge in read mode (badge opens entity detail)
