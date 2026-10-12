# US-085: Plateau-Übersicht nach Status

**ID**: US-085
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich eine strukturierte Übersicht aller Plateaus nach Status gruppiert sehen, damit ich auf einen Blick erkenne, welcher Zustand aktuell produktiv ist und welche Zielzustände geplant sind.

## Bezug

**Use Case**: [UC-11](../use-cases/UC-11-plateau-definieren-und-go-live.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-085](../req/REQ-085-plateau-uebersicht.md)

## Akzeptanzkriterien

**AC1** (Gruppierte Anzeige):
- Wenn: ich die Plateau-Übersicht aufrufe
- Dann: sehe ich das Baseline hervorgehoben, Targets mit `validFrom` und Realized-Plateaus in einer einklappbaren Sektion

**AC2** (Leere Target-Sektion):
- Wenn: keine Target-Plateaus existieren
- Dann: zeigt die Target-Sektion "Noch kein Ziel-Plateau geplant"

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
