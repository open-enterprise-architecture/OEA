---
id: UC-10
title: Geschäftsprozesse nach BPMN 2.0 modellieren
version: 0.1.0
status: draft
priority: must
created: 2026-06-26
author: requirements-engineer
primary_actor: SH-08
references:
  secondary_actors:
    - SH-04
    - SH-07
  stakeholders:
    - SH-08
    - SH-04
    - SH-07
  concept:
    - concept/20-entities/09-prozess-architektur.md
    - concept/20-entities/06-kern-entitaetstypen.md
  business_objects:
    - process
    - entity
    - metamodel-configuration
    - viewpoint
  requirements:
    - REQ-076
    - REQ-077
---

# UC-10: Geschäftsprozesse nach BPMN 2.0 modellieren

## Diagramm

```plantuml
@startuml UC-10-BPMN
left to right direction
skinparam actorStyle awesome

actor "Anna\n(Business Analyst)" as Anna
actor "Michael\n(Solution Architekt)" as Michael
actor "Sabine\n(Business Engineer)" as Sabine

rectangle "OEA – Geschäftsprozesse modellieren (BPMN 2.0)" {
  usecase "Prozessdiagramm anlegen\n(BPMN-Viewpoint)" as UC_Create
  usecase "Pool & Lanes\nkonfigurieren" as UC_Pool
  usecase "Tasks platzieren\n& Personen zuordnen" as UC_Tasks
  usecase "SequenceFlow &\nGateways verbinden" as UC_Flow
  usecase "Task mit Applikation\nverknüpfen" as UC_App
  usecase "Prozess speichern\n(Repository)" as UC_Save
}

Anna --> UC_Create
Anna --> UC_Pool
Anna --> UC_Tasks
Anna --> UC_Flow
Michael --> UC_App
Sabine --> UC_Create

UC_Create ..> UC_Pool : <<include>>
UC_Pool ..> UC_Tasks : <<include>>
UC_Tasks ..> UC_Flow : <<include>>
UC_Tasks ..> UC_App : <<extend>>
UC_Create ..> UC_Save : <<include>>
@enduml
```

## Ziel

Die Business Analyst-Persona kann Geschäftsprozesse direkt im OEA-Tool in BPMN 2.0 modellieren. Sie ordnet dabei Lanes Rollen und Organisationseinheiten zu sowie Tasks konkreten Personen — ohne ein separates Prozess-Tool zu benötigen. Prozessmodelle sind Bestandteil des gemeinsamen EA-Repositorys und damit mit Applikationen, Fähigkeiten und Architektur-Domains verknüpfbar.

## Akteure

- **Primär**: SH-08 (Anna – Business Analyst)
- **Sekundär**: SH-04 (Michael – Solution Architekt; verknüpft Tasks mit Applikationen), SH-07 (Sabine – Business Engineer; konfiguriert BPMN-Typen im Metamodell)

## Vorbedingungen

1. OEA-Instanz läuft; Anna ist eingeloggt mit Rolle „Modeler" oder höher
2. BPMN-Process-View-Viewpoint ist im Metamodell konfiguriert (built-in oder von Admin angepasst)
3. OrganizationalUnit- und Rollen-Entitäten existieren im Repository (können auch leer sein)

## Nachbedingungen

- Prozessdiagramm ist im Repository persistent gespeichert
- Alle Prozess-Elemente (Pool, Lane, Task, Event, Gateway) sind als ArchitectureEntities mit korrekten EntityTypeIds angelegt
- Zuordnungs-Connections (Lane → OrgUnit, Lane → Rolle, Task → Person) sind im Repository verankert
- Prozessmodell ist im Web-Portal read-only einsehbar (SH-03/04)

## Hauptfluss

1. Anna öffnet ein bestehendes Diagramm oder legt ein neues Diagramm an und wählt den Viewpoint „Prozess-Modell (BPMN 2.0)"
2. Die Canvas-Palette zeigt BPMN-spezifische Elemente: Pool, Lane, Task-Varianten, Events, Gateways — entsprechend der Metamodell-Konfiguration
3. Anna zieht einen **Pool** auf die Canvas; der Pool-Wizard öffnet sich: Name eingeben, Teilnehmer-OrgUnit zuordnen (`bpmn-pool-represents-org-unit`)
4. Anna fügt eine oder mehrere **Lanes** in den Pool ein; pro Lane kann sie:
   - Eine **Organisationseinheit** zuordnen (`bpmn-lane-belongs-to-org-unit`)
   - Eine oder mehrere **Rollen** zuordnen (`bpmn-lane-performs-role`)
