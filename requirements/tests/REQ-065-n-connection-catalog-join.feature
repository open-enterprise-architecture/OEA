# Ableitung aus: requirements/req/req-065-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-06 @UC-08
Feature: REQ-065 – Katalog-Join für Connection-Typ-Primaries und n-Connection-Traversal

  Das System MUSS für
  [Catalog](../../business-objects/catalog.md)-Instanzen zwei
  Erweiterungen der Join-Logik (REQ-045/046) unterstützen: 1.
  **Connection-Typ als Primary**: `primaryEntityType` DARF auf einen
  EntityType mit `isConnection=true` gesetzt werden (z.B.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Connection-Typ als Primary – MetamodelConfiguration mit EntityType `data-flow` (isConnection=true)
    Given MetamodelConfiguration mit EntityType `data-flow` (isConnection=true)
    When  Katalog-Manager legt Catalog mit `primaryEntityType=data-flow` an
    Then  HTTP 201; Catalog ist valide; Abfrage liefert alle data-flow-Instanzen als Zeilen

  @AC2
  Scenario: AC2 – n-Connection-Join outbound – DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, target...
    Given DataFlow id=5 (data-flow); carries-data id=103 (sourceEntityId=5, targetEntityId=42)
    And   Catalog `Data-Flow-Übersicht` (primaryEntityType=data-flow) mit JoinDefinition: `{ connectionType: carries-data, joinDirection: outbound, targetEntityType: data-object, targetColumns: [{attributeName: "name"}] }`
    When  Catalog-Abfrage
    Then  Zeile für DataFlow id=5; joinResults enthält DataObject id=42 mit name „Kundenstamm"

  @AC3
  Scenario: AC3 – Nicht-n-Connection bleibt unberührt – normaler Catalog mit primaryEntityType=application-component und Join da...
    Given normaler Catalog mit primaryEntityType=application-component und Join data-flow outbound
    When  Catalog-Abfrage
    Then  Ergebnis identisch mit REQ-046-Verhalten; kein Regressionsfehler

  @AC4
  Scenario: AC4 – Mehrere n-Connections aggregiert – DataFlow id=5 hat 2 carries-data-Verbindungen (data-objects 42 und 43)
    Given DataFlow id=5 hat 2 carries-data-Verbindungen (data-objects 42 und 43)
    When  Catalog-Abfrage, aggregate-Modus
    Then  Eine Zeile für DataFlow id=5; joinResults enthält Array mit beiden data-objects

  @AC5
  Scenario: AC5 – Connection-Primary: Quell- und Ziel-Entity als Spalten verfügbar – Catalog mit primaryEntityType=data-flow; Spalten `sourceEntityName`, `ta...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Catalog mit primaryEntityType=data-flow; Spalten `sourceEntityName`, `targetEntityName` konfiguriert
    Then  Spalten zeigen Namen der Quell- bzw. Ziel-Entität jedes Dataflusses
