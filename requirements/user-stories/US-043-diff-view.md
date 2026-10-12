# US-043: Diff-Ansicht einer Solution anzeigen

**ID**: US-043
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich eine Diff-Ansicht sehen, die mir zeigt, welche Entitäten sich durch diese Solution verändern und wie der Zielzustand im Vergleich zum aktuellen Stand aussieht, damit ich den Scope auf einen Blick überprüfen und sicherstellen kann, dass alle geplanten Änderungen korrekt erfasst sind.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-041: Diff-Ansicht aktueller Stand vs. Zielzustand einer Solution](../req/REQ-041-diff-ansicht.md)

## Akzeptanzkriterien

**AC1** (retiring):
- Gegeben: Solution hat ein `retiring`-Delta für `CRM-Legacy`
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint `CRM-Legacy` einmal; linke Seite zeigt sie im aktuellen Stand, rechte Seite zeigt sie rot/durchgestrichen als „ausser Betrieb"

**AC2** (new):
- Gegeben: Solution hat ein `new`-Delta für `Salesforce (ApplicationComponent)`
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint `Salesforce` einmal; linke Seite leer (existiert noch nicht), rechte Seite grün als neue Entität

**AC3** (modified):
- Gegeben: Solution hat ein `modified`-Delta für `ERP-Core` (version: v4 → v5)
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint `ERP-Core` einmal; links `version=v4`, rechts `version=v5`; nur das geänderte Property ist hervorgehoben

**AC4** (kein Dump der Gesamtlandschaft):
- Gegeben: Solution hat 3 Deltas; die Ausgangsbasis enthält 100 Entitäten
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: werden genau 3 Einträge angezeigt; alle anderen 97 Entitäten sind **nicht** sichtbar

**AC5** (leere Delta-Liste):
- Gegeben: Solution hat noch keine Deltas
- Wenn: Michael die Diff-Ansicht öffnet
- Dann: erscheint der Hinweis „Noch keine Änderungen erfasst"; kein Fehler

**AC6** (Live-Update):
- Gegeben: Michael hat die Diff-Ansicht geöffnet
- Wenn: er ein weiteres Delta speichert (in US-040–042)
- Dann: aktualisiert sich die Diff-Ansicht ohne vollständiges Seiten-Neuladen

## Technische Hinweise

- Betroffene Komponenten: Solution-Detailansicht (Tab oder Bereich „Diff"), Backend `GET /api/v1/solutions/{id}/diff`
- Berechnung on-the-fly: Ausgangsbasis (REQ-039-Daten) + EntityDeltas → Diff-Ergebnis; nicht persistiert
- Response-Struktur: `[{ entityId, entityName, entityType, deltaType, before: {props}, after: {props} }]`
- UI-Layout: Split-Panel oder Before/After-Karten; visuelle Differenzierung:
  - `new`: grüner Hintergrund / Plus-Icon; linke Spalte leer
  - `retiring`: roter Hintergrund / Durchstreichung; rechte Spalte ausgegraut
  - `modified`: geänderte Properties farblich hervorgehoben (gelb oder blau); unveränderte Properties gedimmt
- Live-Update: nach Delta-Speicherung (US-040–042) Diff-Endpunkt erneut aufrufen; kein WebSocket für MVP nötig

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC6 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Diff mit new/modified/retiring; leere Delta-Liste; Gesamt-Entitätsliste bleibt aus Diff ausgeblendet; Live-Update nach Delta-Save
- [ ] Snapshot-Test für visuelle Differenzierung (Farben/Symbole)
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-039 (Ausgangsbasis-Backend), US-040 (new-Deltas), US-041 (modified-Deltas), US-042 (retiring-Deltas)
- Blockiert: keine (Diff-Ansicht ist Endpunkt des UC-05-Workflows; Review-Workflow folgt in künftigem UC)

## Notizen

5 SP wegen der Split-Panel-UI und der Property-Level-Diff-Darstellung für `modified` (Vorher/Nachher pro Property, gedimmte unveränderte Properties). Das Backend-Diff ist O(n) in der Anzahl der Deltas und damit unproblematisch. Die UI ist der aufwendigere Teil dieser Story.
