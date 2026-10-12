# Ableitung aus: requirements/req/req-108-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-17
Feature: REQ-108 – SBB Governance-Status verwalten

  Das System MUSS das Anlegen, Bearbeiten und Löschen von
  `SolutionBuildingBlock`-Einträgen (SBBs) mit `scope=organization`
  ermöglichen. Pflichtfelder: `name`, `governanceStatus` (`[approved,
  acceptable, deprecated, prohibited]`).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – SBB anlegen – eine SBB mit `governanceStatus=approved` und `implements=[ABB-UUID]` ang...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine SBB mit `governanceStatus=approved` und `implements=[ABB-UUID]` angelegt wird
    Then  antwortet das System mit HTTP 201

  @AC2
  Scenario: AC2 – Prohibited-Warnung auf Entitäten – eine SBB auf `governanceStatus=prohibited` gesetzt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine SBB auf `governanceStatus=prohibited` gesetzt wird
    Then  zeigen alle Entitäten mit `instanceOfSBBId=dieser-SBB` in der UI den Hinweis „Verwendetes Produkt verboten – Migration erforderlich"

  @AC3
  Scenario: AC3 – ABB-Referenz-Warnung – eine SBB ohne `implements`-ABBs angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  eine SBB ohne `implements`-ABBs angelegt wird
    Then  zeigt das System eine Governance-Warnung „SBB ohne ABB-Referenz ist nur Produktkatalog-Eintrag" ohne die Speicherung zu blockieren
