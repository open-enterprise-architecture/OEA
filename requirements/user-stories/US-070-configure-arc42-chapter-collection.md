# US-070: Arc42-Kapitelsammlung im Metamodell konfigurieren

**ID**: US-070
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Metamodell-Administrator möchte ich eine Arc42-Kapitelsammlung anlegen, Fragen definieren und dem EntityType „ApplicationComponent" zuweisen – damit Solution Architekten beim Öffnen eines Systems sofort die richtigen Dokumentationsfragen sehen.

## Bezug

**Use Case**: [UC-04](../use-cases/UC-04-metamodell-konfigurieren.md), [UC-09](../use-cases/UC-09-loesungsarchitektur-arc42-dokumentieren.md)
**Persona**: Sabine – Business Engineer (SH-07); Kurt – Lead EA (SH-03)
**Requirements**: [REQ-067](../req/REQ-067-arc42-kapitelsammlung.md)

## Akzeptanzkriterien

**AC1** (Collection anlegen):
- Wenn: Admin legt Collection „Arc42 Standard" mit 12 Fragen an und weist sie `application-component` zu
- Dann: Collection gespeichert; ApplicationComponent-Entitäten zeigen Arc42-Tab

**AC2** (Built-in importieren):
- Wenn: Admin klickt „Standard Arc42 importieren"
- Dann: 12-Fragen-Collection als editierbare Kopie verfügbar

**AC3** (Frage-Reihenfolge ändern):
- Wenn: Admin ändert sortOrder der Fragen
- Dann: Neue Reihenfolge im Tab korrekt angezeigt

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests: Anlegen, Import, Reihenfolge
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
