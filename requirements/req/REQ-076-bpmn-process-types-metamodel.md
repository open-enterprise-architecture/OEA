---
id: REQ-076
title: BPMN-Prozesselemente und OrganizationalUnit im Metamodell
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
    - UC-04
  business_objects:
    - process
    - entity
    - metamodel-configuration
  business_rules:
    - BR-01
    - BR-02
    - BR-03
    - BR-04
    - BR-05
    - BR-06
  stakeholders:
    - SH-08
    - SH-07
  concept:
    - concept/20-entities/09-prozess-architektur.md
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-076: BPMN-Prozesselemente und OrganizationalUnit im Metamodell

## Aussage

Das System MUSS einen vollständigen Satz built-in `EntityTypeDefinitions` für BPMN 2.0-Prozessmodellierung bereitstellen (Pool, Lane, Task-Varianten, Events, Gateways, SequenceFlow, MessageFlow). Zusätzlich MUSS `organizational-unit` als built-in EntityType verfügbar sein. Alle built-in BPMN-Typen und `organizational-unit` MÜSSEN via `extends` zu unternehmensspezifischen Subtypen erweiterbar sein, ohne die Basis-Typen zu verändern.

## Begründung

Die Business Analyst-Persona (SH-08) benötigt sofort einsatzbereite BPMN-Typen — ohne dass der Administrator zuerst alle Typen manuell konfigurieren muss. Gleichzeitig müssen Unternehmen ihre eigenen Prozesstypen (z.B. „Review-Task", „SAP-Service-Task") definieren können. Die `organizational-unit` ist ein neues Konzept im EA-Repository, das Rollen, Personen und Prozess-Lanes miteinander verknüpft.

## Kontext

Alle Prozess-Elemente sind reguläre `ArchitectureEntities` im gemeinsamen Integer-ID-Raum (entity.md). Der React-Flow-Canvas nutzt Nested-Nodes für Pool/Lane-Hierarchie: ein Pool ist ein `GroupNode`; Lanes sind Subgruppen; Tasks/Events/Gateways sind reguläre Nodes mit `parentId`. Die Pool/Lane-Hierarchie wird im Repository über `bpmn-contained-in`- und `bpmn-lane-in-pool`-Connections gespeichert; der Canvas liest diese Connections beim Laden aus und baut das verschachtelte Layout auf.

## Typ-spezifische Felder

### Bei type=functional

**Umfang der built-in EntityTypes** (alle `isBuiltIn: true`; alle per `extends` erweiterbar):

**Prozess-Container**:
- `bpmn-process` (bpmn:Process)
- `bpmn-pool` (bpmn:Pool)
- `bpmn-lane` (bpmn:Lane)

**Tasks** (alle `isConnection: false`):
- `bpmn-task`, `bpmn-user-task`, `bpmn-service-task`, `bpmn-send-task`, `bpmn-receive-task`, `bpmn-business-rule-task`, `bpmn-script-task`, `bpmn-sub-process`

**Events**:
- `bpmn-start-event`, `bpmn-end-event`, `bpmn-intermediate-catch-event`, `bpmn-intermediate-throw-event`

**Gateways**:
- `bpmn-exclusive-gateway`, `bpmn-parallel-gateway`, `bpmn-inclusive-gateway`, `bpmn-event-gateway`

**Connections** (isConnection=true, isBuiltIn=true):
- `bpmn-sequence-flow` (SequenceFlow zwischen Prozess-Elementen)
- `bpmn-message-flow` (MessageFlow zwischen Pools)
- `bpmn-contained-in` (Element → Lane/Pool; für Nested-Node-Layout)
- `bpmn-lane-in-pool` (Lane → Pool)

**OrganizationalUnit** (isConnection=false, isBuiltIn=true):
- `organizational-unit` (Basistyp; erweiterbar zu `department`, `team`, etc.)
- `org-unit-part-of` (Connection, isConnection=true; OrgUnit → übergeordnete OrgUnit; Hierarchie)

**Zuordnungs-Connections** (isConnection=true, isBuiltIn=true):
- `bpmn-lane-performs-role` (bpmn-lane → role)
- `bpmn-lane-belongs-to-org-unit` (bpmn-lane → organizational-unit)
- `bpmn-pool-represents-org-unit` (bpmn-pool → organizational-unit)
- `bpmn-task-assigned-to` (bpmn-user-task → person)
- `bpmn-task-requires-role` (bpmn-task → role)

**Built-in Viewpoint** (ergänzend zu UC-06/REQ-037):
- `bpmn-process-view`: Viewpoint mit Notation `bpmn2`; allowedEntityTypes: alle BPMN-Typen; Pool/Lane-Layout-Modus aktiviert

## Akzeptanzkriterien

**AC1** (Built-in BPMN-Typen vorhanden):
- Wenn: OEA-Instanz gestartet; Admin öffnet MetamodelConfiguration
- Dann: Alle 19 built-in BPMN-EntityTypes und 9 built-in Connections sind sichtbar (isBuiltIn=true); unveränderlich in ihren Basiseigenschaften

**AC2** (Erweiterbarkeit):
- Wenn: Admin legt `EntityTypeDefinition { id: "approval-task", extends: "bpmn-user-task" }` an
- Dann: `approval-task` erbt alle Attribute von `bpmn-user-task`; erscheint in BPMN-Palette; kann in Prozessdiagrammen genutzt werden

**AC3** (OrganizationalUnit Typ):
- Wenn: Admin öffnet MetamodelConfiguration
- Dann: `organizational-unit` als built-in EntityType sichtbar; per `extends` zu `department`, `team`, `business-unit` etc. erweiterbar

**AC4** (Pool/Lane Nested-Layout):
- Gegeben: Prozessdiagramm mit Pool → Lane → Task-Hierarchie via `bpmn-contained-in`-Connections
- Wenn: Canvas lädt das Diagramm
- Dann: Pool erscheint als äusserer Container; Lanes als Swim-Lanes; Tasks innerhalb der korrekten Lanes; Layout korrekt verschachtelt

**AC5** (SequenceFlow Constraint):
- Wenn: Nutzer versucht, einen `bpmn-sequence-flow` von einem BPMN-Task zu einer Nicht-BPMN-Entität (z.B. ApplicationComponent) zu ziehen
- Dann: API gibt HTTP 422 zurück (BR-01); UI zeigt Fehlermeldung

**AC6** (BPMN-Viewpoint):
- Wenn: Nutzer legt neues Diagramm an und wählt den built-in Viewpoint `bpmn-process-view`
- Dann: Palette zeigt ausschliesslich BPMN-konforme Elemente; Pool/Lane-Modus ist aktiv (Container-Drop möglich)

## Verifikationsmethode

- [x] Methode: test (automatisiert, API- und Integrationstests)
- [x] Bestanden-Kriterium: `GET /api/v1/metamodel/entity-types?builtIn=true` gibt alle 19+7 BPMN-Typen zurück; AC2 per Integrationstest; AC4 per Playwright-Snapshot-Test

## Abhängigkeiten

- **Voraussetzungen**: metamodel-configuration.md (EntityTypeDefinition mit `extends`); entity.md (Integer-ID-Raum); ADR-007 (React Flow mit Nested-Node-Support)
- **Folgewirkungen**: REQ-077 (Zuordnungs-Connections im UI), REQ-040 (EntityDeltas → Canvas), REQ-066 (Anlage-Wizard für BPMN-Elemente)

## Realisierung

- ADR(s): ADR-007 (React Flow — Nested Nodes via `parentId`)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
