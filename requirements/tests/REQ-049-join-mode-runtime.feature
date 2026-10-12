# Ableitung aus: requirements/req/req-049-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-049 – Join-Modus eines Katalogs zur Laufzeit umschalten

  Das System MUSS allen Katalog-Besuchern ermöglichen, den Join-Modus
  (`rows` | `aggregate`) pro JoinDefinition zur Laufzeit zu ändern,
  ohne die gespeicherte Catalog-Konfiguration zu verändern. Die Tabelle
  MUSS nach dem Umschalten ohne Seiten-Neuladen aktualisiert werden.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – rows-Modus via Parameter – Catalog „Application Inventory" mit JoinDefinition id=join-1 (DataFlow→I...
    Given Catalog „Application Inventory" mit JoinDefinition id=join-1 (DataFlow→Interface), defaultJoinMode=aggregate; CRM-System hat 2 Interfaces
    When  `GET /api/v1/catalogs/{id}/query?joinMode[join-1]=rows`
    Then  Response enthält 2 Zeilen für CRM-System (je Interface eine); Catalog.joinDefinitions[0].defaultJoinMode bleibt `aggregate` (unverändert)

  @AC2
  Scenario: AC2 – aggregate-Modus, gespeicherter Default greift – wie AC1
    Given wie AC1
    When  `GET /api/v1/catalogs/{id}/query` (ohne joinMode-Parameter)
    Then  Response enthält 1 Zeile für CRM-System mit joinResults.Schnittstellen als Array

  @AC3
  Scenario: AC3 – Keine Mutation am Catalog – Nach AC1: `GET /api/v1/catalogs/{id}` (Catalog-Konfiguration abrufen)
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When die beschriebene Aktion ausgeführt wird
    Then  `joinDefinitions[0].defaultJoinMode = "aggregate"` (unverändert)

  @AC4
  Scenario: AC4 – Unbekannte joinDefinitionId – `joinMode[nonexistent-id]=rows`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `joinMode[nonexistent-id]=rows`
    Then  HTTP 400 „JoinDefinition ‹nonexistent-id› nicht gefunden"

  @AC5
  Scenario: AC5 – Mehrere Joins gleichzeitig überschreiben – Catalog hat 2 JoinDefinitions (join-1 defaultMode=aggregate, join-2 defa...
    Given Catalog hat 2 JoinDefinitions (join-1 defaultMode=aggregate, join-2 defaultMode=aggregate)
    When  `?joinMode[join-1]=rows&joinMode[join-2]=aggregate`
    Then  join-1 liefert Zeilen-Expansion; join-2 bleibt aggregiert
