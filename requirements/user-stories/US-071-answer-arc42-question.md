# US-071: Arc42-Frage für ein System beantworten und bearbeiten

**ID**: US-071
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich im System-Detail-Tab „Arc42 Dokumentation" jede Kapitel-Frage einzeln beantworten und später bearbeiten – damit die Architekturdokumentation direkt im EA-Repository lebt und mit dem Modell verknüpft ist.

## Bezug

**Use Case**: [UC-09](../use-cases/UC-09-loesungsarchitektur-arc42-dokumentieren.md)
**Persona**: Michael – Solution Architekt (SH-04)
**Requirements**: [REQ-069](../req/REQ-069-arc42-dokumentation-bearbeiten.md)

## Akzeptanzkriterien

**AC1** (Tab + Fragen sichtbar):
- Wenn: Michael öffnet CRM-System-Entität
- Dann: Tab „Arc42 Dokumentation" sichtbar; alle Fragen der zugewiesenen Collection aufgelistet

**AC2** (Antwort anlegen):
- Wenn: Michael klickt „Jetzt beantworten", schreibt Text, speichert
- Dann: Antwort-Entität angelegt; Frage zeigt grünes Haken-Icon; Fortschrittsanzeige aktualisiert

**AC3** (Antwort bearbeiten):
- Wenn: Michael klickt „Bearbeiten" bei beantworteter Frage
- Dann: Editor öffnet sich mit bestehendem Content; Änderungen speicherbar

**AC4** (Fortschrittsanzeige):
- Wenn: 3 von 12 beantwortet
- Dann: Chip „3 / 12 beantwortet" und offene Fragen visuell markiert

**AC5** (Web Portal read-only):
- Wenn: CIO öffnet dieselbe Entität
- Dann: Inhalte lesbar; kein Bearbeitungs-Button

## Definition of Done

- [ ] AC1–AC5 erfüllt
- [ ] Tests: Tab, Antwort anlegen, bearbeiten, Fortschritt, read-only
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
