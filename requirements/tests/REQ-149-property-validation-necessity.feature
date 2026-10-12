# Ableitung aus: requirements/req/req-149-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert)

@functional @must @UC-04 @UC-06
Feature: REQ-149 – Eigenschafts-Validierung nach Notwendigkeit

  Das System **MUSS** beim Speichern einer Entität die Property-Werte
  gegen die aktiven Metatyp-Property-Mappings validieren und bei
  `necessity=mandatory` einen Fehler, bei `necessity=warning` eine
  Warnung zurückgeben; `necessity=optional` erzeugt keinen Hinweis.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – MetaTyp M hat Property `version` mit necessity=mandatory
    Given MetaTyp M hat Property `version` mit necessity=mandatory
    When  POST /entities mit MetaTyp M ohne Property `version`
    Then  422; errors[0].field = "properties", errors[0].code = "MANDATORY_MISSING"

  @AC2
  Scenario: AC2 – MetaTyp M hat Property `owner` mit necessity=warning
    Given MetaTyp M hat Property `owner` mit necessity=warning
    When  POST /entities mit MetaTyp M ohne Property `owner`
    Then  201 Created; Response enthält warnings[0].field = "properties[owner]"

  @AC3
  Scenario: AC3 – POST /entities/batch mit dryRun=true
    Given POST /entities/batch mit dryRun=true
    When  mehrere Entitäten, einige mit mandatory-Verletzung
    Then  ValidationResult.valid=false; alle Fehler pro Item aufgelistet; keine Persistierung
