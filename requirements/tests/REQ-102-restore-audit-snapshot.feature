# Ableitung aus: requirements/req/req-102-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-15
Feature: REQ-102 – Audit-Snapshot vor jeder Wiederherstellung

  Vor jeder Wiederherstellung (vollständig UC-15 oder teilweise UC-16)
  MUSS das System den aktuellen Entitätszustand als unveränderlichen
  EntityVersion-Snapshot sichern. Der neue EntityVersion-Eintrag MUSS
  `restoredFromVersion` (Quellversion) enthalten.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Snapshot-Eintrag nach Wiederherstellung – eine Wiederherstellung ausgeführt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Wiederherstellung ausgeführt wird
    Then  ist ein neuer EntityVersion-Eintrag mit `restoredFromVersion=N` in der Zeitlinie sichtbar

  @AC2
  Scenario: AC2 – Markierung in Zeitlinie – die Zeitlinie nach einer Wiederherstellung angezeigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Zeitlinie nach einer Wiederherstellung angezeigt wird
    Then  zeigt der neue Eintrag die Markierung "Wiederhergestellt aus vN"

  @AC3
  Scenario: AC3 – Snapshot vor Wiederherstellung erhalten – die Wiederherstellung abgeschlossen ist
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Wiederherstellung abgeschlossen ist
    Then  ist der Zustand VOR der Wiederherstellung als eigenständiger Snapshot in `entity_versions` vorhanden
