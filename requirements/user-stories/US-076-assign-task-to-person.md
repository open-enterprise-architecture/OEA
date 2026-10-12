---
id: US-076
title: Aufgabe (UserTask) einer Person zuordnen
use_case: UC-10
requirement: REQ-077
priority: must
status: proposed
story_points: 3
created: 2026-06-26
---

# US-076: Aufgabe (UserTask) einer Person zuordnen

**Als** Business Analyst (Anna, SH-08)  
**möchte ich** einem UserTask direkt im Prozessdiagramm eine konkrete Person aus dem EA-Repository zuordnen,  
**damit** für jede manuelle Aufgabe im Prozess ein klarer Owner benannt ist.

## Akzeptanzkriterien

- [ ] Detail-Panel eines `bpmn-user-task` zeigt das Feld „Verantwortliche Person" mit Autocomplete gegen `person`-Entitäten
- [ ] Auswahl einer Person legt `bpmn-task-assigned-to`-Connection an
- [ ] Task-Shape auf Canvas zeigt Initialen (z.B. „MM") als Avatar-Icon in der rechten oberen Ecke
- [ ] Feld „Erforderliche Rolle" im Detail-Panel ermöglicht Zuordnung über `bpmn-task-requires-role` (auch für `bpmn-task`-Basistyp)
- [ ] Zuordnung entfernen: „×" im Detail-Panel löscht Connection; Avatar-Icon verschwindet
- [ ] Katalog für `bpmn-user-task` kann Join-Spalte „Person" (`bpmn-task-assigned-to.name`) anzeigen (AC7 REQ-077)
- [ ] Web-Portal: Personen-Avatar sichtbar im read-only-Canvas; Tooltip zeigt vollständigen Namen

## Technische Hinweise

- Connection: `bpmn-task-assigned-to` (isBuiltIn=true; source=bpmn-user-task; target=person)
- BR-05: UI warnt wenn mehr als 1 Person zugeordnet wird (SHOULD-Regel; kein hartes Reject)
- Person-Entitäten: built-in EntityType `person` (aus role.md, geplant; Fallback: manuell angelegte person-Entity)
