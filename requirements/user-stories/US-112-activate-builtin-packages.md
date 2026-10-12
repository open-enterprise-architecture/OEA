# US-112: Eingebettete Continuum-Pakete per Klick aktivieren

**ID**: US-112
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich eingebettete Continuum-Pakete wie „TOGAF 10 TRM" mit einem Klick importieren, damit ich ohne Datei-Upload sofort eine vollständige Basisbibliothek erhalte.

## Bezug

**Use Case**: [UC-18](../use-cases/UC-18-continuum-paket-importieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-112](../req/REQ-112-builtin-pakete-aktivieren.md)

## Akzeptanzkriterien

**AC1** (Paket-Vorschau):
- Wenn: ich die Paketliste öffne
- Dann: sehe ich „TOGAF 10 TRM" mit Vorschau (47 ABBs, 38 TRM-Kategorien, 12 Patterns)

**AC2** (Import per Klick):
- Wenn: ich auf „Importieren" klicke
- Dann: wird der Import atomar ausgeführt; ein Import-Protokoll zeigt das Ergebnis

**AC3** (Bereits importiertes Paket):
- Wenn: das Paket bereits importiert wurde
- Dann: zeigt die UI ein Badge „Bereits importiert" und einen Hinweis auf Konflikt-Handling

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
