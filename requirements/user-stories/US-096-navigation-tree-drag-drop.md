# US-096: Navigationsbaum per Drag & Drop reorganisieren

**ID**: US-096
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich Ordner und Items im Navigationsbaum per Drag & Drop verschieben und neu anordnen können, damit ich die Baumstruktur schnell und ergonomisch anpassen kann.

## Bezug

**Use Case**: [UC-13](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-096](../req/REQ-096-navigationsbaum-drag-drop.md)

## Akzeptanzkriterien

**AC1** (Zyklus-Schutz):
- Wenn: ich Ordner A unter einen Nachkommen von A ziehe
- Dann: wird das Drop-Target als ungültig markiert; ein API-Versuch antwortet mit HTTP 422

**AC2** (Neuanordnung von Items):
- Wenn: ich Items innerhalb eines Ordners neu anordne
- Dann: wird `sortOrder` korrekt aktualisiert und die Reihenfolge ist persistent

**AC3** (Item zwischen Ordnern verschieben):
- Wenn: ich ein Item von Ordner A nach Ordner B verschiebe
- Dann: ist das Item in B und nicht mehr in A

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
