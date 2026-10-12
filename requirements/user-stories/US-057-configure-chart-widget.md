# US-057: Chart-Widget konfigurieren (Bar, Pie, weitere Typen)

**ID**: US-057
**Story Points**: 8
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einem Dashboard Chart-Widgets (Bar, Line, Pie, Donut, Area, Treemap) hinzufügen und mit einer PropertyAggregation-DataSource samt Gruppierung konfigurieren – damit ich dem CIO Verteilungen und Vergleiche (z.B. Investitionskosten pro Plateau, Applikationen pro Domäne) als aussagekräftige Diagramme zeigen kann.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-widget-konfigurieren.md), [REQ-053](../req/REQ-053-property-aggregation-datasource.md), [REQ-055](../req/REQ-055-dashboard-daten-abrufen.md), [REQ-057](../req/REQ-057-widget-grid-layout.md)

## Akzeptanzkriterien

**AC1** (Bar-Chart: Kosten pro Plateau):
- Gegeben: 3 Plateau-Instanzen mit investitionskostenPrognose: P1=5M, P2=12M, P3=25M
- Wenn: Kurt konfiguriert Chart-Widget: chartType=bar, entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum, groupBy=name
- Dann: Bar-Chart mit 3 Balken; X-Achse = Plateau-Name; Y-Achse = Betrag; Balken-Höhen korrekt

**AC2** (Pie-Chart: Applikationen nach Domäne):
- Gegeben: 20 ApplicationComponents, 12 domain=finance, 8 domain=hr
- Wenn: Chart-Widget: chartType=pie, entityType=application-component, propertyName=id, aggregationFn=count, groupBy=architectureDomainId
- Dann: Pie-Chart mit 2 Segmenten (Finance 60%, HR 40%); Labels zeigen Domain-Namen (nicht IDs)

**AC3** (Pie-Chart: zwei yAxis abgelehnt):
- Wenn: chartType=pie mit zwei yAxis-Einträgen konfiguriert
- Dann: HTTP 422 „pie erlaubt genau eine Y-Achse"

**AC4** (groupBy=architectureLayerId):
- Gegeben: EntityTypes mit Ebenen-Zuweisung; 50 Entitäten verteilt auf 3 Ebenen
- Wenn: Bar-Chart, aggregationFn=count, groupBy=architectureLayerId
- Dann: 3 Balken (Business, Applikation, Technologie); Labels aus ArchitectureLayerDefinition.name

**AC5** (Legende und DataLabels):
- Wenn: showLegend=true, showDataLabels=true konfiguriert
- Dann: Chart rendert Legende und direkte Wert-Beschriftungen auf den Chart-Elementen

**AC6** (Ungültige groupBy-Referenz):
- Wenn: groupBy=nichtVorhandenesProperty
- Dann: HTTP 422 „groupBy 'nichtVorhandenesProperty' ist kein gültiges PropertyDefinition-Name auf EntityType 'application-component' und kein Systemwert"

## Technische Hinweise

- Chart-Typen: bar, line, pie, donut, area, treemap (alle dieselbe Widget-Konfiguration; nur chartType wechselt)
- xAxis ist optional bei pie/donut/treemap; für bar/line/area ist xAxis Pflicht wenn groupBy gesetzt
- groupBy-Systemwerte `architectureDomainId` und `architectureLayerId` lösen Name-Labels aus den zugehörigen Definitions auf (nicht Roh-IDs)
- Chart-Bibliothek für das Web Portal: noch nicht entschieden (kein ADR nötig; Implementation-Entscheidung)

## Definition of Done

- [ ] AC1–AC6 erfüllt
- [ ] Tests: bar mit groupBy, pie mit architectureDomainId, pie-2-yAxis-Fehler, architectureLayerId, DataLabels/Legende, ungültige groupBy
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
