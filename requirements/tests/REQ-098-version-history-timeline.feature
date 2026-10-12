# Ableitung aus: requirements/req/req-098-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-14
Feature: REQ-098 – Versionshistorie als chronologische Zeitlinie

  Das System MUSS für jede ArchitectureEntity eine chronologische
  Versionshistorie (Zeitlinie) anzeigen. Pro Version MÜSSEN folgende
  Informationen sichtbar sein: Versionsnummer, Zeitstempel (UTC), Name
  der ändernden Person (verlinkt auf Profil), geänderte Felder (aus
  `EntityVersion.changedFields`) und optionaler Änderungsgrund.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Zeitlinie mit Versionen – eine Entität mit 5 Versionen geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Entität mit 5 Versionen geöffnet wird
    Then  zeigt die Zeitlinie 5 Einträge, neueste zuerst

  @AC2
  Scenario: AC2 – Filter nach Actor – die Zeitlinie nach Actor "Michael" gefiltert wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Zeitlinie nach Actor "Michael" gefiltert wird
    Then  werden nur Versionen angezeigt, die von Michael erstellt wurden

  @AC3
  Scenario: AC3 – Entität ohne Historie – eine Entität ohne Änderungshistorie geöffnet wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Entität ohne Änderungshistorie geöffnet wird
    Then  zeigt der Tab "Keine Änderungen seit Anlage. Erstellt am [Datum] von [Person]."
