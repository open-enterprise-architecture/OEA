---
id: REQ-081
title: BPMN-DataObject-Integration und Prozess-Datenlineage
type: functional
priority: should
status: proposed
version: 0.1.0
use_case: UC-10
created: 2026-06-26
author: requirements-engineer
---

# REQ-081: BPMN-DataObject-Integration und Prozess-Datenlineage

## Beschreibung

BPMN-Prozesse können DataObjects und DataStores enthalten. Tasks beschreiben über Input- und Output-Assoziationen, welche Daten sie lesen, erzeugen oder anreichern (`transformationType`). Da `bpmn-data-object` als Subtyp von `data-object` (entity.md) modelliert ist, entsteht eine nahtlose Verbindung zur technischen Data Lineage (UC-08): die gleiche Entity erscheint sowohl im Prozessdiagramm als auch in der Lineage-Abfrage.

## Akzeptanzkriterien

**AC1** (DataObject im Prozessdiagramm):
- Business Analyst kann `bpmn-data-object` und `bpmn-data-store` aus der BPMN-Palette auf das Prozessdiagramm ziehen
- DataObjects können innerhalb von Lanes oder frei auf Prozessebene platziert werden

**AC2** (Input-/Output-Assoziation anlegen):
- Business Analyst zieht eine gestrichelte Linie (BPMN-Notation) von einem DataObject zu einem Task (Input) oder vom Task zu einem DataObject (Output)
- UI fragt beim Anlegen: Richtung (Input / Output) und optional `transformationType` (create / enrich / overwrite / delete / read-only)

**AC3** (Anreicherungs-Annotation):
- Detail-Panel einer `bpmn-data-output-association` zeigt Felder: `transformationType` (Dropdown), `affectedAttributes` (Tag-Eingabe), `condition` (Freitext)
- Diese Properties werden im Repository gespeichert und sind via API abfragbar

**AC4** (Bestehendes data-object referenzieren):
- Business Analyst kann beim Anlegen eines `bpmn-data-object` ein bereits im EA-Repository vorhandenes `data-object` auswählen (Autocomplete via `[[`-Trigger, REQ-070)
- Das BPMN-Element zeigt dann Name und ID des referenzierten Objekts; kein Duplikat wird erstellt

**AC5** (DataStore mit technischer Infrastruktur verknüpfen):
- `bpmn-data-store` kann via `realizes`-Connection mit einem `data-component` aus dem EA-Repository verknüpft werden
- Verknüpfung: Kontextmenü „Realisiert durch" → Suche im EA-Repository

**AC6** (Lineage-Query durch Prozess):
- `GET /api/v1/entities/{id}/lineage?direction=downstream` für ein `data-object` gibt auch BPMN-Tasks zurück, die dieses Objekt über `bpmn-data-output-association` anreichern
- Analog upstream: Tasks, die es als Input lesen

**AC7** (Katalog-Join auf Prozess-Daten):
- Katalog mit `data-object` als primaryEntityType kann via Join auf `bpmn-data-output-association` zeigen, welche Prozess-Tasks jedes DataObject erzeugen oder anreichern

**AC8** (Web Portal read-only):
- BPMN-Diagramm mit DataObjects ist im Web Portal lesbar; DataObject-Names und transformationType-Annotationen sichtbar

## Technische Hinweise

- `bpmn-data-object` als EntityTypeDefinition mit `extends: data-object`, `isBuiltIn: true`; Property `isCollection: boolean` (BPMN-Standard)
- `bpmn-data-store` als eigener EntityTypeDefinition; keine Vererbung von `data-object` (semantisch verschieden)
- `bpmn-data-input-association` und `bpmn-data-output-association` als built-in Connections; Properties auf dem Connection-Objekt (ADR-004: alle Connections haben Integer-IDs)
- Canvas-Darstellung: gestrichelte Linie mit ausgefülltem/offenem Pfeil (BPMN-Notation); DataObject-Symbol: ungefaltetes Dokument-Icon; DataStore-Symbol: Zylinder
- Lineage-API (REQ-062): muss `bpmn-data-input-association` und `bpmn-data-output-association` als traversierbare Kanten kennen
- BR-07/BR-08 aus process.md gelten (Pfeilrichtung, create-Constraint)
