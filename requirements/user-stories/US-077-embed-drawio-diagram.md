---
id: US-077
title: Draw.io-Diagramm in Arc42-Dokumentation einbetten
use_case: UC-09
requirement: REQ-078
priority: should
status: proposed
story_points: 8
created: 2026-06-26
---

# US-077: Draw.io-Diagramm in Arc42-Dokumentation einbetten

**Als** Solution Architekt (Michael, SH-04)  
**möchte ich** in einer Arc42-Dokumentationsantwort ein Draw.io-Diagramm einbetten und direkt im Tool bearbeiten,  
**damit** ich detaillierte Komponentendiagramme und Deployment-Topologien ohne Screenshot-Workaround direkt in der Architekturdokumentation pflegen kann.

## Akzeptanzkriterien

- [ ] Arc42-WYSIWYG-Editor bietet Codeblock-Typ `drawio` an (via `/`-Slash-Command oder Toolbar-Dropdown)
- [ ] Leerer `drawio`-Block zeigt Platzhalter mit „In Draw.io öffnen"-Button
- [ ] Klick auf „In Draw.io öffnen" öffnet draw.io-Editor-Modal (embed.diagrams.net oder Self-hosted)
- [ ] Nach Speichern im draw.io-Editor wird das XML im Block gespeichert und als SVG gerendert
- [ ] SVG-Vorschau im Leseansicht korrekt und ohne XML-Text sichtbar
- [ ] Tippen von `[[` in einem Shape-Label im draw.io-Editor öffnet Entity-Autocomplete; Auswahl fügt Entitätsnamen als plain text ein
- [ ] Draw.io-XML wird vor dem Rendering sanitisiert (keine Script-Tags, keine javascript:-URIs) — AC6 REQ-078
- [ ] Web-Portal: SVG read-only sichtbar; kein Editor-Button
- [ ] Konfigurierbarer `drawioEditorUrl` (Env/MetamodelConfiguration) für Air-gapped-Deployments

## Technische Hinweise

- draw.io Embed-API: `postMessage`-Protokoll; TipTap-CodeBlock mit Language `drawio`
- Viewer: `draw.io-viewer-static.min.js` (lokal mitgeliefert)
- Sanitizing: DOMParser → Whitelist-Traversal vor Rendering und vor Speichern

## Nicht im Scope

- Automatische Synchronisierung von Shape-Labels mit OEA-Entitätsnamen bei Umbenennung (v2.0)
- Import bestehender `.drawio`-Dateien als Dateiupload (separates Feature)
