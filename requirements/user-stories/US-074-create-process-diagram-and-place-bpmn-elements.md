---
id: US-074
title: Prozessdiagramm anlegen und BPMN-Elemente platzieren
use_case: UC-10
requirement: REQ-076
priority: must
status: proposed
story_points: 8
created: 2026-06-26
---

# US-074: Prozessdiagramm anlegen und BPMN-Elemente platzieren

**Als** Business Analyst (Anna, SH-08)  
**möchte ich** ein neues Diagramm mit dem Viewpoint „Prozess-Modell (BPMN 2.0)" anlegen und Pools, Lanes, Tasks, Events und Gateways auf der Canvas platzieren,  
**damit** ich Geschäftsprozesse direkt im EA-Repository modellieren kann ohne ein separates Tool zu benötigen.

## Akzeptanzkriterien

- [ ] Viewpoint „bpmn-process-view" steht bei Diagramm-Anlage zur Auswahl
- [ ] Canvas-Palette zeigt alle built-in BPMN-Elemente gruppiert: Pools/Lanes, Tasks, Events, Gateways
- [ ] Pool kann auf Canvas gezogen werden; Pool ist ein Container; Lanes können in den Pool eingefügt werden
- [ ] Elemente (Tasks, Events, Gateways) können per Drag aus Palette in eine Lane fallen gelassen werden; `bpmn-contained-in`-Connection wird automatisch angelegt
- [ ] SequenceFlow kann zwischen zwei BPMN-Elementen per Handle-Drag verbunden werden
- [ ] Verbindung von SequenceFlow zu einem Nicht-BPMN-Element wird mit Fehlermeldung abgelehnt (BR-01)
- [ ] Diagramm speichert alle Entities und Connections atomar; nach Reload ist Layout korrekt wiederhergestellt
- [ ] Unternehmensspezifische Subtypen (z.B. `approval-task`) erscheinen ebenfalls in der Palette (AC2 REQ-076)

## Technische Hinweise

- React Flow `parentId` für Pool/Lane-Containment; `bpmn-contained-in`-Connection als Repository-Abbild
- Palette-Gruppierung: „Container" (Pool, Lane, SubProcess), „Aufgaben", „Ereignisse", „Gateways"
- Lane-Resize: Lane muss vertikal grössenveränderbar sein; Pool passt sich automatisch an

## Nicht im Scope

- BPMN 2.0 XML-Export
- Automatisches Layout (ELK.js für BPMN — v2.0)
