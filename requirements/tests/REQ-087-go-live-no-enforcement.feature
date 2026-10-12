# Ableitung aus: requirements/req/req-087-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-087 – Go-Live ohne Enforcement durch Warnungen

  Go-Live DARF NICHT durch nicht-implemented Solutions oder offene
  CEL-Rule-Violations blockiert werden. Diese Zustände MÜSSEN als
  Warnungen im Bestätigungsdialog angezeigt werden (Anzahl + Liste),
  blockieren die Ausführung jedoch nicht.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Nicht-implemented Solutions als Warnung – bei Go-Live 3 nicht-implemented Solutions existieren
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  bei Go-Live 3 nicht-implemented Solutions existieren
    Then  zeigt der Dialog die Warnung "3 Solutions nicht implementiert"; Go-Live bleibt ausführbar

  @AC2
  Scenario: AC2 – CEL-Violations als Warnung – bei Go-Live CEL-Rule-Violations existieren
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  bei Go-Live CEL-Rule-Violations existieren
    Then  zeigt der Dialog die Warnung "N Business-Rule-Violations"; Go-Live bleibt ausführbar

  @AC3
  Scenario: AC3 – Kein Warnungsabschnitt ohne Warnungen – bei Go-Live weder nicht-implemented Solutions noch CEL-Violations existi...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  bei Go-Live weder nicht-implemented Solutions noch CEL-Violations existieren
    Then  wird kein Warnungsabschnitt im Dialog angezeigt
