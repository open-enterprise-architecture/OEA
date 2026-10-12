# US-058: Table-Widget mit Katalog-DataSource konfigurieren

**ID**: US-058
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Lead Enterprise Architekt möchte ich einem Dashboard ein Table-Widget hinzufügen, das die Zeilen eines bestehenden Katalogs direkt im Dashboard einbettet – damit ich neben Charts auch Detailtabellen (z.B. Top-Applikationen nach Kosten) auf demselben Dashboard anzeigen kann.

## Bezug

**Use Case**: [UC-07](../use-cases/UC-07-dashboard-anlegen-und-verwenden.md)
**Persona**: Kurt – Lead Enterprise Architekt (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-widget-konfigurieren.md), [REQ-054](../req/REQ-054-catalog-query-datasource.md), [REQ-055](../req/REQ-055-dashboard-daten-abrufen.md)

## Akzeptanzkriterien

**AC1** (Widget mit Katalog anlegen):
- Gegeben: Katalog „Application Inventory" (scope=instance) existiert; SavedView „Kompaktansicht" vorhanden
- Wenn: Kurt legt Table-Widget an: title="Applikations-Übersicht", catalogId=<id>, savedViewId=<view-id>, pageSize=10
- Dann: Widget gespeichert; beim Daten-Abruf zeigt die Tabelle die ersten 10 Zeilen gemäss SavedView-Konfiguration

**AC2** (Ohne SavedView → Standard-View des Katalogs):
- Wenn: Table-Widget ohne savedViewId konfiguriert
- Dann: Katalog-Abfrage verwendet den Default-Stand des Katalogs (keine SavedView)

**AC3** (Katalog nicht sichtbar):
- Gegeben: Franz hat einen scope=personal-Katalog
- Wenn: Kurt versucht, diesen Katalog als DataSource zu nutzen
- Dann: HTTP 403; Widget nicht gespeichert

**AC4** (Katalog später gelöscht: Fehler-Isolation):
- Gegeben: Table-Widget referenziert Katalog „Inventory"; Katalog wird danach gelöscht
- Wenn: Dashboard-Daten abgerufen
- Dann: Table-Widget zeigt Fehlerhinweis „Katalog nicht gefunden"; andere Widgets rendern normal

**AC5** (PropertyAggregation für table → abgelehnt):
- Wenn: Table-Widget mit DataSource=PropertyAggregation konfiguriert
- Dann: HTTP 422 „Table-Widget erfordert CatalogQuery DataSource"

## Technische Hinweise

- Widget-Typ: `table`; nur CatalogQuery als DataSource erlaubt
- Tabellen-Paginierung: Frontend rendert Paginierungssteuerung basierend auf `totalCount` und `pageSize`
- Column-Overrides: optionale Titel-Umbenennung oder Breiten-Override im Dashboard-Kontext (ohne den Katalog zu verändern)

## Definition of Done

- [ ] AC1–AC5 erfüllt
- [ ] Tests: mit SavedView, ohne SavedView, fremder Katalog, gelöschter Katalog, falsche DataSource
- [ ] Code-Review
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen
