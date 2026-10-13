# Derived from: requirements/req/req-080-*.md
# V-model system test specification – created 2026-06-30

@functional @could
Feature: REQ-080 – Spell checking in the WYSIWYG editor

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Browser-native baseline – TipTap editor element has `spellcheck="true"` set
    # TODO: AC without standard structure – refine manually
    # - TipTap editor element has `spellcheck="true"` set
    # - The browser marks misspelled words with a red underline
    # - Right-click → browser correction suggestions (default behavior)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC2
  Scenario: AC2 – Language of the check – LanguageTool active: check language follows the UI language of the user selected in REQ-079...
    Given the precondition according to the requirement is met
    When  LanguageTool active: check language follows the UI language of the user selected in REQ-079; explicitly overridable in the editor
    Then the expected result has occurred

  @AC3
  Scenario: AC3 – LanguageTool configurable – configured: editor sends text to the LanguageTool API and shows errors with colored underline...
    Given the precondition according to the requirement is met
    When  configured: editor sends text to the LanguageTool API and shows errors with colored underline
    Then the expected result has occurred

  @AC4
  Scenario: AC4 – Error types with LanguageTool – Spelling errors: red underline
    # TODO: AC without standard structure – refine manually
    # - Spelling errors: red underline
    # - Grammar errors: blue underline
    # - Style hints: gray underline (can be disabled via user setting)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC5
  Scenario: AC5 – Fallback when unreachable – LanguageTool unreachable: no error in the UI; browser-native spellcheck remains active
    Given the precondition according to the requirement is met
    When  LanguageTool unreachable: no error in the UI; browser-native spellcheck remains active
    Then the expected result has occurred

  @AC6
  Scenario: AC6 – Offline / air-gapped – Self-hosted LanguageTool (`languagetool/languagetool` Docker image, LGPL)...
    # TODO: AC without standard structure – refine manually
    # - Self-hosted LanguageTool (`languagetool/languagetool` Docker image, LGPL) configurable; consistent with REQ-075
    # - No external service call if `languageToolUrl` is not set
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC7
  Scenario: AC7 – Can be disabled – User can disable spell checking in the editor via toolbar button...
    # TODO: AC without standard structure – refine manually
    # - User can disable spell checking in the editor via toolbar button (preference is saved)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement
