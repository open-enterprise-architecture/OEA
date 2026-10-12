---
id: REQ-072
title: Canvas-Rendering-Latenz (Diagramm laden und rendern)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-05
  business_objects:
    - entity
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-03
    - SH-04
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-007
supersedes: []
superseded_by: []
---

# REQ-072: Canvas-Rendering-Latenz (Diagramm laden und rendern)

## Aussage

Das System MUSS ein Architektur-Diagramm — vom Öffnen bis zur vollständig interaktiven Darstellung aller Shapes und Connections auf dem Canvas — innerhalb eines definierten Zeitbudgets darstellen.

## Begründung

Der Diagramm-Canvas (React Flow, ADR-007) ist das primäre Arbeitsmittel für EA-Modellierer (SH-03, SH-04). Ein Diagramm mit 150+ Elementen (realistisch für Bebauungspläne und Systemkontextdiagramme in Mittelstandsunternehmen) muss schnell genug laden, damit der Modellier-Flow nicht unterbrochen wird. Zu langes Rendering erhöht Frustration und führt zu flachen, unvollständigen Diagrammen.

## Kontext

Gemessen wird die Zeit von API-Response bis zur vollständig gerenderten, interaktiven Canvas (Time-to-Interactive, TTI). Umfasst: Datenabruf, React-Flow-Render, SVG-Layout. Nicht umfasst: initiales App-Laden (eigene NFR) oder Mermaid/PlantUML-Rendering im Arc42-Editor.

## Typ-spezifische Felder

### Bei type=non-functional

**Kategorie**: performance

**Messbare Zielwerte**:

| Metrik | Zielwert | Schwellwert (warning) | Schwellwert (critical) | Scope |
|---|---|---|---|---|
| Time-to-Interactive, kleines Diagramm | < 500ms | 800ms | 1.500ms | 50 Entities + 30 Connections |
| Time-to-Interactive, mittleres Diagramm | < 1.500ms | 2.500ms | 4.000ms | 150 Entities + 100 Connections |
| Time-to-Interactive, grosses Diagramm | < 3.000ms | 5.000ms | 8.000ms | 400 Entities + 300 Connections |
| Frame-Rate beim Verschieben von Shapes | ≥ 30 fps | < 30 fps | < 15 fps | mittleres Diagramm; modernem Desktop-Browser |

## Akzeptanzkriterien

**AC1** (Kleines Diagramm):
- Gegeben: Diagramm mit 50 Entities + 30 Connections; Chrome auf Standard-Developer-Hardware
- Wenn: Diagramm geöffnet
- Dann: Canvas interaktiv in < 500ms (gemessen ab vollständiger API-Response)

**AC2** (Mittleres Diagramm):
- Gegeben: 150 Entities + 100 Connections
- Wenn: Diagramm geöffnet
- Dann: Time-to-Interactive < 1.500ms

**AC3** (Grosses Diagramm):
- Gegeben: 400 Entities + 300 Connections
- Wenn: Diagramm geöffnet
- Dann: Time-to-Interactive < 3.000ms; keine Browser-Freezes > 100ms

**AC4** (Pan/Zoom flüssig):
- Gegeben: mittleres Diagramm (150+100)
- Wenn: Nutzer zieht Canvas oder zoomt mit Mausrad
- Dann: Frame-Rate ≥ 30 fps (kein sichtbares Ruckeln)

## Verifikationsmethode

- [x] Methode: test (automatisiert, Performance-Test im Browser)
- [x] Test-Setup: synthetische Diagramme mit definierten Entity-/Connection-Zahlen; Chrome DevTools Performance-Profil oder Playwright-Messungen
- [x] Mess-Werkzeug: Playwright + Chrome Performance-API (`performance.measure`)
- [x] Bestanden-Kriterium: Time-to-Interactive-Werte gemäss Tabelle; FPS via `requestAnimationFrame`-Loop
- [ ] In CI integriert: ja, als Performance-Gate (Playwright headless)

## Abhängigkeiten

- **Voraussetzungen**: ADR-007 (React Flow, accepted); REQ-040 (EntityDeltas → Canvas)
- **Folgewirkungen**: grosse Diagramme können Virtualisierung erfordern (React Flow Virtualization / windowing)

## Risiken bei Nichterfüllung

- Modellierer fragmentieren grosse Bebauungspläne in viele kleine Diagramme, um Latenz zu umgehen; Kohärenz geht verloren

## Trade-offs

- React-Flow-Virtualisierung (nur sichtbare Nodes rendern): drastische Latenzverbesserung bei grossen Diagrammen, aber Layout-Algorithmen werden komplexer
- Initiales Auto-Layout (z.B. ELK.js): erhöht TTI um 200–500ms, aber erspart manuelle Positionierung

## Realisierungs-Hinweise

- React Flow `nodesDraggable` und `elementsSelectable` erst nach erstem Render aktivieren (reduziert blockierendes JS)
- Für Diagramme > 300 Nodes: `<ReactFlow>` mit `onlyRenderVisibleElements` aktivieren

## Realisierung

- ADR(s): ADR-007
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
