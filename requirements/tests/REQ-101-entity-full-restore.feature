# Ableitung aus: requirements/req/req-101-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-15
Feature: REQ-101 – Vollwiederherstellung einer Entität auf früheren Stand

  Das System MUSS die Wiederherstellung einer ArchitectureEntity auf
  einen beliebigen früheren Stand ermöglichen. Vor der Ausführung MUSS
  ein Bestätigungsdialog angezeigt werden, der den Diff (aktuell vs.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Wiederherstellung auf früheren Stand – eine Entität auf Version v3 wiederhergestellt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Entität auf Version v3 wiederhergestellt wird
    Then  sind `name`, `description` und `properties` auf v3-Werte gesetzt; `id` ist unverändert

  @AC2
  Scenario: AC2 – Bestätigungsdialog mit Diff – die Wiederherstellung initiiert wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Wiederherstellung initiiert wird
    Then  zeigt der Bestätigungsdialog, welche Felder sich zwischen aktuellem Stand und Zielversion unterscheiden

  @AC3
  Scenario: AC3 – Abbruch ohne Änderung – der Dialog abgebrochen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  der Dialog abgebrochen wird
    Then  bleibt die Entität unverändert
