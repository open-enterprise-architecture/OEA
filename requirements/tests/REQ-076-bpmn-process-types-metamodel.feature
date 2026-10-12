# Ableitung aus: requirements/req/req-076-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (automatisiert, API- und Integrationstests)

@functional @must @UC-10 @UC-04
Feature: REQ-076 – BPMN-Prozesselemente und OrganizationalUnit im Metamodell

  Das System MUSS einen vollständigen Satz built-in
  `EntityTypeDefinitions` für BPMN 2.0-Prozessmodellierung
  bereitstellen (Pool, Lane, Task-Varianten, Events, Gateways,
  SequenceFlow, MessageFlow). Zusätzlich MUSS `organizational-unit` als
  built-in EntityType verfügbar sein.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – Built-in BPMN-Typen vorhanden – OEA-Instanz gestartet; Admin öffnet MetamodelConfiguration
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  OEA-Instanz gestartet; Admin öffnet MetamodelConfiguration
    Then  Alle 19 built-in BPMN-EntityTypes und 9 built-in Connections sind sichtbar (isBuiltIn=true); unveränderlich in ihren Basiseigenschaften

  @AC2
  Scenario: AC2 – Erweiterbarkeit – Admin legt `EntityTypeDefinition { id: "approval-task", extends: "bpmn-u...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin legt `EntityTypeDefinition { id: "approval-task", extends: "bpmn-user-task" }` an
    Then  `approval-task` erbt alle Attribute von `bpmn-user-task`; erscheint in BPMN-Palette; kann in Prozessdiagrammen genutzt werden

  @AC3
  Scenario: AC3 – OrganizationalUnit Typ – Admin öffnet MetamodelConfiguration
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Admin öffnet MetamodelConfiguration
    Then  `organizational-unit` als built-in EntityType sichtbar; per `extends` zu `department`, `team`, `business-unit` etc. erweiterbar

  @AC4
  Scenario: AC4 – Pool/Lane Nested-Layout – Prozessdiagramm mit Pool → Lane → Task-Hierarchie via `bpmn-contained-in...
    Given Prozessdiagramm mit Pool → Lane → Task-Hierarchie via `bpmn-contained-in`-Connections
    When  Canvas lädt das Diagramm
    Then  Pool erscheint als äusserer Container; Lanes als Swim-Lanes; Tasks innerhalb der korrekten Lanes; Layout korrekt verschachtelt

  @AC5
  Scenario: AC5 – SequenceFlow Constraint – Nutzer versucht, einen `bpmn-sequence-flow` von einem BPMN-Task zu einer...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer versucht, einen `bpmn-sequence-flow` von einem BPMN-Task zu einer Nicht-BPMN-Entität (z.B. ApplicationComponent) zu ziehen
    Then  API gibt HTTP 422 zurück (BR-01); UI zeigt Fehlermeldung

  @AC6
  Scenario: AC6 – BPMN-Viewpoint – Nutzer legt neues Diagramm an und wählt den built-in Viewpoint `bpmn-pro...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Nutzer legt neues Diagramm an und wählt den built-in Viewpoint `bpmn-process-view`
    Then  Palette zeigt ausschliesslich BPMN-konforme Elemente; Pool/Lane-Modus ist aktiv (Container-Drop möglich)
