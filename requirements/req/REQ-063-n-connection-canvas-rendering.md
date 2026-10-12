---
id: REQ-063
title: n-Connection Canvas-Darstellung (3-Punkte-Indikator + Verbindungs-Panel)
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - viewpoint
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs:
    - ADR-010
    - ADR-007
    - ADR-009
supersedes: []
superseded_by: []
---

# REQ-063: n-Connection Canvas-Darstellung (3-Punkte-Indikator + Verbindungs-Panel)

## Aussage

Das System MUSS im Canvas-Editor der Client App (Electron, ADR-009) für jede Connection-Entity, die als `sourceEntityId` in mindestens einer weiteren Connection (n-Connection) referenziert wird, einen **kreisförmigen 3-Punkte-Indikator** (`•••`) auf der Verbindungslinie darstellen; Doppelklick auf diesen Indikator MUSS ein **Verbindungs-Panel** öffnen, das alle n-Connections dieser Connection auflistet, am oberen Rand nach Connection-Typ filterbar ist.

## Begründung

Eine Connection, die selbst Quelle weiterer Connections ist, wäre ohne visuellen Hinweis unsichtbar komplex — der Nutzer würde nicht merken, dass an einer Linie weitere Informationen hängen. Der 3-Punkte-Indikator signalisiert: „Diese Verbindung hat mehr". Das Verbindungs-Panel mit Typ-Filtern erlaubt es, verschiedenartige n-Connections (carries-data, security-control, compliance-requirement) getrennt zu betrachten. Referenz: Obsidian (Graph View / Backlink-Panel).

## Kontext

Betroffen von diesem REQ:
- **Client App** (Electron, ADR-009): vollständiger Canvas-Editor (React Flow, ADR-007); 3-Punkte-Indikator und Verbindungs-Panel sind hier implementiert
- **Web Portal**: read-only; zeigt den Indikator ebenfalls (aber kein Editier-Modus im Panel)

Der Indikator wird beim Laden eines Diagramms server-seitig vorberechnet: für jede Connection im Diagramm prüft das System, ob sie als `sourceEntityId` in einer n-Connection vorkommt (`SELECT * FROM architecture_entities WHERE source_entity_id = ?`). Das Ergebnis fließt in die Diagramm-Antwort als `hasNConnections: bool`.

## Typ-spezifische Felder

### 3-Punkte-Indikator

| Eigenschaft | Wert |
|---|---|
| Form | Kreis (Radius ≈ 10px); farblich dezent (Grau oder Sekundärfarbe des Viewpoints) |
| Inhalt | `•••` (drei Punkte); alternativ Icon |
| Position | Mittig auf der Connection-Linie; ggf. leicht versetzt wenn mehrere Indikatoren auf einer Linie |
| Sichtbarkeit | Immer sichtbar, wenn `hasNConnections=true`; verschwindet nicht beim Zoom |
| Interaktion | Einzelklick: Indikator hervorheben; Doppelklick: Verbindungs-Panel öffnen |

### Verbindungs-Panel

