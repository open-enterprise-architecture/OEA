# Ableitung aus: requirements/req/req-105-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-16
Feature: REQ-105 – Teilwiederherstellung selektive Feldauswahl

  Im Wizard MÜSSEN ausschliesslich Felder auswählbar sein, die sich
  zwischen Quelle und aktuellem Stand unterscheiden. Identische Felder
  MÜSSEN ausgegraut und nicht anwählbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Identische Felder ausgegraut – ein Feld in Quelle und aktuellem Stand identisch ist
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein Feld in Quelle und aktuellem Stand identisch ist
    Then  ist die Checkbox ausgegraut und nicht anklickbar

  @AC2
  Scenario: AC2 – Neue Properties gekennzeichnet – eine Property "cost-center" in der Quellversion noch nicht vorhanden war
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Property "cost-center" in der Quellversion noch nicht vorhanden war
    Then  zeigt der Wizard "(in Quellversion nicht vorhanden)"; Checkbox nicht anwählbar

  @AC3
  Scenario: AC3 – Vorschau bei Feldauswahl – die Checkbox eines geänderten Feldes aktiviert wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Checkbox eines geänderten Feldes aktiviert wird
    Then  wird der Wert aus der Quellversion als Vorschau angezeigt
