# Ableitung aus: requirements/req/req-066-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30

@functional @must @UC-04 @UC-05 @UC-06 @UC-08
Feature: REQ-066 – Konfigurierbare Entity-Anlage-Workflows (Wizard)

  Das System MUSS für jeden `EntityTypeDefinition` mit mindestens einem
  konfigurierten `creationStep` beim Anlegen einer neuen Entität — egal
  ob im Katalog-Kontext oder im Diagramm-Kontext — automatisch einen
  mehrseitigen Wizard öffnen. Jeder `CreationStep` entspricht genau
  einer Pop-over-Seite.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Wizard öffnet sich automatisch – Katalog-Kontext – ApplicationComponent hat 3 `creationSteps`
    Given ApplicationComponent hat 3 `creationSteps`
    When  Lukas klickt „+ Neue Entität" im ApplicationComponent-Catalog
    Then  Wizard öffnet sich auf Seite 1 „Basisdaten"; kein klassisches Anlage-Modal

  @AC2
  Scenario: AC2 – Wizard öffnet sich automatisch – Diagramm-Kontext – Lukas platziert ein ApplicationComponent-Element im Diagramm (Drag & Dro...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas platziert ein ApplicationComponent-Element im Diagramm (Drag & Drop aus Palette)
    Then  Wizard öffnet sich sofort; Entität ist erst nach „Fertig" auf dem Canvas sichtbar

  @AC3
  Scenario: AC3 – Navigation Weiter / Fertig – 3 Schritte
    Given 3 Schritte
    When  Lukas navigiert: Weiter → Weiter → Fertig
    Then  Schritt 1 → Schritt 2 → Schritt 3 → Fertig; Entität und alle Connections atomisch persistiert

  @AC4
  Scenario: AC4 – Zurück-Navigation – Lukas ist auf Schritt 2 und klickt „Zurück"
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas ist auf Schritt 2 und klickt „Zurück"
    Then  Schritt 1 wird wieder angezeigt; eingegebene Daten aus Schritt 1 bleiben erhalten

  @AC5
  Scenario: AC5 – Mandatory Property blockiert Fertig – Schritt 1 hat Property `owner` (validationMode=mandatory); Lukas lässt e...
    Given Schritt 1 hat Property `owner` (validationMode=mandatory); Lukas lässt es leer
    When  Lukas klickt „Fertig" (oder versucht nach Schritt 3 zu gelangen)
    Then  Fehlermeldung auf dem entsprechenden Schritt; keine Persistierung

  @AC6
  Scenario: AC6 – connectionAssignment minConnections=1 – Schritt 3 „Capability-Verknüpfung" mit minConnections=1
    Given Schritt 3 „Capability-Verknüpfung" mit minConnections=1
    When  Lukas klickt „Fertig" ohne eine Capability ausgewählt zu haben
    Then  Schritt 3 zeigt Fehler „Mind. 1 Capability muss verknüpft werden"; keine Persistierung

  @AC7
  Scenario: AC7 – Abbruch = keine Entität – Lukas schliesst den Wizard (ESC oder X) auf Schritt 2
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Lukas schliesst den Wizard (ESC oder X) auf Schritt 2
    Then  Keine Entität im Repository; keine teilangelegte Verbindung

  @AC8
  Scenario: AC8 – Kein Wizard ohne creationSteps – EntityType `stereotype` ohne `creationSteps` (leere Liste)
    Given EntityType `stereotype` ohne `creationSteps` (leere Liste)
    When  Nutzer legt neue Instanz an
    Then  Klassisches Anlage-Modal (kein Wizard); kein Regressionsfehler

  @AC9
  Scenario: AC9 – Atomische Persistierung – Wizard mit Entität + 2 Connections
    Given Wizard mit Entität + 2 Connections
    When  Die zweite Connection-Erstellung schlägt serverseitig fehl (z.B. Zielentität gelöscht)
    Then  Weder Entität noch erste Connection sind persistent; HTTP 422 mit Fehlerbeschreibung
