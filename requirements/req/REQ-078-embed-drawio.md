---
id: REQ-078
title: Draw.io-Diagramme im WYSIWYG-Editor einbetten
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - arc42
    - entity
  business_rules:
    - arc42:BR-08
    - arc42:BR-09
  stakeholders:
    - SH-04
    - SH-08
  concept:
    - concept/20-entities/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-078: Draw.io-Diagramme im WYSIWYG-Editor einbetten

## Aussage

Das System MUSS im WYSIWYG-Editor (Arc42, UC-09) das Einbetten von Draw.io-Diagrammen als Codeblock (`drawio`) ermöglichen. Der Codeblock enthält Draw.io-XML (`<mxGraphModel>`); das System MUSS dieses XML als SVG-Vorschau rendern. Beim Klick auf die Vorschau MUSS ein Modal mit dem draw.io-Editor öffnen; nach dem Speichern im Editor wird das XML im Codeblock aktualisiert. Das System MUSS beim Bearbeiten von Shape-Labels im draw.io-Editor das Entity-Mention-Autocomplete via `[[` anbieten (reiner Textname als Ergebnis, REQ-070 BR-08).

## Begründung

Arc42-Dokumentationen enthalten häufig strukturierte Diagramme, die weder in Mermaid noch in PlantUML gut modellierbar sind: detaillierte Komponentendiagramme, Deployment-Topologien, freie Skizzen. Draw.io ist das meistgenutzte freie Diagramm-Tool (Marktführer in dieser Nische, VS Code Extension + Confluence App). Die Einbettung als Plugin ermöglicht, dass Solution Architekten (SH-04) und Business Analysts (SH-08) bestehende Draw.io-Diagramme direkt in die Architekturdokumentation einbetten, ohne Screenshot-Workarounds.

## Kontext

Draw.io (aka diagrams.net) ist Open Source (Apache 2.0) und stellt eine Embed-API bereit (`diagrams.net/js/viewer-static.min.js` für Read-only-Rendering und `embed.diagrams.net` für Editor-Embedding via postMessage-Protokoll). Beide Ansätze funktionieren lokal (kein externer Server nötig, wenn die JS-Bibliothek mitgeliefert wird).

Das XML-Format (`mxGraphModel`) ist stabil; OEA speichert es raw im `content`-Feld des WYSIWYG-Editors als `drawio`-Codeblock — identisch zur Handhabung von `mermaid` und `plantuml` Codeblöcken.

## Typ-spezifische Felder

### Codeblock-Format

Im `content`-Feld des Arc42MetaObject wird ein Draw.io-Diagramm wie folgt gespeichert:

````
```drawio
<mxGraphModel dx="1422" dy="762" grid="1" ...>
  <root>
    <mxCell id="0"/>
    <mxCell id="1" parent="0"/>
    <mxCell id="2" value="CRM-System" style="rounded=1;..." vertex="1" parent="1">
      <mxGeometry x="80" y="80" width="160" height="60" as="geometry"/>
    </mxCell>
    <mxCell id="3" value="SAP ERP" style="rounded=1;..." vertex="1" parent="1">
      <mxGeometry x="360" y="80" width="160" height="60" as="geometry"/>
    </mxCell>
    <mxCell id="4" value="sendet Daten" style="edgeStyle=..." edge="1" source="2" target="3" parent="1">
      <mxGeometry relative="1" as="geometry"/>
    </mxCell>
  </root>
</mxGraphModel>
```
````

### Rendering-Verhalten

| Zustand | Darstellung |
|---|---|
| Leseansicht | SVG-Rendering des XML via draw.io-Viewer-Library; kein interaktiver Editor |
| Bearbeitungsansicht | Vorschau-SVG mit „Öffnen in Draw.io"-Button oben rechts |
| Editor-Modal geöffnet | `iframe` oder Popup mit draw.io-Editor; `embed.diagrams.net` via `postMessage` |
| Nach Editor-Speichern | Modal schliesst; XML im Codeblock wird durch aktualisiertes XML ersetzt |
| Kein XML (leerer Block) | Platzhalter „Draw.io-Diagramm hinzufügen" mit „Öffnen"-Button |

### Entity-Mention im draw.io-Editor

Wenn der Nutzer im draw.io-Editor ein Shape-Label bearbeitet und `[[` tippt:
- OEA-Integration (via draw.io-Embed-API postMessage) fängt das Tastatur-Event ab
- Autocomplete-Dropdown erscheint über dem draw.io-Modal
- Auswahl fügt den Entitätsnamen als reinen Text in das Shape-Label ein
- Kein ID-Link im XML (BR-08 / BR-09)

Die Integration benötigt eine kleine JavaScript-Brücke zwischen dem draw.io-iframe und dem TipTap-Editor.

### Sicherheit

- Das `<mxGraphModel>`-XML MUSS vor dem Rendering via DOMParser sanitisiert werden (BR-09)
- Verbotene Elemente: `<script>`, Event-Handler-Attribute (`onload`, `onclick`, etc.), `javascript:`-URIs in `href`-Attributen
- Erlaubte Elemente: alle `mxCell`-Elemente und draw.io-spezifische Tags
- Das erzeugte SVG des Viewer-Library muss ebenfalls sanitisiert werden (SVG kann inline-Scripts enthalten)
- `iframe` für draw.io-Editor: `sandbox="allow-scripts allow-same-origin"` + `Content-Security-Policy`

