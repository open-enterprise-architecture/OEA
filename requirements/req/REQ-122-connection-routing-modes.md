---
id: REQ-122
title: Connection-Routing-Modi (Curved, Gerade, Orthogonal)
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

# REQ-122: Connection-Routing-Modi (Curved, Gerade, Orthogonal)

## Aussage

Das System MUSS für jede Connection auf dem Canvas drei Routing-Modi anbieten, die pro Connection individuell wählbar sind:

| Modus | Verlauf | Raster-Bezug |
|---|---|---|
| **Curved** | Bézierkurve; weiches Schwingen um Ankerpunkte | folgt **nicht** dem Raster |
| **Gerade** | Direkte Linie vom Quell- zum Ziel-Ankerpunkt | folgt **nicht** zwingend dem Raster |
| **Orthogonal** | Ausschliesslich 90°-Winkel (rechtwinklige Führung) | folgt dem **Raster** |

Der Routing-Modus MUSS über das Kontextmenü der Connection (Rechtsklick) oder eine Toolbar bei selektierter Connection änderbar sein. Der Standard-Routing-Modus für neue Connections MUSS im Viewpoint konfigurierbar sein.

## Begründung

Verschiedene Diagramm-Typen erfordern unterschiedliche Verbindungsführungen: ArchiMate-Diagramme bevorzugen orthogonale Verbindungen; informelle Skizzen profitieren von Bézierkurven; einfache Relationsdiagramme kommen mit geraden Linien aus. Ohne diese drei Modi müssen Nutzer auf externe Diagramm-Tools ausweichen.

## Akzeptanzkriterien

**AC1** (Curved-Modus):
- Wenn: Routing-Modus = Curved
- Dann: Connection wird als Bézierkurve gerendert; Kurven-Verlauf ist durch Bézierkurven-Griffe (via Ankerpunkte, REQ-123) steuerbar; Linie folgt keinem Raster

**AC2** (Orthogonal-Modus):
- Wenn: Routing-Modus = Orthogonal
- Dann: Connection macht ausschliesslich 90°-Biegungen; alle Segmente verlaufen horizontal oder vertikal; Biegepunkte liegen auf Rasterpunkten (REQ-121)

**AC3** (Modus-Wechsel):
- Wenn: Nutzer Routing-Modus einer bestehenden Connection ändert
- Dann: Connection wird sofort neu gerendert; bestehende Ankerpunkte (REQ-123) bleiben erhalten und werden auf das neue Routing angewendet (ggf. auf Raster gerundet bei Wechsel zu Orthogonal)

**AC4** (Viewpoint-Default):
- Wenn: Neue Connection auf Canvas gezogen wird
- Dann: Routing-Modus entspricht dem im Viewpoint konfigurierten Default; ohne Konfiguration = Orthogonal

## Abhängigkeiten

- **Voraussetzungen**: REQ-121 (Raster für Orthogonal-Modus), REQ-123 (Ankerpunkte)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Vue Flow: `edgeType='smoothstep'` (Orthogonal), `'straight'` (Gerade), `'default'` (Curved/Bezier)
- Orthogonal-Routing: Segmente snappen auf `snapGrid`-Einheit; Biegepunkte liegen immer auf Rasterpunkten
- Modus-Wechsel: beim Wechsel Curved → Orthogonal werden Ankerpunkte auf nächsten Rasterpunkt gerundet

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
