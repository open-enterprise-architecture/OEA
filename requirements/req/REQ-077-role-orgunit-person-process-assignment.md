---
id: REQ-077
title: Rollen, Organisationseinheiten und Personen Prozesselementen zuordnen
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-10
  business_objects:
    - process
    - entity
  business_rules:
    - BR-03
    - BR-04
    - BR-05
  stakeholders:
    - SH-08
  concept:
    - concept/20-entities/09-prozess-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-077: Rollen, Organisationseinheiten und Personen Prozesselementen zuordnen

## Aussage

Das System MUSS es ermöglichen, direkt im Prozessdiagramm:
1. Lanes eine **Organisationseinheit** (`bpmn-lane-belongs-to-org-unit`) und beliebig viele **Rollen** (`bpmn-lane-performs-role`) zuzuordnen
2. Pools eine **Organisationseinheit** zuzuordnen (`bpmn-pool-represents-org-unit`)
3. UserTasks eine **Person** aus dem Repository zuzuordnen (`bpmn-task-assigned-to`)

Alle Zuordnungen werden als `ArchitectureEntity`-Connections im gemeinsamen ID-Raum gespeichert und sind über Katalog-Abfragen auswertbar.

## Begründung

Prozessmodelle ohne Verantwortlichkeiten sind für die Business Analyst-Persona (SH-08) nicht nutzbar — sie braucht auf einen Blick: „Wer macht was?" Die Speicherung als reguläre Connections (nicht als Metadaten) bedeutet, dass alle Abfrage-Mechanismen (Katalog, Join, Lineage) ohne Sonderbehandlung für Prozesse funktionieren. Beispiel: „Zeige alle Tasks, die Abteilung Einkauf zugeordnet sind" ist eine Katalog-Abfrage mit Join über `bpmn-lane-belongs-to-org-unit` und `bpmn-contained-in`.

## Kontext

OrganizationalUnits, Rollen und Personen müssen im Repository als Entitäten existieren, bevor sie zugeordnet werden können. Falls sie noch nicht existieren, bietet das UI einen Schnell-Anlegen-Dialog (ähnlich A1 in UC-10). Zuordnungen sind optional — ein gültiges Prozessdiagramm muss keine Zuordnungen haben.

## Typ-spezifische Felder

### Bei type=functional

**UI-Interaktionen im Canvas**:

| Element | Zuordnungs-Aktion | Speicherung |
|---|---|---|
| Lane | Rechtsklick → „Organisationseinheit zuordnen" | `bpmn-lane-belongs-to-org-unit` Connection |
| Lane | Rechtsklick → „Rolle zuordnen" (mehrere möglich) | `bpmn-lane-performs-role` Connection(s) |
| Pool | Rechtsklick → „Teilnehmer (OrgUnit) setzen" | `bpmn-pool-represents-org-unit` Connection |
| UserTask | Detail-Panel → Feld „Verantwortliche Person" | `bpmn-task-assigned-to` Connection |
| UserTask / Task | Detail-Panel → Feld „Erforderliche Rolle" | `bpmn-task-requires-role` Connection |

**Autocomplete**: Alle Zuordnungsfelder nutzen denselben `/api/v1/entities/search`-Endpunkt (REQ-070), gefiltert nach EntityType (`organizational-unit`, `role`, `person`).

**Visuelle Indikatoren auf Canvas**:
- Lane: Organisations-Badge (OrgUnit-Kürzel oder -Name) + Rollen-Chips unterhalb des Lane-Namens
- UserTask: Personen-Avatar (Initials) als kleines Icon in der rechten oberen Ecke des Task-Shapes

**Katalog-Auswertbarkeit**:
- Ein Katalog mit `primaryEntityType: bpmn-user-task` kann via Join über `bpmn-task-assigned-to` die verantwortliche Person als Spalte anzeigen
- Ein Katalog mit `primaryEntityType: bpmn-lane` kann OrgUnit und Rollen als Join-Spalten anzeigen

## Akzeptanzkriterien

**AC1** (OrgUnit → Lane):
- Wenn: Anna rechtsklickt auf eine Lane und wählt „Organisationseinheit zuordnen"; sucht „Einkauf"; wählt die OrgUnit
- Dann: `bpmn-lane-belongs-to-org-unit`-Connection gespeichert; Lane zeigt OrgUnit-Badge

**AC2** (Mehrere Rollen → Lane):
- Wenn: Anna einer Lane die Rollen „Genehmiger" und „Prüfer" zuordnet
- Dann: Zwei `bpmn-lane-performs-role`-Connections gespeichert; beide Rollen als Chips in der Lane sichtbar

**AC3** (OrgUnit → Pool):
- Wenn: Anna den Pool mit der OrgUnit „Unternehmensbereich Vertrieb" verknüpft
- Dann: `bpmn-pool-represents-org-unit`-Connection gespeichert; Pool-Header zeigt OrgUnit-Namen

**AC4** (Person → UserTask):
- Wenn: Anna im Detail-Panel eines UserTasks die Person „Maria Müller" sucht und zuordnet
- Dann: `bpmn-task-assigned-to`-Connection gespeichert; Task-Shape zeigt Initialen „MM" als Avatar-Icon

**AC5** (Zuordnung entfernen):
- Wenn: Anna eine bestehende Zuordnung (z.B. OrgUnit einer Lane) entfernt
- Dann: Connection wird aus Repository gelöscht; Badge/Chip verschwindet sofort aus dem Canvas

**AC6** (Katalog-Join OrgUnit):
- Gegeben: Mehrere Lanes haben OrgUnit-Zuordnungen
- Wenn: Katalog für `bpmn-lane` mit Join-Spalte `bpmn-lane-belongs-to-org-unit.name` geöffnet
- Dann: OrgUnit-Name als Spalte sichtbar; Filter auf OrgUnit möglich

**AC7** (Katalog-Join Person):
- Gegeben: Mehrere UserTasks haben Personen-Zuordnungen
- Wenn: Katalog für `bpmn-user-task` mit Join-Spalte `bpmn-task-assigned-to.name` geöffnet
- Dann: Personen-Name als Spalte; Tasks ohne Zuordnung zeigen leere Zelle

**AC8** (Schnell-Anlegen OrgUnit):
- Wenn: Anna im Zuordnungsdialog eine OrgUnit sucht, die nicht existiert, und „Neu anlegen" wählt
- Dann: Mini-Wizard öffnet sich (Name, Typ auswählen); nach Bestätigung ist die neue OrgUnit sofort zugewiesen

**AC9** (Web-Portal read-only):
- Wenn: Viewer-Nutzer das Prozessdiagramm im Web-Portal öffnet
- Dann: OrgUnit-Badges und Personen-Avatare sichtbar; keine Edit-Aktionen verfügbar; Tooltips mit vollständigem Namen

## Verifikationsmethode

- [x] Methode: test (Integrationstest + Playwright E2E)
- [x] Bestanden-Kriterium: AC1–AC5 via Playwright (Canvas-Interaktion + API-Verifikation); AC6–AC7 via API-Test (Katalog-Join-Query); AC8–AC9 manuell oder Playwright

## Abhängigkeiten

- **Voraussetzungen**: REQ-076 (built-in BPMN-Typen und Zuordnungs-Connections); REQ-070 (Entity-Mention-Autocomplete / Search-API); REQ-044–046 (Katalog-Join für Connections)
- **Konflikte**: keine bekannt

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
