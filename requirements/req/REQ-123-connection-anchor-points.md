---
id: REQ-123
title: Ankerpunkte auf Connections
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
    - entity
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-123: Ankerpunkte auf Connections

## Aussage

Das System MUSS das Setzen, Verschieben und Entfernen von **Ankerpunkten** (Waypoints) auf Connection-Linien ermöglichen. Ankerpunkte steuern den Verlauf der Connection zwischen Quell- und Ziel-Entität. Ankerpunkte MÜSSEN **immer auf dem Raster einrasten** (unabhängig vom Routing-Modus der Connection). Ankerpunkte werden durch Klick auf die Connection-Linie gesetzt, durch Drag & Drop verschoben und durch Doppelklick oder Kontextmenü entfernt.

## Begründung

Ohne Ankerpunkte können Connections nicht präzise um Entities herumgeführt werden. Das Einrasten auf dem Raster stellt sicher, dass auch bei gemischten Routing-Modi (Curved, Gerade) ein visuell konsistentes, ausgerichtetes Diagramm entsteht.

## Akzeptanzkriterien

**AC1** (Ankerpunkt setzen):
- Wenn: Nutzer auf eine selektierte Connection klickt
- Dann: Neuer Ankerpunkt wird auf dem nächsten Rasterpunkt gesetzt und ist als Griff sichtbar

**AC2** (Ankerpunkt immer auf Raster):
- Wenn: Ankerpunkt per Drag & Drop verschoben wird (beliebiger Routing-Modus)
- Dann: Ankerpunkt rastet auf nächsten Rasterpunkt ein; sub-Raster-Positionierung ist nicht möglich

**AC3** (Ankerpunkt entfernen):
- Wenn: Nutzer einen Ankerpunkt doppelklickt oder im Kontextmenü „Ankerpunkt entfernen" wählt
- Dann: Ankerpunkt wird entfernt; Connection passt ihren Verlauf automatisch an

**AC4** (Modus-Verhalten):
- Wenn: Connection-Modus = Curved und Ankerpunkt gesetzt
- Dann: Bézierkurve verläuft durch den Ankerpunkt; Kurven-Griffe erscheinen beidseitig des Ankerpunkts zur Kurven-Steuerung
- Wenn: Connection-Modus = Orthogonal und Ankerpunkt gesetzt
- Dann: Segmente verlaufen rechtwinklig zum Ankerpunkt; Ankerpunkt liegt auf Raster-Schnittpunkt

## Abhängigkeiten

- **Voraussetzungen**: REQ-121 (Raster), REQ-122 (Routing-Modi)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Ankerpunkte werden im Diagram-Objekt pro Connection als `waypoints: [{x, y}]` gespeichert; Koordinaten sind immer Vielfache der Rastereinheit
- Curved: Waypoints als Bezier-Kontrollpunkte interpretieren; Curved-Edge in Vue Flow unterstützt `waypoints` als Zwischenpunkte nativ
- Orthogonal: Waypoints definieren Biegepunkte; Router berechnet rechtwinklige Segmente automatisch zwischen Waypoints

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
