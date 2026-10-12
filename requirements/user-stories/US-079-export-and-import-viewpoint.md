---
id: US-079
title: Viewpoint exportieren und importieren
use_case: UC-04
requirement: REQ-059
priority: should
status: proposed
story_points: 3
created: 2026-06-26
---

# US-079: Viewpoint exportieren und importieren

**Als** Lead Enterprise Architekt (Kurt, SH-03)  
**möchte ich** einzelne Viewpoints als JSON-Dateien exportieren und in anderen OEA-Instanzen importieren,  
**damit** Organisations-Standards für Diagramm-Ansichten (z.B. Standard-Bebauungsplan-View) geteilt werden können.

## Akzeptanzkriterien

- [ ] Einzelner Viewpoint exportierbar via Kontextmenü im Viewpoint-Manager
- [ ] Export enthält: Viewpoint-Metadaten, allowedEntityTypes, allowedConnectionTypes, NotationMappings
- [ ] Import via `POST /api/v1/metamodel/viewpoints/import` (JSON-Upload)
- [ ] Bei Namenskonflikt: Nutzer wählt zwischen Überschreiben, Umbenennen oder Abbrechen
- [ ] Alle built-in Viewpoints (bpmn-process-view etc.) sind exportierbar aber beim Import schreibgeschützt (scope=built-in bleibt erhalten)

## Technische Hinweise

- `GET /api/v1/metamodel/viewpoints/{id}/export`
- `POST /api/v1/metamodel/viewpoints/import`
- Konsistent mit Metamodell-Export (US-078): gleiches Paket-Format (ADR-002)
