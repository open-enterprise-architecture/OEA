---
id: REQ-066
title: Konfigurierbare Entity-Anlage-Workflows (Wizard)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-05
    - UC-06
    - UC-08
  business_objects:
    - metamodel-configuration
    - entity
  business_rules:
    - metamodel-configuration:BR-19
    - metamodel-configuration:BR-20
    - metamodel-configuration:BR-21
    - metamodel-configuration:BR-22
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/20-entities/14-erweiterbarkeit.md
    - concept/20-entities/15-schema-evolution.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-066: Konfigurierbare Entity-Anlage-Workflows (Wizard)

## Aussage

Das System MUSS für jeden `EntityTypeDefinition` mit mindestens einem konfigurierten `creationStep` beim Anlegen einer neuen Entität — egal ob im Katalog-Kontext oder im Diagramm-Kontext — automatisch einen mehrseitigen Wizard öffnen. Jeder `CreationStep` entspricht genau einer Pop-over-Seite. Navigation: „Weiter" für alle Schritte ausser dem letzten, „Fertig" auf dem letzten Schritt. Nach „Fertig" wird die Entität atomisch im Repository persistiert und steht sofort im auslösenden Kontext zur Verfügung.

## Begründung

OEA soll den grösstmöglichen Freiheitsgrad im Metamodell erlauben: jede Instanz definiert ihre eigenen EntityTypes, Properties und Regeln. Reine save-time-Validierungen (ConstraintRule, MandatoryConnectionConstraint) blockieren nachträglich — sie bieten aber keine proaktive Führung durch Pflichtfelder und Pflichtverknüpfungen. Der Wizard löst dieses Problem, indem er Nutzer Schritt für Schritt durch alle konfigurierten Anforderungen führt, bevor die Entität überhaupt angelegt wird. Das Ergebnis ist ein vollständiges, regelkonformes Objekt — ohne Nacharbeit.

## Kontext

Die `creationSteps`-Konfiguration sitzt auf `EntityTypeDefinition` in der MetamodelConfiguration (BO: metamodel-configuration.md, Version 0.8.0). Drei Step-Typen:
- `properties`: zeigt alle Properties einer `propertyCategory`
- `domainAssignment`: zeigt Architekturdomänen-Auswahl (Multi-Select)
- `connectionAssignment`: zeigt Zielentitäts-Suche + Auswahl; erstellt Connection nach Fertigstellung

Der Wizard gilt für **alle** Anlage-Einstiegspunkte:
- „+ Entität" im Katalog (Katalog-Kontext: nach Fertig erscheint die Entität als neue Zeile)
- „Element platzieren" im Diagramm (Diagramm-Kontext: nach Fertig erscheint das Shape auf dem Canvas)
- API (`POST /api/v1/entities` mit Wizard-Payload)

## Typ-spezifische Felder

### Konfiguration (Metamodell-Ebene, UC-04)

Ein Metamodell-Konfigurateur legt die Steps in der MetamodelConfiguration fest:

```yaml
entityTypeDefinitions:
  - id: application-component
    name: ApplicationComponent
    creationSteps:
      - stepId: basic-info
        title: "Basisdaten"
        description: "Name und grundlegende Informationen"
        stepType: properties
        propertyCategory: "Allgemein"

      - stepId: domain-assignment
        title: "Domänen-Zuordnung"
        description: "Welcher Architekturdomäne gehört diese Applikation an?"
        stepType: domainAssignment

      - stepId: capability-link
        title: "Capability-Verknüpfung"
        description: "Welche Capability realisiert diese Applikation?"
        stepType: connectionAssignment
        connectionType: "realizes-capability"
        targetEntityType: "capability"
        minConnections: 1
```

Mit dieser Konfiguration öffnet sich beim Anlegen einer ApplicationComponent ein 3-Seiten-Wizard: Schritt 1 zeigt Properties der Kategorie „Allgemein"; Schritt 2 zeigt Domänen-Auswahl; Schritt 3 zeigt Capability-Suche. Navigation: „Weiter" → „Weiter" → „Fertig".

### Wizard-API (POST /api/v1/entities, erweitertes Payload)

```json
{
  "entityTypeId": "application-component",
  "name": "CRM-System",
  "properties": [
    { "propertyName": "owner", "value": "IT-Operations" }
  ],
  "architectureDomainIds": ["finance", "sales"],
  "connections": [
    {
      "connectionTypeId": "realizes-capability",
      "targetEntityId": 17
    }
  ]
}
```

Das Backend erstellt die Entität und alle angegebenen Connections in einer Transaktion. Schlägt die Transaktion fehl, werden weder Entität noch Connections persistiert.

