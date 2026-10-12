# US-067: Verbindungs-Panel per Doppelklick öffnen und nach Typ filtern

**ID**: US-067
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Data Architekt möchte ich per Doppelklick auf den 3-Punkte-Indikator einer Connection ein Panel öffnen, das alle verknüpften n-Connections auflistet – und diese nach Connection-Typ filtern können – damit ich sofort sehe, welche Datenobjekte ein Datenfluss trägt und welche Sicherheitskontrollen darauf angewendet werden.

## Bezug

**Use Case**: [UC-08](../use-cases/UC-08-data-lineage-modellieren.md)
**Persona**: Lukas – Senior Data Architekt (SH-02); Kurt – Lead EA (SH-03)
**Requirements**: [REQ-063](../req/REQ-063-n-connection-canvas-darstellung.md)

## Akzeptanzkriterien

**AC1** (Panel öffnet sich):
- Wenn: Lukas doppelklickt auf 3-Punkte-Indikator von DataFlow id=5
- Dann: Verbindungs-Panel öffnet sich; Header zeigt „ERP → DWH (data-flow)"; Liste aller n-Connections sichtbar

**AC2** (Filter-Chips in Panel):
- Gegeben: DataFlow id=5 hat carries-data (×2) und security-control (×1)
- Wenn: Panel geöffnet
- Dann: Filter-Bar zeigt Chips `carries-data (2)` und `security-control (1)`; alle initial aktiv

**AC3** (Chip-Toggle filtert Liste):
- Wenn: Lukas klickt Chip `security-control`
- Dann: Nur security-control-Einträge sichtbar; carries-data ausgeblendet

**AC4** (Mehrere Chips gleichzeitig aktiv):
- Wenn: Lukas aktiviert carries-data und security-control
- Dann: Beide Typen sichtbar; Gesamtliste aller n-Connections beider Typen

**AC5** (Ziel-Entität sichtbar):
- Gegeben: carries-data id=103 verbindet DataFlow id=5 mit DataObject „Kundenstamm" (id=42)
- Wenn: Panel mit carries-data gefiltert
- Dann: Eintrag zeigt: „carries-data #103 → Kundenstamm (data-object, ID 42)"

**AC6** (Web Portal read-only):
- Wenn: CIO öffnet Panel im Web Portal
- Dann: Panel zeigt Einträge; kein „Neue n-Connection anlegen"-Button; keine Bearbeitungs-Icons

## Technische Hinweise

- Panel lädt via `GET /api/v1/entities/{id}/n-connections` (Filter-State lokal im Frontend)
- Referenz UI-Muster: Obsidian Backlink-Panel / Hover-Preview

## Definition of Done

- [ ] AC1–AC6 erfüllt
- [ ] Tests: Panel-Öffnen, Filter-Chips, Toggle, Ziel-Entität, Web Portal read-only
- [ ] E2E-Test (Playwright) für Doppelklick → Panel → Filter-Interaktion
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
