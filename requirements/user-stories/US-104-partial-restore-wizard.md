# US-104: Teilwiederherstellung per dreistufigem Wizard

**ID**: US-104
**Story Points**: 8
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich eine Entität selektiv über einen dreistufigen Wizard wiederherstellen können, damit ich nur bestimmte Felder zurücksetzen kann ohne korrekte Änderungen zu überschreiben.

## Bezug

**Use Case**: [UC-16](../use-cases/UC-16-teilwiederherstellung-entitaet.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-104](../req/REQ-104-teilwiederherstellung-wizard.md)

## Akzeptanzkriterien

**AC1** (Quellversion vorausgefüllt):
- Wenn: ich den Wizard aus der Zeitlinie für Version v3 starte
- Dann: ist die Quellversion v3 vorausgefüllt

**AC2** (Automatisches Überspringen):
- Wenn: Schritt 1 keine Unterschiede enthält
- Dann: wird er automatisch mit Hinweis übersprungen; Wizard wechselt zu Schritt 2

**AC3** (Bestätigen-Button ohne Auswahl deaktiviert):
- Wenn: in keinem Schritt Felder ausgewählt wurden
- Dann: ist der Bestätigen-Button deaktiviert

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
