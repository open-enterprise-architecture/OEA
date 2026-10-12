# Ableitung aus: requirements/req/req-067-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @should @UC-04 @UC-09
Feature: REQ-067 – Arc42ChapterCollection im Metamodell konfigurieren

  Das System MUSS dem Metamodell-Administrator ermöglichen,
  `Arc42ChapterCollection`-Objekte in der MetamodelConfiguration
  anzulegen, zu bearbeiten und zu löschen. Eine Collection besteht aus
  einem `name`, einer geordneten Liste von
  `Arc42QuestionTemplate`-Einträgen und einer Liste von
  `assignedEntityTypeIds`.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Collection anlegen – Admin postet `{ id: "arc42-kmu", name: "Arc42 KMU", questions: [...], as...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin postet `{ id: "arc42-kmu", name: "Arc42 KMU", questions: [...], assignedEntityTypeIds: ["application-component"] }`
    Then  HTTP 201; Collection in MetamodelConfiguration gespeichert; EntityType `application-component` zeigt Collection beim Öffnen

  @AC2
  Scenario: AC2 – Frage hinzufügen – Admin fügt `{ questionId: "context", title: "Kontext", questionText: ".....
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin fügt `{ questionId: "context", title: "Kontext", questionText: "...", sortOrder: 1 }` zu einer Collection hinzu
    Then  Frage erscheint an Position 1 in der Collection

  @AC3
  Scenario: AC3 – Built-in Standard-Collection importieren – Admin klickt „Standard Arc42 importieren"
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin klickt „Standard Arc42 importieren"
    Then  Collection `arc42-standard` mit 12 Fragen wird als user-definierte Kopie angelegt (editierbar); Original bleibt unveränderlich

  @AC4
  Scenario: AC4 – Mehrere zugewiesene EntityTypes – `assignedEntityTypeIds: ["application-component", "technology-component"]`
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  `assignedEntityTypeIds: ["application-component", "technology-component"]`
    Then  Beide EntityTypes zeigen die Collection im Arc42-Tab

  @AC5
  Scenario: AC5 – Löschen Collection – Admin löscht eine Collection, die bereits Antwort-Entitäten besitzt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin löscht eine Collection, die bereits Antwort-Entitäten besitzt
    Then  HTTP 422; Meldung: „Collection hat {N} Antworten — zuerst Antworten entfernen oder Collection als inaktiv markieren"; kein Datenverlust
