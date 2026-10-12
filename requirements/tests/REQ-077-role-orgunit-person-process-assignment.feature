# Ableitung aus: requirements/req/req-077-*.md
# V-Modell Systemtestspezifikation – erstellt 2026-06-30
# Verifikation: Methode: test (Integrationstest + Playwright E2E)

@functional @must @UC-10
Feature: REQ-077 – Rollen, Organisationseinheiten und Personen Prozesselementen zuordnen

  Das System MUSS es ermöglichen, direkt im Prozessdiagramm: 1. Lanes
  eine **Organisationseinheit** (`bpmn-lane-belongs-to-org-unit`) und
  beliebig viele **Rollen** (`bpmn-lane-performs-role`) zuzuordnen 2.

  Background:
    Given eine laufende OEA-Instanz
    And ein Nutzer mit der erforderlichen Rolle ist angemeldet

  @AC1
  Scenario: AC1 – OrgUnit → Lane – Anna rechtsklickt auf eine Lane und wählt „Organisationseinheit zuordnen...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna rechtsklickt auf eine Lane und wählt „Organisationseinheit zuordnen"; sucht „Einkauf"; wählt die OrgUnit
    Then  `bpmn-lane-belongs-to-org-unit`-Connection gespeichert; Lane zeigt OrgUnit-Badge

  @AC2
  Scenario: AC2 – Mehrere Rollen → Lane – Anna einer Lane die Rollen „Genehmiger" und „Prüfer" zuordnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna einer Lane die Rollen „Genehmiger" und „Prüfer" zuordnet
    Then  Zwei `bpmn-lane-performs-role`-Connections gespeichert; beide Rollen als Chips in der Lane sichtbar

  @AC3
  Scenario: AC3 – OrgUnit → Pool – Anna den Pool mit der OrgUnit „Unternehmensbereich Vertrieb" verknüpft
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna den Pool mit der OrgUnit „Unternehmensbereich Vertrieb" verknüpft
    Then  `bpmn-pool-represents-org-unit`-Connection gespeichert; Pool-Header zeigt OrgUnit-Namen

  @AC4
  Scenario: AC4 – Person → UserTask – Anna im Detail-Panel eines UserTasks die Person „Maria Müller" sucht und...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna im Detail-Panel eines UserTasks die Person „Maria Müller" sucht und zuordnet
    Then  `bpmn-task-assigned-to`-Connection gespeichert; Task-Shape zeigt Initialen „MM" als Avatar-Icon

  @AC5
  Scenario: AC5 – Zuordnung entfernen – Anna eine bestehende Zuordnung (z.B. OrgUnit einer Lane) entfernt
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna eine bestehende Zuordnung (z.B. OrgUnit einer Lane) entfernt
    Then  Connection wird aus Repository gelöscht; Badge/Chip verschwindet sofort aus dem Canvas

  @AC6
  Scenario: AC6 – Katalog-Join OrgUnit – Mehrere Lanes haben OrgUnit-Zuordnungen
    Given Mehrere Lanes haben OrgUnit-Zuordnungen
    When  Katalog für `bpmn-lane` mit Join-Spalte `bpmn-lane-belongs-to-org-unit.name` geöffnet
    Then  OrgUnit-Name als Spalte sichtbar; Filter auf OrgUnit möglich

  @AC7
  Scenario: AC7 – Katalog-Join Person – Mehrere UserTasks haben Personen-Zuordnungen
    Given Mehrere UserTasks haben Personen-Zuordnungen
    When  Katalog für `bpmn-user-task` mit Join-Spalte `bpmn-task-assigned-to.name` geöffnet
    Then  Personen-Name als Spalte; Tasks ohne Zuordnung zeigen leere Zelle

  @AC8
  Scenario: AC8 – Schnell-Anlegen OrgUnit – Anna im Zuordnungsdialog eine OrgUnit sucht, die nicht existiert, und „N...
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Anna im Zuordnungsdialog eine OrgUnit sucht, die nicht existiert, und „Neu anlegen" wählt
    Then  Mini-Wizard öffnet sich (Name, Typ auswählen); nach Bestätigung ist die neue OrgUnit sofort zugewiesen

  @AC9
  Scenario: AC9 – Web-Portal read-only – Viewer-Nutzer das Prozessdiagramm im Web-Portal öffnet
    Given die Vorbedingung gemäß Anforderung ist erfüllt
    When  Viewer-Nutzer das Prozessdiagramm im Web-Portal öffnet
    Then  OrgUnit-Badges und Personen-Avatare sichtbar; keine Edit-Aktionen verfügbar; Tooltips mit vollständigem Namen
