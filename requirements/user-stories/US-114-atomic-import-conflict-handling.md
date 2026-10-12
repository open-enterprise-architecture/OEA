# US-114: Paket-Import atomar ausführen und Konflikte behandeln

**ID**: US-114
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich, dass der Paket-Import als atomare Transaktion ausgeführt wird und mir ID-Konflikte zur Auflösung anzeigt, damit weder ein partieller Import entsteht noch organisationsspezifische Bausteine unbeabsichtigt überschrieben werden.

## Bezug

**Use Case**: [UC-18](../use-cases/UC-18-continuum-paket-importieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-114](../req/REQ-114-atomarer-import-konflikt-handling.md)

## Akzeptanzkriterien

**AC1** (Zyklus-Abbruch):
- Wenn: das importierte Paket einen Zyklus im ABB-Verfeinerungs-Graphen enthält
- Dann: bricht der Import ab; kein Baustein wird importiert; ich sehe eine Fehlermeldung mit Zyklus-Beschreibung

**AC2** (Konflikt-Liste):
- Wenn: das Paket 3 Bausteine mit bereits vorhandenen IDs enthält
- Dann: sehe ich die Konflikt-Liste; der Default ist „Überspringen" für alle Konflikte

**AC3** (Rollback bei DB-Fehler):
- Wenn: nach dem Import von 20 von 50 Bausteinen ein Datenbankfehler auftritt
- Dann: wird ein vollständiger Rollback ausgeführt; 0 Bausteine sind importiert

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
