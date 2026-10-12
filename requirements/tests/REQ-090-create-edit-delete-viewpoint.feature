# Ableitung aus: requirements/req/req-090-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-12
Feature: REQ-090 – Viewpoint anlegen, bearbeiten und löschen

  Das System MUSS das Anlegen, Bearbeiten und Löschen von user-defined
  `ViewpointDefinition`-Einträgen ermöglichen. Ein Viewpoint besteht
  aus: `name` (Pflicht, eindeutig in der Instanz), `notation` (Pflicht:
  `archimate3|uml|bpmn20`), `allowedEntityTypes[]`,
  `allowedConnectionTypes[]` und `notationMappings[]` (pro EntityType:
  `notationElement`, `defaultWidth`, `defaultHeight`).

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Viewpoint anlegen – ein neuer Viewpoint mit 3 EntityTypes angelegt wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein neuer Viewpoint mit 3 EntityTypes angelegt wird
    Then  antwortet die API mit HTTP 201; der Viewpoint ist sofort für neue Diagramme auswählbar

  @AC2
  Scenario: AC2 – EntityType entfernen mit Warnung – beim Bearbeiten ein EntityType aus `allowedEntityTypes` entfernt wird, d...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  beim Bearbeiten ein EntityType aus `allowedEntityTypes` entfernt wird, der in bestehenden Diagrammen genutzt wird
    Then  zeigt das System eine Warnung "N Diagramme betroffen"; die Bearbeitung wird nicht blockiert

  @AC3
  Scenario: AC3 – Viewpoint löschen – ein user-defined Viewpoint gelöscht wird
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  ein user-defined Viewpoint gelöscht wird
    Then  wird er aus der `MetamodelConfiguration` entfernt
