# Ableitung aus: requirements/req/req-104-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-16
Feature: REQ-104 – Teilwiederherstellung dreistufiger Wizard

  Das System MUSS einen dreistufigen Wizard für die selektive
  Wiederherstellung bereitstellen: Schritt 1 General Properties
  (`name`, `description`, `isLogical`), Schritt 2 Custom Properties
  (alle konfigurierten Properties aus `snapshot.propertyDefinitions`),
  Schritt 3 Verbindungsübersicht (informativ:
  geändert/hinzugekommen/entfernt). Jeder Schritt MUSS einzeln
  überspringbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Quellversion vorausgefüllt – der Wizard aus der Zeitlinie (UC-14) für Version v3 gestartet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der Wizard aus der Zeitlinie (UC-14) für Version v3 gestartet wird
    Then  ist die Quellversion v3 vorausgefüllt

  @AC2
  Scenario: AC2 – Automatisches Überspringen – Schritt 1 keine Unterschiede in General Properties enthält
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Schritt 1 keine Unterschiede in General Properties enthält
    Then  wird Schritt 1 automatisch mit dem Hinweis "Keine Unterschiede in General Properties" übersprungen; Wizard wechselt direkt zu Schritt 2

  @AC3
  Scenario: AC3 – Bestätigen-Button bei leerer Auswahl – in allen Schritten keine Felder ausgewählt wurden
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  in allen Schritten keine Felder ausgewählt wurden
    Then  ist der Bestätigen-Button deaktiviert (gemäss REQ-106)
