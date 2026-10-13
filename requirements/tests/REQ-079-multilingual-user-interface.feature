# Derived from: requirements/req/req-079-*.md
# V-model system test specification – created 2026-06-30

@functional @should
Feature: REQ-079 – Multilingual user interface (i18n)

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Default English – A freshly set up OEA instance shows the UI entirely in English...
    # TODO: AC without standard structure – refine manually
    # - A freshly set up OEA instance shows the UI entirely in English without further configuration
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC2
  Scenario: AC2 – Switch language – Admin has activated an additional language
    Given the precondition according to the requirement is met
    When  Admin has activated an additional language
    Then  every user can select this language in the profile settings

  @AC3
  Scenario: AC3 – EntityType display name multilingual – EntityTypeDefinition supports `displayName: { en: "...", de: "..." }`...
    # TODO: AC without standard structure – refine manually
    # - EntityTypeDefinition supports `displayName: { en: "...", de: "..." }` as a language map
    # - The UI always shows the display name in the active user language; fallback: English; second fallback: technical identifier
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC4
  Scenario: AC4 – Fallback for missing translation – No empty string in the UI: if a key is missing in the active language, the Eng...
    # TODO: AC without standard structure – refine manually
    # - No empty string in the UI: if a key is missing in the active language, the English text appears (no `undefined`)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC5
  Scenario: AC5 – Language packs importable – Admin can upload and activate JSON language packs via the admin UI
    # TODO: AC without standard structure – refine manually
    # - Admin can upload and activate JSON language packs via the admin UI
    # - Format: flat JSON `{ "entity.create": "Create entity", ... }`
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC6
  Scenario: AC6 – Instance default configurable – Selectable during bootstrapping (UC-02): which language is the instance default...
    # TODO: AC without standard structure – refine manually
    # - Selectable during bootstrapping (UC-02): which language is the instance default language (fallback for users without their own preference)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement

  @AC7
  Scenario: AC7 – Offline-capable – Language packs are loaded and cached at app start; no external CDN access at runtime...
    # TODO: AC without standard structure – refine manually
    # - Language packs are loaded and cached at app start; no external CDN access at runtime (REQ-075)
    Given the precondition according to the requirement is met
    When the described action is performed
    Then the system behaves according to the statement