## Akzeptanzkriterien

**AC1** (Rendering aus Leseansicht):
- Gegeben: Arc42-Antwort mit `drawio`-Codeblock
- Wenn: Nutzer öffnet die Antwort
- Dann: Draw.io-Diagramm als SVG gerendert (nicht als XML-Text); korrekte Shapes und Connections sichtbar

**AC2** (Editor-Modal öffnen):
- Gegeben: Nutzer ist im Bearbeitungsmodus der Arc42-Antwort
- Wenn: Klick auf „In Draw.io öffnen"
- Dann: Modal öffnet sich mit dem vollen draw.io-Editor; vorhandenes XML ist geladen; Nutzer kann Shapes hinzufügen, verschieben, beschriften

**AC3** (Änderungen übernehmen):
- Wenn: Nutzer im draw.io-Editor speichert (Ctrl+S oder Speichern-Button)
- Dann: Modal schliesst; aktualisiertes XML erscheint im `drawio`-Codeblock; SVG-Vorschau wird sofort aktualisiert

**AC4** (Neues leeres Diagramm):
- Wenn: Nutzer fügt einen leeren `drawio`-Codeblock ein und klickt „Öffnen"
- Dann: draw.io-Editor öffnet mit leerem Canvas; nach Speichern steht XML im Block

**AC5** (Entity-Mention in Shape-Label):
- Wenn: Nutzer bearbeitet ein Shape-Label im draw.io-Editor und tippt `[[CRM`
- Dann: Autocomplete-Dropdown öffnet sich über dem draw.io-Modal; nach Auswahl wird `CRM-System` als reiner Text ins Shape-Label eingefügt

**AC6** (Sicherheit — kein XSS):
- Gegeben: Draw.io-XML mit `<mxCell value="<script>alert(1)</script>"/>`
- Wenn: Rendering
- Dann: Script-Tag wird entfernt oder escaped; kein JavaScript ausgeführt

**AC7** (Web-Portal read-only):
- Wenn: Nutzer im Web-Portal Arc42-Dokumentation öffnet
- Dann: Draw.io-Diagramm als SVG sichtbar; kein Editor-Button; kein postMessage-Bridge aktiv

**AC8** (Offline-fähig):
- Gegeben: OEA-Instanz ohne Internetzugang; draw.io-Viewer-Library ist lokal mitgeliefert
- Wenn: Nutzer öffnet Antwort mit `drawio`-Block
- Dann: SVG-Rendering funktioniert ohne externe Anfragen; draw.io-Editor-Popup verwendet ebenfalls lokale draw.io-Installation (wenn konfiguriert)

## Verifikationsmethode

- [x] Methode: test (Playwright E2E für AC1–AC5, AC7) + manuell (AC6 Security-Review, AC8)
- [x] Bestanden-Kriterium: SVG sichtbar (kein `<xml>`-Text); Editor öffnet; XML nach Speichern korrekt; keine Script-Execution

## Abhängigkeiten

- **Voraussetzungen**: REQ-068 (WYSIWYG-Editor existiert); REQ-070 (`[[`-Trigger für Entity-Mentions)
- **Neue Bibliothek**: `draw.io-viewer-static.min.js` (Apache 2.0; kann lokal mitgeliefert werden); `embed.diagrams.net` als Editor-Embedding-Option oder Self-hosted draw.io-Instanz
- **Konfiguration**: `drawioEditorUrl` in MetamodelConfiguration → Default `https://embed.diagrams.net`; für Air-gapped Umgebungen: URL zu lokaler draw.io-Instanz
- **Beeinflusst**: REQ-075 (Plattformunabhängigkeit): lokale draw.io-Instanz als Docker-Container konfigurierbar

## Trade-offs

- draw.io online (`embed.diagrams.net`) ist einfacher einzurichten, setzt aber Internetverbindung voraus — widerspricht REQ-075 für Air-gapped-Deployments
- Lokale draw.io-Instanz (selbst gehostet via `jgraph/drawio` Docker-Image) ist vollständig offline, erfordert aber eine weitere Abhängigkeit im Deployment
- Lösung: `drawioEditorUrl` als konfigurierbare Option; Default = online; Air-gapped-Betreiber setzen die URL auf ihre lokale Instanz

## Realisierungs-Hinweise

- TipTap CodeBlock-Extension mit Language-Attribut `drawio`; Language-spezifisches Rendering (andere Codeblöcke: Syntax-Highlighting; `drawio`: SVG-Vorschau)
- draw.io-Embed-Protokoll: `postMessage({action: 'load', xml: '...'})` → Editor lädt; auf `{event: 'save', xml: '...'}` reagieren → XML ins TipTap-Dokument schreiben
- XML-Sanitizing: DOMParser → traversal → Whitelist-basiertes Filtering; kein `innerHTML`-Assignment ohne Sanitizing

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