| Element | Beschreibung |
|---|---|
| Header | Name der Connection (z.B. „ERP → DWH") + EntityType-Label |
| Filter-Bar | Horizontale Chip-Leiste oben; ein Chip pro vorhandenem n-Connection-Typ (z.B. `carries-data (2)`, `security-control (1)`); Chip ist Toggle (aktiv/inaktiv); Mehrfachauswahl möglich |
| Liste | Unterhalb der Filter: alle n-Connections dieses Typs (gefiltert); pro Eintrag: ID, Name, Ziel-Entität (Name + EntityType), Properties (eingeklappt/ausgeklappt) |
| Leer-Zustand | „Keine Verbindungen dieses Typs" wenn Filter aktiv und keine Treffer |
| Bearbeiten | Button „Neue n-Connection anlegen" (öffnet Anlage-Dialog; nur Client App); read-only im Web Portal |

### Filterverhalten

- Beim Öffnen des Panels: alle Chips aktiviert → alle n-Connection-Typen sichtbar
- Klick auf einzelnen Chip: nur dieser Typ bleibt aktiv (Exclusive-Toggle); Nochmaliger Klick: zurück zu „alle aktiv"
- Mehrere Chips können gleichzeitig aktiv sein (Shift-Klick oder entsprechendes UX-Muster TBD)

## Akzeptanzkriterien

**AC1** (Indikator erscheint):
- Gegeben: DataFlow id=5 ist `sourceEntityId` von carries-data id=103
- Wenn: Lukas öffnet ein Diagramm, das DataFlow id=5 enthält
- Dann: Auf der Linie von DataFlow id=5 erscheint der 3-Punkte-Kreis-Indikator

**AC2** (Kein Indikator ohne n-Connection):
- Gegeben: DataFlow id=7 ist in keiner n-Connection sourceEntityId
- Wenn: Diagramm mit DataFlow id=7 geöffnet
- Dann: Kein Indikator auf dieser Linie

**AC3** (Doppelklick öffnet Panel):
- Wenn: Lukas doppelklickt den 3-Punkte-Indikator auf DataFlow id=5
- Dann: Verbindungs-Panel öffnet sich; Header zeigt „ERP → DWH (data-flow)"; Filter-Bar zeigt Chip `carries-data (2)`

**AC4** (Filter-Chip schaltet Ansicht):
- Gegeben: Panel mit Chips `carries-data (2)` und `security-control (1)` (beide aktiv)
- Wenn: Lukas klickt auf `security-control`
- Dann: Nur `security-control`-Verbindungen sichtbar; `carries-data`-Einträge ausgeblendet

**AC5** (Web Portal read-only):
- Wenn: CIO öffnet im Web Portal ein Diagramm mit n-Connection-Indikator
- Dann: Indikator sichtbar; Doppelklick öffnet Panel in read-only; „Neue n-Connection anlegen"-Button fehlt

**AC6** (Mehrere Indikatoren auf einer Linie):
- Gegeben: DataFlow id=5 hat 3 carries-data und 2 security-control Verbindungen
- Wenn: Diagramm geladen
- Dann: Ein einziger Indikator (nicht 5 separate); Panel zeigt beide Typen in Filter-Bar; Gesamtzahl sichtbar (z.B. `•••  5`)

## Verifikationsmethode

- [x] Methode: manuell (UI-Test im Client App)
- [x] Test-Setup: Diagramm mit DataFlow mit und ohne n-Connections; Panel-Interaktion
- [x] Bestanden-Kriterium: AC1–AC6 grün bei manueller Verifikation; AC1/AC2 auch per automatisiertem Test (hasNConnections-Flag)
- [ ] In CI integriert: AC1/AC2 automatisiert; AC3–AC6 E2E-Test (Playwright)

## Abhängigkeiten

- **Voraussetzungen**: REQ-061 (carries-data-Connections müssen existieren); ADR-007 (React Flow); ADR-009 (Electron)
- **Folgewirkungen**: keine direkten

## Realisierungs-Hinweise

- `hasNConnections`-Flag im Diagramm-Load-Response (Backend berechnet beim Laden)
- React Flow Custom Edge: wenn `hasNConnections=true`, wird ein Custom EdgeMarker (Kreis mit `•••`) mitgerendert
- Panel als React Side-Panel / Popover; lädt n-Connection-Daten via `GET /api/v1/entities/{id}/n-connections`
- Filter-State: lokal im Frontend (kein Servercall beim Filtern)

## Realisierung

- ADR(s): [ADR-010](../../adrs/ADR-010-n-connection-data-lineage.md) (proposed), [ADR-007](../../adrs/ADR-007-canvas-bibliothek.md), [ADR-009](../../adrs/ADR-009-client-app-framework.md)
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft; 3-Punkte-Indikator + Panel mit Typ-Filter; Obsidian als Referenz |
