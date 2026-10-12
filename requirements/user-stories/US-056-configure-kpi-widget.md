# US-056: KPI-Widget mit PropertyAggregation konfigurieren

**ID**: US-056
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einem Dashboard ein KPI-Widget hinzufügen, das eine aggregierte Kennzahl (z.B. Summe der Investitionskosten aller Plateaus) als einzelne prominente Zahl darstellt – damit der CIO auf einen Blick die wichtigste Kenngrösse sieht.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-052: Widget konfigurieren](../req/REQ-052-widget-konfigurieren.md), [REQ-053: PropertyAggregation-DataSource](../req/REQ-053-property-aggregation-datasource.md), [REQ-055: Daten abrufen](../req/REQ-055-dashboard-daten-abrufen.md)

## Akzeptanzkriterien

**AC1** (Widget anlegen mit sum):
- Gegeben: MetamodelConfiguration enthält EntityType `plateau` mit PropertyDefinition `investitionskostenPrognose` (kind=int); 3 Plateau-Instanzen mit Werten 5 000 000, 12 000 000, 25 000 000
- Wenn: Kurt legt KPI-Widget an: title="Gesamtinvestition Prognose", entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum, unit="CHF"
- Dann: Widget gespeichert; beim Daten-Abruf zeigt das Widget „42 000 000 CHF"

**AC2** (count-Aggregation):
- Gegeben: 15 Plateau-Instanzen im Repository
- Wenn: Kurt legt KPI-Widget an: propertyName=id, aggregationFn=count, unit="Plateaus"
- Dann: Widget zeigt „15 Plateaus"

**AC3** (Ungültige Aggregation: varchar-Property mit sum):
- Wenn: Kurt wählt propertyName=name (kind=varchar), aggregationFn=sum
- Dann: Fehlermeldung „sum erfordert ein Integer-Property"; Widget nicht gespeichert

**AC4** (Keine Daten → null-Anzeige):
- Gegeben: EntityType `plateau` hat keine Instanzen mit befülltem Property
- Wenn: Daten abrufen
- Dann: Widget zeigt „– (keine Daten)" statt einer Zahl; kein Fehler-Status

**AC5** (comparisonMode=vs-target ohne targetValue):
- Wenn: Kurt setzt comparisonMode=vs-target ohne targetValue
- Dann: Fehler „targetValue ist Pflicht bei comparisonMode=vs-target"

## Technische Hinweise

- Widget-Typ: `kpi`; DataSource muss `PropertyAggregation` sein (CatalogQuery → 422)
- Zahlendarstellung: numberFormat aus Widget-Konfiguration; Default: locale-sensitives Format ohne Nachkommastellen
- `value=null` bei leeren Daten: Frontend rendert „–" statt 0

## Definition of Done

- [ ] AC1–AC5 erfüllt
- [ ] Tests: sum, count, ungültige Aggregation, null-Daten, fehlender targetValue
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