### Validierung

- Mandatory-Felder des `properties`-Schritts: sofortige Inline-Validierung beim Verlassen des Feldes; „Weiter"-Button bleibt aktivierbar (damit der Nutzer vorwärts navigieren und zurückkehren kann), aber „Fertig" blockiert wenn mandatory Properties leer sind
- `connectionAssignment` mit `minConnections=1`: „Fertig" blockiert, wenn keine Connection ausgewählt
- `domainAssignment`: immer optional (minCount=0); keine Blockade

## Akzeptanzkriterien

**AC1** (Wizard öffnet sich automatisch – Katalog-Kontext):
- Gegeben: ApplicationComponent hat 3 `creationSteps`
- Wenn: Lukas klickt „+ Neue Entität" im ApplicationComponent-Catalog
- Dann: Wizard öffnet sich auf Seite 1 „Basisdaten"; kein klassisches Anlage-Modal

**AC2** (Wizard öffnet sich automatisch – Diagramm-Kontext):
- Wenn: Lukas platziert ein ApplicationComponent-Element im Diagramm (Drag & Drop aus Palette)
- Dann: Wizard öffnet sich sofort; Entität ist erst nach „Fertig" auf dem Canvas sichtbar

**AC3** (Navigation Weiter / Fertig):
- Gegeben: 3 Schritte
- Wenn: Lukas navigiert: Weiter → Weiter → Fertig
- Dann: Schritt 1 → Schritt 2 → Schritt 3 → Fertig; Entität und alle Connections atomisch persistiert

**AC4** (Zurück-Navigation):
- Wenn: Lukas ist auf Schritt 2 und klickt „Zurück"
- Dann: Schritt 1 wird wieder angezeigt; eingegebene Daten aus Schritt 1 bleiben erhalten

**AC5** (Mandatory Property blockiert Fertig):
- Gegeben: Schritt 1 hat Property `owner` (validationMode=mandatory); Lukas lässt es leer
- Wenn: Lukas klickt „Fertig" (oder versucht nach Schritt 3 zu gelangen)
- Dann: Fehlermeldung auf dem entsprechenden Schritt; keine Persistierung

**AC6** (connectionAssignment minConnections=1):
- Gegeben: Schritt 3 „Capability-Verknüpfung" mit minConnections=1
- Wenn: Lukas klickt „Fertig" ohne eine Capability ausgewählt zu haben
- Dann: Schritt 3 zeigt Fehler „Mind. 1 Capability muss verknüpft werden"; keine Persistierung

**AC7** (Abbruch = keine Entität):
- Wenn: Lukas schliesst den Wizard (ESC oder X) auf Schritt 2
- Dann: Keine Entität im Repository; keine teilangelegte Verbindung

**AC8** (Kein Wizard ohne creationSteps):
- Gegeben: EntityType `stereotype` ohne `creationSteps` (leere Liste)
- Wenn: Nutzer legt neue Instanz an
- Dann: Klassisches Anlage-Modal (kein Wizard); kein Regressionsfehler

**AC9** (Atomische Persistierung):
- Gegeben: Wizard mit Entität + 2 Connections
- Wenn: Die zweite Connection-Erstellung schlägt serverseitig fehl (z.B. Zielentität gelöscht)
- Dann: Weder Entität noch erste Connection sind persistent; HTTP 422 mit Fehlerbeschreibung

## Abhängigkeiten

- **Voraussetzungen**: metamodel-configuration.md v0.8.0 (CreationStep); REQ-036 (EntityType-Konfiguration); REQ-033 (Import — Workflow-Konfiguration muss importierbar sein)
- **Folgewirkungen**: Löst Kernproblem der proaktiven Datenvollständigkeit; ersetzt reaktive ConstraintRule-Blockaden für Pflicht-Properties und Pflicht-Connections
- **Optionaler Konflikt**: ConstraintRule severity=error und MandatoryConnectionConstraint bleiben aktiv als save-time-Fallback; Wizard und Constraints sind komplementär

## Realisierungs-Hinweise

- Frontend: Multi-Step-Panel-Komponente (Modal/Drawer); Schritt-Indikator (Breadcrumb oder Stepper oben)
- Zustand: Wizard-State lokal im Frontend (kein Draft-Persistenz); Payload wird erst bei Fertig gesendet
- Context-Handling: Nach Fertig muss das auslösende Kontextelement (Katalog-Tabelle oder Diagramm-Canvas) die neue Entität ohne vollen Reload darstellen
- Client App (Electron): voll editierbar; Web Portal: Wizard nur für Typen mit `isReadOnly=false`

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
