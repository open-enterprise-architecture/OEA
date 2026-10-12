# US-122: Connection-Routing-Modus wählen (Curved / Gerade / Orthogonal)

**ID**: US-122
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Kurt – Lead Enterprise Architekt möchte ich pro Connection wählen können, ob sie geschwungen (Curved), gerade oder orthogonal (rechtwinklig auf dem Raster) verläuft, damit ich verschiedene Diagramm-Konventionen unterstützen und Übersichtlichkeit gezielt steuern kann.

## Bezug

**Use Case**: [UC-05](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-122](../req/REQ-122-connection-routing-modi.md)

## Akzeptanzkriterien

**AC1** (Curved):
- Wenn: Routing-Modus = Curved
- Dann: Connection als Bézierkurve; keine Raster-Bindung

**AC2** (Orthogonal):
- Wenn: Routing-Modus = Orthogonal
- Dann: Ausschliesslich 90°-Segmente; Biegepunkte auf Rasterpunkten

**AC3** (Modus-Wechsel):
- Wenn: Routing-Modus einer bestehenden Connection geändert
- Dann: Sofortiges Neu-Rendering; bestehende Ankerpunkte bleiben erhalten

**AC4** (Viewpoint-Default):
- Wenn: Neue Connection gezogen wird
- Dann: Standard-Routing aus Viewpoint-Konfiguration; fallback = Orthogonal

## Definition of Done

- [ ] AC1–AC4 erfüllt
- [ ] Tests vorhanden
- [ ] Code-Review; Linter grün; In Trace-Matrix eingetragen
