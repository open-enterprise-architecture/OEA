---
id: REQ-121
title: Canvas-Raster mit Orientierungslinien
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - viewpoint
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-121: Canvas-Raster mit Orientierungslinien

## Aussage

Der Diagramm-Canvas MUSS ein sichtbares Raster darstellen, das aus **4 dünnen Hilfslinien** gefolgt von **1 dickeren Orientierungslinie** besteht (Wiederholungsperiode: 5 Rastereinheiten). Entities, die auf dem Canvas platziert oder in der Grösse verändert werden, MÜSSEN auf den Rasterpunkten einrasten. Das Raster MUSS über einen Toolbar-Button oder das Tastenkürzel `G` ein- und ausgeblendet werden können; das Einrasten bleibt auch bei ausgeblendetem Raster aktiv.

## Begründung

Ein Raster mit visueller Orientierung (dünn/dick-Rhythmus) erleichtert die präzise Ausrichtung von Entities erheblich. Ohne Einrasten entstehen unregelmässige Abstände, die Diagramme unlesbar machen und in automatisch generierten Exportformaten zu Artefakten führen.

## Akzeptanzkriterien

**AC1** (Raster-Darstellung):
- Wenn: Canvas geöffnet wird
- Dann: Raster sichtbar mit Muster 4 dünne + 1 dicke Linie; dickere Linie hat deutlich höheren visuellen Kontrast als dünne

**AC2** (Entity-Einrasten):
- Wenn: Eine Entity auf dem Canvas verschoben oder angelegt wird
- Dann: Position und Grösse rasten auf die nächste Rastereinheit ein; sub-Raster-Positionierung ist nicht möglich

**AC3** (Raster ein-/ausblenden):
- Wenn: Nutzer `G` drückt oder Toolbar-Button klickt
- Dann: Raster-Linien nicht mehr sichtbar; Einrasten bleibt weiterhin aktiv

## Abhängigkeiten

- **Voraussetzungen**: UC-05 Canvas vorhanden
- **Folgewirkungen**: REQ-122 (Orthogonal-Routing nutzt Rastereinheiten), REQ-123 (Ankerpunkte rasten auf Raster)

## Realisierungs-Hinweise

- Rastereinheit: 10 px bei 100 % Zoom; skaliert mit Zoom-Faktor
- Dickere Linie alle 5 Einheiten = 50 px-Raster als Orientierungsraster
- Vue Flow / React Flow: `snapToGrid={true}` mit `snapGrid={[10,10]}`; Grid-Hintergrund via CSS `background-size`

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
