# US-123: Ankerpunkte auf Connections setzen und verschieben

**ID**: US-123
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich auf Connection-Linien Ankerpunkte setzen, die immer auf dem Raster einrasten, damit ich Verbindungen präzise um andere Elemente herumführen kann ohne die Ausrichtung zu verlieren.

## Bezug

**Use Case**: [UC-05](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-123](../req/REQ-123-connection-ankerpunkte.md)

## Akzeptanzkriterien

**AC1** (Ankerpunkt setzen):
- Wenn: Klick auf selektierte Connection-Linie
- Dann: Neuer Ankerpunkt auf nächstem Rasterpunkt; als Griff sichtbar

**AC2** (Einrasten immer aktiv):
- Wenn: Ankerpunkt per Drag & Drop verschoben wird
- Dann: Einrasten auf Raster; sub-Raster-Positionierung nicht möglich (gilt für alle Routing-Modi)

**AC3** (Ankerpunkt entfernen):
- Wenn: Doppelklick auf Ankerpunkt oder Kontextmenü „Entfernen"
- Dann: Ankerpunkt entfernt; Connection passt Verlauf an

**AC4** (Curved + Ankerpunkt):
- Wenn: Modus = Curved und Ankerpunkt gesetzt
- Dann: Bézierkurve verläuft durch Ankerpunkt; Kurven-Griffe beidseitig zur Kurven-Steuerung sichtbar

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