5. Anna platziert **Tasks** (UserTask, ServiceTask, BusinessRuleTask etc.) innerhalb der Lanes; Elemente werden automatisch via `bpmn-contained-in` mit der Lane verknüpft
6. Für UserTasks öffnet Anna das Detail-Panel und weist eine **Person** aus dem Repository zu (`bpmn-task-assigned-to`)
7. Anna verbindet Elemente mit **SequenceFlow**-Connections; Gateways (Exclusive, Parallel, Inclusive) werden über die Palette eingefügt
8. Anna wählt optional „Verbindung zu Applikation": eine Task-zu-Applikation-Connection verbindet den Prozessschritt mit dem unterstützenden System im EA-Repository
9. Anna speichert das Diagramm; alle Entities und Connections werden atomar persistiert

## Alternative Flüsse

**A1 — OrganizationalUnit existiert nicht**:
- Schritt 3/4: Zuordnungs-Dropdown zeigt leere Liste
- Anna wählt „Neue OrgUnit anlegen": Mini-Wizard (Name, Typ) legt `organizational-unit`-Entity direkt im Zuordnungsdialog an
- Fluss weiter wie im Hauptfluss

**A2 — Person existiert nicht im Repository**:
- Schritt 6: Person nicht gefunden im Autocomplete
- Anna kann Feld leer lassen (Zuordnung optional) und später nachtragen

**A3 — Viewpoint nur mit Standard-BPMN-Typen**:
- Schritte 1–9 laufen ohne unternehmensspezifische Subtypen; alle Tasks erscheinen als `bpmn-task` oder Standard-Subtyp

## Akzeptanzkriterien

**AC1** (Pool/Lane-Struktur):
- Gegeben: Anna hat Viewpoint „Prozess-Modell (BPMN 2.0)" geöffnet
- Wenn: Sie einen Pool anlegt und Lanes einfügt
- Dann: Pool erscheint als Container auf Canvas; Lanes als Swim-Lanes innerhalb des Pools; Tasks können in Lanes platziert werden

**AC2** (OrgUnit-Zuordnung):
- Wenn: Anna eine Lane mit einer OrgUnit verbindet
- Dann: `bpmn-lane-belongs-to-org-unit`-Connection im Repository gespeichert; Lane zeigt OrgUnit-Namen als Label oder Badge

**AC3** (Rollen-Zuordnung):
- Wenn: Anna eine Lane mit einer Rolle verbindet
- Dann: `bpmn-lane-performs-role`-Connection gespeichert; mehrere Rollen pro Lane möglich

**AC4** (Personen-Zuordnung):
- Wenn: Anna einem UserTask eine Person zuordnet
- Dann: `bpmn-task-assigned-to`-Connection gespeichert; Task zeigt Person-Avatar oder Kürzel als Indikator

**AC5** (Prozesselemente im Katalog):
- Gegeben: Prozessdiagramm ist gespeichert
- Wenn: Anna einen Katalog für `bpmn-user-task` öffnet
- Dann: Alle UserTask-Entities aus allen Diagrammen erscheinen; Join-Spalte zeigt zugeordnete Person und Lane

**AC6** (Metamodell-Konfigurierbarkeit):
- Gegeben: Admin hat einen Subtyp `approval-task extends bpmn-user-task` konfiguriert
- Wenn: Anna das Prozessdiagramm öffnet
- Dann: `approval-task` erscheint in der Palette; kann wie jeder andere Task platziert und zugeordnet werden

**AC7** (Web-Portal Lesbarkeit):
- Wenn: Nutzer mit Rolle „Viewer" (Web-Portal) das Prozessdiagramm öffnet
- Dann: BPMN-Canvas read-only sichtbar; OrgUnit- und Personen-Zuordnungen als Tooltips

## Nicht im Scope

- BPMN 2.0 XML-Export/Import (separat, Prio: medium — SH-08-Concern)
- Simulation / Prozess-Ausführung
- DMN-Integration (Decision-Tabellen)
- Collaboration-Diagramme mit Message-Flows zwischen verschiedenen Pool-Teilnehmern (v2.0)

## Konzept-Bezug

- §9 Prozess-Architektur: BPMN als Adapter-Modul, Prozess als ArchitectureEntity
- §6 Kern-Entitätstypen: OrganizationalUnit als konfigurierbarer EntityType

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
