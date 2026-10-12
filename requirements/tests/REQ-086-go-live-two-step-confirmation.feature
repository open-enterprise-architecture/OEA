# Ableitung aus: requirements/req/req-086-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-11
Feature: REQ-086 – Go-Live zweistufige Bestätigung

  Vor der Ausführung eines Go-Live-Übergangs MUSS das System einen
  Bestätigungsdialog anzeigen, der (1) den Diff zusammenfasst
  (P1→baseline, P0→realized), (2) nicht-implemented Solutions und
  offene CEL-Violations als Warnungen auflistet und (3) die manuelle
  Eingabe des exakten Plateau-Namens als zweite Bestätigungsstufe
  erfordert.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Button deaktiviert ohne Namenseingabe – der Bestätigungsdialog ohne Namenseingabe angezeigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der Bestätigungsdialog ohne Namenseingabe angezeigt wird
    Then  ist der "Go-Live bestätigen"-Button deaktiviert

  @AC2
  Scenario: AC2 – Falscher Name – ein falscher Plateau-Name eingegeben wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein falscher Plateau-Name eingegeben wird
    Then  bleibt der Button deaktiviert; keine Fehlermeldung ist erforderlich

  @AC3
  Scenario: AC3 – Korrekter Name – der exakte Plateau-Name eingegeben wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der exakte Plateau-Name eingegeben wird
    Then  wird der Button aktiviert und Go-Live kann ausgeführt werden
