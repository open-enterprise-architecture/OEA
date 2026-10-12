# Ableitung aus: requirements/req/req-106-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-16
Feature: REQ-106 – Teilwiederherstellung restoredFields Protokoll

  Bei jeder Teilwiederherstellung MUSS der neue EntityVersion-Eintrag
  die exakte Liste der wiederhergestellten Felder in Dot-Notation als
  `restoredFields` enthalten (z.B. `["description",
  "properties.owner"]`).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – restoredFields im Versionseintrag – eine Teilwiederherstellung mit `description` und `properties.owner` ausg...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine Teilwiederherstellung mit `description` und `properties.owner` ausgeführt wird
    Then  enthält der neue EntityVersion-Eintrag `restoredFields=["description","properties.owner"]`

  @AC2
  Scenario: AC2 – Leere Auswahl abweisen – keine Felder ausgewählt wurden und der Bestätigen-Button geklickt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  keine Felder ausgewählt wurden und der Bestätigen-Button geklickt wird
    Then  ist der Button deaktiviert; ein API-Aufruf mit leerem `restoredFields` wird mit HTTP 422 abgewiesen

  @AC3
  Scenario: AC3 – Zeitlinie-Lesbarkeit – die Zeitlinie nach einer Teilwiederherstellung angezeigt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  die Zeitlinie nach einer Teilwiederherstellung angezeigt wird
    Then  zeigt der Eintrag "Teilweise wiederhergestellt aus v4 (description, properties.owner)"
