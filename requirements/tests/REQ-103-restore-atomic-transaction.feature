# Ableitung aus: requirements/req/req-103-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-15
Feature: REQ-103 – Wiederherstellung als atomare Transaktion

  Snapshot-Sicherung, Entitäts-Update und Schreiben des neuen
  EntityVersion-Eintrags MÜSSEN bei jeder Wiederherstellung (UC-15 und
  UC-16) in einer einzigen DB-Transaktion erfolgen. Bei technischem
  Fehler MUSS ein vollständiger Rollback erfolgen; kein
  Inkonsistenz-Zustand (Snapshot ohne Update oder Update ohne Snapshot)
  ist zulässig.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Rollback bei DB-Fehler – ein simulierter DB-Fehler nach dem Snapshot-Schreiben auftritt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein simulierter DB-Fehler nach dem Snapshot-Schreiben auftritt
    Then  erfolgt ein vollständiger Rollback; Entität ist unverändert; kein Snapshot in `entity_versions`

  @AC2
  Scenario: AC2 – Erfolgreiche Wiederherstellung – die Wiederherstellung erfolgreich abgeschlossen wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Wiederherstellung erfolgreich abgeschlossen wird
    Then  sind Snapshot, Entitäts-Update und Versionseintrag alle vorhanden und konsistent
