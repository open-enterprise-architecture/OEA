# US-044: Konflikt-Warnung bei parallelen EntityDeltas anzeigen

**ID**: US-044
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich eine Warnung sehen, wenn eine andere aktive Solution dieselbe Entität ebenfalls verändert, damit ich frühzeitig weiss, wo Abstimmungsbedarf mit anderen Architektinnen oder Architekten besteht, bevor der Konflikt beim Go-Live teuer wird.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-042: Konflikt-Warnung bei parallelen EntityDeltas](../req/REQ-042-konflikt-warnung-parallele-solutions.md)

## Akzeptanzkriterien

**AC1** (Konflikt erkannt):
- Gegeben: Solution A (draft, Owner: Maria) enthält ein Delta für `CRM-Legacy`
- Wenn: Michael in Solution B ein Delta für `CRM-Legacy` speichert
- Dann: erscheint eine Warnung „Diese Entität wird auch in 'Solution A' (Owner: Maria) geändert. Bitte koordinieren."

**AC2** (kein Konflikt mit implemented):
- Gegeben: Solution A (implemented) enthält ein Delta für `CRM-Legacy`
- Wenn: Michael in Solution B ein Delta für `CRM-Legacy` speichert
- Dann: **keine** Warnung (implemented ist kein aktiver Konflikt)

**AC3** (persistente Warnung):
- Gegeben: die Warnung für `CRM-Legacy` wurde beim Speichern des Deltas angezeigt
- Wenn: Michael die Solution B neu lädt
- Dann: ist die Warnung an dem betroffenen Delta weiterhin sichtbar (kein einmaliger Toast)

**AC4** (Warnung verschwindet bei Go-Live):
- Gegeben: Solution A hat ein konfliktendes Delta für `CRM-Legacy`; Solution B zeigt dafür eine Warnung
- Wenn: Solution A auf `implemented` gesetzt wird
- Dann: verschwindet die Warnung in Solution B beim nächsten Laden (on-the-fly, kein gespeichertes Flag)

**AC5** (mehrere Konflikte):
- Gegeben: Solutions A und B haben jeweils ein Delta für `CRM-Legacy`
- Wenn: Michael in Solution C ein Delta für `CRM-Legacy` anlegt
- Dann: erscheinen zwei Warnungen (eine pro konfliktbehafteter Solution)

## Technische Hinweise

- Betroffene Komponenten: Backend `POST /api/v1/solutions/{id}/deltas` (Konflikt-Check nach jedem Write), Solution-Detailansicht (Warnanzeige an Delta-Einträgen)
- Konflikt-Query: `SELECT s.id, s.name, p.display_name FROM entity_deltas ed JOIN solutions s ON ed.solution_id = s.id JOIN persons p ON s.owner_id = p.id WHERE ed.entity_id = :entityId AND s.id != :currentSolutionId AND s.status NOT IN ('implemented', 'archived')`
- Warnung on-the-fly berechnen (beim Laden der Delta-Liste), nicht als persistiertes Feld speichern – so ist sie immer aktuell
- Warnung ist nicht-blockierend: Delta wird in jedem Fall gespeichert, auch bei Konflikt
- Response bei POST: neben dem gespeicherten Delta optional ein `conflicts: [{ solutionId, solutionName, ownerName }]`-Array

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC5 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Konflikt mit draft/proposed/in-progress-Solution; kein Konflikt mit implemented/archived; mehrfacher Konflikt; Warnung persistiert; Warnung verschwindet nach Go-Live
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-040 (new-Delta-Endpunkt), US-041 (modified-Delta-Endpunkt), US-042 (retiring-Delta-Endpunkt) – alle teilen denselben Backend-Endpunkt, der den Konflikt-Check auslöst
- Blockiert: keine

## Notizen

3 SP: Die eigentliche Logik ist ein einfacher SQL-Join; der Aufwand liegt im UI (persistente Warnanzeige am Delta, nicht nur als Toast) und in den Tests (verschiedene Solution-Status-Kombinationen). Wichtig: Warnung on-the-fly berechnen, nicht als Flag speichern, damit sie automatisch verschwindet wenn der Konflikt sich auflöst (AC4).
