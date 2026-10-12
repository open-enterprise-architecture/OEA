# US-121: Canvas-Raster mit Orientierungslinien

**ID**: US-121
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich auf dem Diagramm-Canvas ein Raster mit 4 dünnen und 1 dicken Orientierungslinie sehen, das Entities beim Platzieren einrasten lässt, damit meine Diagramme präzise ausgerichtet und visuell konsistent sind.

## Bezug

**Use Case**: [UC-05](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-121](../req/REQ-121-canvas-raster.md)

## Akzeptanzkriterien

**AC1** (Raster sichtbar):
- Wenn: Canvas geöffnet wird
- Dann: Raster mit Muster 4 dünne + 1 dicke Linie sichtbar; dicke Linie klar unterscheidbar

**AC2** (Einrasten):
- Wenn: Entity auf Canvas verschoben wird
- Dann: Position rastet auf nächsten Rasterpunkt ein; freie Positionierung nicht möglich

**AC3** (Ein-/Ausblenden):
- Wenn: `G` gedrückt oder Toolbar-Button geklickt
- Dann: Raster-Linien ausgeblendet; Einrasten bleibt aktiv

## Definition of Done

- [ ] AC1–AC3 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
