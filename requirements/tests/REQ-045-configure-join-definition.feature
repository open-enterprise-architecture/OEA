# Ableitung aus: requirements/req/req-045-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-06
Feature: REQ-045 – Join-Definition zu einem Katalog hinzufügen

  Das System MUSS dem Katalog-Manager ermöglichen, einem
  [Catalog](../../business-objects/catalog.md)
  `JoinDefinition`-Einträge hinzuzufügen, zu bearbeiten und zu
  entfernen. Eine `JoinDefinition` besteht aus: `name` (Pflicht),
  `connectionType` (Pflicht; EntityType mit `isConnection=true` –
  BR-02), `joinDirection` (Pflicht: `outbound` | `inbound` | `both`),
  `targetEntityType` (Pflicht; muss laut
  `allowedSourceTypes`/`allowedTargetTypes` des ConnectionTypes
  erreichbar sein – BR-03), `targetColumns` (ColumnConfig[]; Attribute
  der Zielentität), `defaultJoinMode` (optional; `rows` | `aggregate`;
  Default = Catalog.defaultJoinMode) und `aggregateLabel` (optional;
  Label der Aggregat-Zelle).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Join hinzufügen, Happy Path – Catalog „Application Inventory" mit primaryEntityType=ApplicationCompone...
    Given Catalog „Application Inventory" mit primaryEntityType=ApplicationComponent; MetamodelConfiguration hat ConnectionType `DataFlow` (isConnection=true, allowedSourceTypes=[ApplicationComponent], allowedTargetTypes=[Interface])
    When  `POST /api/v1/catalogs/{id}/joins` mit `{ name: "Schnittstellen", connectionType: "DataFlow", joinDirection: "outbound", targetEntityType: "Interface", targetColumns: [{attributeName: "name", sortOrder: 0}], defaultJoinMode: "aggregate" }`
    Then  HTTP 201; Catalog enthält neue JoinDefinition mit generierter id

  @AC2
  Scenario: AC2 – BR-02 – connectionType kein Connection-Typ – `ApplicationComponent` hat `isConnection=false`
    Given `ApplicationComponent` hat `isConnection=false`
    When  `connectionType: "ApplicationComponent"` im Request
    Then  HTTP 422 „Der Typ ‹ApplicationComponent› ist kein Connection-Typ (isConnection=false)"

  @AC3
  Scenario: AC3 – BR-03 – targetEntityType nicht erreichbar – DataFlow hat `allowedTargetTypes=[Interface]`
    Given DataFlow hat `allowedTargetTypes=[Interface]`
    When  `targetEntityType: "TechnologyComponent"` bei `joinDirection=outbound`
    Then  HTTP 422 „Der EntityType ‹TechnologyComponent› ist über ‹DataFlow› in Richtung outbound nicht erreichbar"

  @AC4
  Scenario: AC4 – Join entfernen – `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `DELETE /api/v1/catalogs/{id}/joins/{joinId}`
    Then  HTTP 200; JoinDefinition entfernt; bestehende SavedViews werden um JoinModeOverrides für diesen Join bereinigt

  @AC5
  Scenario: AC5 – targetColumns-Validierung – Interface hat kein Attribut `nonExistingProp`
    Given Interface hat kein Attribut `nonExistingProp`
    When  targetColumns enthält `attributeName: "nonExistingProp"`
    Then  HTTP 422 „Das Attribut ‹nonExistingProp› ist für den Typ ‹Interface› nicht definiert"
