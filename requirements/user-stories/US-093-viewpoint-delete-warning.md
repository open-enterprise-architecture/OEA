# US-093: Viewpoint-Löschung mit Diagramm-Warnung

**ID**: US-093
**Story Points**: 2
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich beim Löschen eines Viewpoints eine Warnung sehen, wie viele Diagramme betroffen sind, damit ich nicht unbemerkt verwaiste Diagramm-Referenzen erzeuge.

## Bezug

**Use Case**: [UC-12](../use-cases/UC-12-viewpoint-verwalten.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-093](../req/REQ-093-viewpoint-loeschen-warnung.md)

## Akzeptanzkriterien

**AC1** (Warnung bei genutztem Viewpoint):
- Wenn: ich einen Viewpoint lösche, der von 5 Diagrammen genutzt wird
- Dann: sehe ich die Warnung "5 Diagramme werden betroffen"; nach Bestätigung wird gelöscht

**AC2** (Keine Warnung bei ungenutztem Viewpoint):
- Wenn: ich einen Viewpoint lösche, der von keinem Diagramm genutzt wird
- Dann: erscheint ein direkter Bestätigungsdialog ohne Warnungstext

## Definition of Done

- [ ] AC1–AC2 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
