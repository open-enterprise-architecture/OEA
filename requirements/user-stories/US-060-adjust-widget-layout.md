# US-060: Widget-Layout im 12-Spalten-Raster anpassen

**ID**: US-060
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich die Widgets auf einem Dashboard per Drag-and-Drop in einem 12-Spalten-Raster positionieren und in Grösse und Position anpassen – damit das Dashboard eine übersichtliche, C-Level-gerechte Anordnung hat.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-057: Widget-Grid-Layout](../req/REQ-057-widget-grid-layout.md), [REQ-052](../req/REQ-052-widget-konfigurieren.md)

## Akzeptanzkriterien

**AC1** (Widget positionieren):
- Wenn: Kurt zieht ein KPI-Widget auf Spalte 1, Zeile 1, Breite 3, Höhe 1 und ein Chart-Widget auf Spalte 4, Zeile 1, Breite 9, Höhe 3
- Dann: Beide GridPositions werden via PUT /api/v1/dashboards/{id}/widgets/{widgetId} gespeichert; bei Neu-Laden des Dashboards sind die Positionen erhalten

**AC2** (Ungültige Breite abgelehnt):
- Wenn: Kurt zieht Widget auf Spalte 5, Breite 9 (col + width = 14)
- Dann: Fehlermeldung „Widget überschreitet die Rasterbreite"; Position wird nicht gespeichert

**AC3** (Dashboard scrollt vertikal):
- Wenn: Kurt positioniert Widgets auf Zeile 1–8 (mehr als auf den Bildschirm passt)
- Dann: Dashboard scrollt vertikal; keine Fehlermeldung; alle Widgets abrufbar

**AC4** (Overlapping-Warnung client-seitig):
- Wenn: Kurt positioniert zwei Widgets so, dass sie sich überlappen
- Dann: Client zeigt optische Warnung (z.B. rote Umrandung); Server akzeptiert beide Positionen (kein 422)

**AC5** (Layout im Web Portal read-only):
- Wenn: CIO öffnet Dashboard im Web Portal
- Dann: Widgets sind in der gespeicherten GridPosition dargestellt; kein Drag-and-Drop; kein Edit-Modus

## Technische Hinweise

- Drag-and-Drop nur in der **Client App** (Electron); Web Portal rendert statisch
- Empfohlene Bibliothek: `react-grid-layout` oder ähnliches; keine eigene ADR nötig
- GridPosition-Update: `PUT /api/v1/dashboards/{id}/widgets/{widgetId}` mit nur geänderten GridPosition-Feldern
- Batch-Update (mehrere Widgets gleichzeitig verschieben): optional in v1.0; alternativ sequenzielle PUTs

## Definition of Done

- [ ] AC1–AC5 erfüllt
- [ ] Tests: Position speichern, ungültige Breite, vertikales Scrollen, read-only im Web Portal
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
