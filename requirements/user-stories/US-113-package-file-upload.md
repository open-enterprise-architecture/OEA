# US-113: Continuum-Paket per Datei-Upload importieren

**ID**: US-113
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich eine `.json`- oder `.yaml`-Paketdatei hochladen und vor dem Import eine Vorschau mit Schema-Validierungsergebnis sehen, damit ich Fehler in der Datei korrigieren kann, bevor Bausteine importiert werden.

## Bezug

**Use Case**: [UC-18](../use-cases/UC-18-continuum-paket-importieren.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-113](../req/REQ-113-paket-datei-upload.md)

## Akzeptanzkriterien

**AC1** (Valide Datei – Vorschau):
- Wenn: ich eine valide Paket-Datei hochlade
- Dann: zeigt die UI eine Vorschau mit Anzahl und Art der enthaltenen Bausteine

**AC2** (Schema-Verletzung):
- Wenn: ich eine Datei mit fehlendem Pflichtfeld hochlade
- Dann: erhalte ich HTTP 422 mit einer Fehlerliste (Zeile, Feld, Fehlerbeschreibung); kein Baustein wird importiert

**AC3** (Import nach Bestätigung):
- Wenn: ich die Vorschau bestätige
- Dann: wird der Import ausgeführt; alle importierten Bausteine haben `scope=imported`

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
