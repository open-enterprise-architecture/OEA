# US-117: Veraltete TRM-Kategorien per Review-Datum-Filter finden

**ID**: US-117
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich, dass `lastReviewedAt` bei jedem Speichern automatisch aktualisiert wird und ich in der TRM-Übersicht nach veralteten Kategorien filtern kann, damit ich periodische TRM-Governance-Reviews effizient durchführen kann.

## Bezug

**Use Case**: [UC-19](../use-cases/UC-19-trm-konfigurieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-117](../req/REQ-117-trm-review-datum.md)

## Akzeptanzkriterien

**AC1** (automatisches lastReviewedAt):
- Wenn: ich eine TRM-Kategorie speichere
- Dann: ist `lastReviewedAt` auf das aktuelle UTC-Datum gesetzt

**AC2** (Filter nach Review-Datum):
- Wenn: ich den Filter „Zuletzt überprüft vor > 12 Monaten" in der TRM-Übersicht aktiviere
- Dann: werden Kategorien mit `lastReviewedAt` < heute − 12 Monate oder `lastReviewedAt = null` hervorgehoben

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
