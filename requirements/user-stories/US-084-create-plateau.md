# US-084: Plateau anlegen

**ID**: US-084
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich Plateaus mit Status `baseline`, `target` oder `transition` anlegen und dabei Name, Beschreibung sowie eine Vorgänger-Referenz angeben, damit ich strategische Architekturzustände im EA-Repository strukturieren kann.

## Bezug

**Use Case**: [UC-11](../use-cases/UC-11-plateau-definieren-und-go-live.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-084](../req/REQ-084-plateau-anlegen.md)

## Akzeptanzkriterien

**AC1** (Baseline anlegen):
- Wenn: ich ein Plateau ohne `succeeds` mit `status=baseline` anlege
- Dann: antwortet die API mit HTTP 201 und `status=baseline`

**AC2** (Target anlegen):
- Wenn: ich ein Plateau mit `status=target` und `succeeds`=Baseline-ID anlege
- Dann: wird HTTP 201 zurückgegeben; `succeeds` ist korrekt gesetzt

**AC3** (Zweites Baseline abweisen):
- Wenn: ich ein weiteres Plateau mit `status=baseline` anlegen will
- Dann: erhalte ich HTTP 422 mit der Meldung "Es existiert bereits ein Baseline-Plateau"

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
