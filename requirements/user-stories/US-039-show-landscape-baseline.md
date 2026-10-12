# US-039: Aktuelle IT-Landschaft als Ausgangsbasis einer Solution anzeigen

**ID**: US-039
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich innerhalb einer Solution den aktuellen Stand der IT-Landschaft als durchsuchbare Liste sehen, damit ich weiss, welche Systeme und Komponenten bereits existieren, und meine geplanten Änderungen auf diesem Ist-Stand aufbauen kann.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-039: Aktuelle IT-Landschaft als Solution-Ausgangsbasis anzeigen](../req/REQ-039-landschaft-ausgangsbasis.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Zwei implemented-Solutions: A hat `CRM-Legacy` (new), B hat `ERP-Core` (new)
- Wenn: Michael eine neue Solution öffnet und die Ausgangsbasis lädt
- Dann: sind `CRM-Legacy` und `ERP-Core` in der Liste; keine anderen Entitäten

**AC2**:
- Gegeben: Solution A (implemented) hat `CRM-Legacy` (retiring)
- Wenn: Michael die Ausgangsbasis lädt
- Dann: ist `CRM-Legacy` **nicht** in der Liste (retiring aus implemented entfernt die Entität)

**AC3**:
- Gegeben: Solution A hat `ERP-Core` als modified (v4); Solution B (neueren Datums, implemented) hat `ERP-Core` als modified (v5)
- Wenn: Michael die Ausgangsbasis lädt
- Dann: zeigt die Liste `ERP-Core` mit dem Stand aus Solution B (neuere Implementation gewinnt)

**AC4** (leere Landschaft):
- Gegeben: noch keine implemented-Solutions vorhanden
- Wenn: Michael die Ausgangsbasis lädt
- Dann: erscheint der Hinweis „Keine realisierten Entitäten vorhanden – du kannst ausschliesslich neue Entitäten anlegen"; die `modified`- und `retiring`-Aktionen sind deaktiviert

**AC5** (Filter):
- Gegeben: Ausgangsbasis mit 20 Entitäten verschiedener Typen
- Wenn: Michael nach EntityType `ApplicationComponent` filtert
- Dann: werden nur Entitäten vom Typ `ApplicationComponent` angezeigt

## Technische Hinweise

- Betroffene Komponenten: Solution-Detailansicht (Bereich „Architektur-Vision & Scope"), Backend `GET /api/v1/solutions/{id}/landscape`
- Aggregationslogik (Projekt-Modus): über alle `entity_deltas` aus Solutions mit status=`implemented`; pro entityId neueste Implementation gewinnt (ORDER BY `implemented_at DESC`); `retiring`-Deltas als Tombstone (Entität wird aus Ergebnis entfernt)
- Datenbank-Änderungen: keine neuen Tabellen; Query auf bestehende `solutions` + `entity_deltas`
- Pagination: Cursor-based (Entitätslisten können gross werden)
- Für MVP: Plateau-Modus (Ausgangsbasis aus `fromPlateau`) kann initial mit Fallback auf Projekt-Modus-Berechnung gemockt werden, sofern Plateau-Modus noch nicht implementiert

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC5 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Aggregation aus mehreren Solutions (inkl. Überschneidungen, retiring als Tombstone, latest-wins bei modified); leere Landschaft; Filter
- [ ] Performance-Test: Aggregation mit 100+ implemented-Solutions < 500ms
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-038 (Solution muss existieren)
- Blockiert: US-040 (retiring/new brauchen Ausgangsbasis), US-041 (modified braucht Ausgangsbasis), US-042 (retiring braucht Ausgangsbasis), US-043 (Diff-Ansicht baut auf Ausgangsbasis auf)

## Notizen

5 SP wegen der Aggregationslogik im Backend (latest-wins, Tombstone-Behandlung, Pagination). Die UI ist einfach (filterbare Tabelle), aber die Query-Logik ist nicht trivial. Separate Unit-Tests für die Aggregationsfunktion empfohlen. Plateau-Modus (Ausgangsbasis aus fromPlateau) ist in REQ-039 spezifiziert, kann aber in einer Folge-Story realisiert werden.
