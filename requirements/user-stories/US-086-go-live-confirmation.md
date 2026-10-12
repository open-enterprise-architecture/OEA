# US-086: Go-Live zweistufig bestätigen

**ID**: US-086
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich vor einem Go-Live-Übergang einen Bestätigungsdialog mit Diff-Zusammenfassung und Namenseingabe sehen, damit ich versehentliche und irreversible Übergänge sicher verhindern kann.

## Bezug

**Use Case**: [UC-11](../use-cases/UC-11-plateau-definieren-und-go-live.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-086](../req/REQ-086-go-live-zweistufige-bestaetigung.md)

## Akzeptanzkriterien

**AC1** (Button ohne Namenseingabe deaktiviert):
- Wenn: ich den Go-Live-Dialog ohne Namenseingabe öffne
- Dann: ist der "Go-Live bestätigen"-Button deaktiviert

**AC2** (Falscher Name):
- Wenn: ich einen falschen Plateau-Namen eingebe
- Dann: bleibt der Button deaktiviert

**AC3** (Korrekter Name):
- Wenn: ich den exakten Plateau-Namen eingebe
- Dann: wird der Button aktiviert und der Go-Live kann ausgeführt werden

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
