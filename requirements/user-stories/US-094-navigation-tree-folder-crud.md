# US-094: Navigationsbaum Ordner anlegen, umbenennen und löschen

**ID**: US-094
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich Ordner im Navigationsbaum anlegen, umbenennen und löschen können, damit ich das EA-Repository sinnvoll strukturieren und navigieren kann.

## Bezug

**Use Case**: [UC-13](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-094](../req/REQ-094-navigationsbaum-ordner-crud.md)

## Akzeptanzkriterien

**AC1** (Ordner anlegen):
- Wenn: ich einen neuen Ordner unter einem Parent anlege
- Dann: erhalte ich HTTP 201 und der Ordner ist im Baum sichtbar

**AC2** (Doppelter Name abweisen):
- Wenn: ich einen Ordner mit einem bereits vorhandenen Namen unter demselben Parent anlege
- Dann: erhalte ich HTTP 422 "Name bereits vorhanden"

**AC3** (Rekursives Löschen):
- Wenn: ich einen Ordner mit Kind-Ordnern und Items lösche
- Dann: werden alle Kind-Ordner und Items entfernt; referenzierte Entitäten bleiben unberührt

**AC4** (Wurzelknoten-Schutz):
- Wenn: ich den Wurzelknoten löschen will
- Dann: erhalte ich HTTP 422 "Wurzelknoten kann nicht gelöscht werden"

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
