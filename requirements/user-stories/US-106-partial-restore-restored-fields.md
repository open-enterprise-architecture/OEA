# US-106: Wiederhergestellte Felder im Audit-Trail protokollieren

**ID**: US-106
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich, dass nach jeder Teilwiederherstellung die exakt wiederhergestellten Felder im Zeitlinie-Eintrag sichtbar sind, damit der Audit-Trail vollständig und nachvollziehbar bleibt.

## Bezug

**Use Case**: [UC-16](../use-cases/UC-16-teilwiederherstellung-entitaet.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-106](../req/REQ-106-teilwiederherstellung-restored-fields.md)

## Akzeptanzkriterien

**AC1** (restoredFields im Versionseintrag):
- Wenn: ich `description` und `properties.owner` teilweise wiederherstelle
- Dann: enthält der Versionseintrag `restoredFields=["description","properties.owner"]`

**AC2** (Leere Auswahl abweisen):
- Wenn: keine Felder ausgewählt sind und ich bestätigen will
- Dann: ist der Button deaktiviert; ein API-Aufruf mit leerem `restoredFields` wird mit HTTP 422 abgewiesen

**AC3** (Zeitlinie-Lesbarkeit):
- Wenn: ich die Zeitlinie nach der Teilwiederherstellung ansehe
- Dann: zeigt der Eintrag "Teilweise wiederhergestellt aus v4 (description, properties.owner)"

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
