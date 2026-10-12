---
id: REQ-052
title: Widget hinzufügen, konfigurieren und entfernen
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-07
  business_objects:
    - dashboard
  business_rules: []
  stakeholders:
    - SH-03
    - SH-07
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-052: Widget hinzufügen, konfigurieren und entfernen

## Aussage

Das System MUSS es dem Dashboard-Ersteller ermöglichen, einem bestehenden [Dashboard](../../business-objects/dashboard.md) Widgets hinzuzufügen, zu konfigurieren und zu entfernen. Es MÜSSEN vier Widget-Typen unterstützt werden: `kpi`, `chart`, `table`, `text`. Der Widget-Typ ist nach dem Anlegen unveränderlich; zum Wechseln des Typs muss das Widget gelöscht und neu angelegt werden. Alle Pflichtfelder eines Widget-Typs MÜSSEN bei der Erstellung vollständig befüllt sein; das System MUSS unvollständige Konfigurationen mit HTTP 422 ablehnen.

## Begründung

Widgets sind die fachlichen Bausteine eines Dashboards. Die Typen sind strukturell verschieden genug, dass sie separate Validierungsregeln benötigen, aber über eine gemeinsame API verwaltet werden können. Unveränderlichkeit des Typs vermeidet Migrations-Komplexität (z.B. DataSource-Format-Wechsel).

## Kontext

Widget-Konfiguration erfolgt in der **Client App**. Das **Web Portal** rendert Widgets read-only. DataSource-Details (PropertyAggregation, CatalogQuery) werden in REQ-053/REQ-054 spezifiziert.

## Typ-spezifische Felder

### API-Endpunkte

**Hinzufügen**:
- `POST /api/v1/dashboards/{dashboardId}/widgets`
- Request: Widget-Konfiguration (type + typspezifische Felder + gridPosition)
- Response 201: erstelltes Widget mit id
- Response 422: Validierungsfehler (fehlende Pflichtfelder, ungültige DataSource)
- Response 403: Requester ist nicht der Ersteller des Dashboards

**Konfigurieren (Update)**:
- `PUT /api/v1/dashboards/{dashboardId}/widgets/{widgetId}`
- widgetType darf NICHT geändert werden → 422 wenn abweichend

**Entfernen**:
- `DELETE /api/v1/dashboards/{dashboardId}/widgets/{widgetId}`
- Response 204; kein Block wenn Dashboard dadurch 0 Widgets hat (leeres Dashboard erlaubt)

### Widget-Typen und Pflichtfelder

| widgetType | Pflichtfelder | Optionale Felder |
|---|---|---|
| `kpi` | title, dataSource (PropertyAggregation) | unit, comparisonMode, targetValue, numberFormat |
| `chart` | title, chartType, dataSource, yAxis | xAxis (nicht bei pie/donut/treemap), colorScheme, showLegend, showDataLabels |
| `table` | title, dataSource (CatalogQuery) | pageSize, columnOverrides |
| `text` | title, content | – |

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | `widgetType` ist nach dem Anlegen unveränderlich | onUpdate |
| BR-02 | `chartType=pie` oder `chartType=donut` darf genau eine Y-Achse konfigurieren | onCreate, onUpdate |
| BR-03 | `kpi`-Widget muss `dataSource` vom Typ `PropertyAggregation` haben; `CatalogQuery` ist ungültig | onCreate, onUpdate |
| BR-04 | `table`-Widget muss `dataSource` vom Typ `CatalogQuery` haben; `PropertyAggregation` ist ungültig | onCreate, onUpdate |
| BR-05 | `kpi`-Widget mit `comparisonMode=vs-target` erfordert `targetValue` | onCreate, onUpdate |
| BR-06 | Nur der Dashboard-Ersteller oder ein Admin darf Widgets hinzufügen, ändern, entfernen | onCreate, onUpdate, onDelete |

## Akzeptanzkriterien

**AC1** (KPI-Widget anlegen):
- Wenn: `POST /api/v1/dashboards/{id}/widgets` mit type=kpi, title="Gesamtkosten", dataSource={PropertyAggregation, entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum}, gridPosition={col:1,row:1,width:3,height:1}
- Dann: HTTP 201; Widget in DB gespeichert; GET /api/v1/dashboards/{id} enthält das Widget

**AC2** (Chart-Widget, chartType=pie → kein xAxis erlaubt):
- Wenn: POST mit type=chart, chartType=pie, zwei yAxis-Einträge
- Dann: HTTP 422 „pie/donut erlaubt genau eine Y-Achse"

**AC3** (Type-Änderung abgelehnt):
- Gegeben: Widget type=kpi existiert
- Wenn: PUT mit widgetType=chart
- Dann: HTTP 422 „widgetType ist unveränderlich"

**AC4** (Ungültige DataSource für table):
- Wenn: POST mit type=table, dataSource=PropertyAggregation
- Dann: HTTP 422 „Table-Widget erfordert CatalogQuery DataSource"

**AC5** (Entfernen):
- Wenn: DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
- Dann: HTTP 204; Widget nicht mehr in GET /api/v1/dashboards/{id} enthalten

**AC6** (Fremdes Dashboard):
- Gegeben: Franz versucht, ein Widget auf Kurts Dashboard hinzuzufügen
- Wenn: POST ohne Berechtigung
- Dann: HTTP 403

## Abhängigkeiten

- Blockiert durch: REQ-051 (Dashboard muss existieren)
- Bezieht DataSource-Validierung aus: REQ-053 (PropertyAggregation), REQ-054 (CatalogQuery)
- Ermöglicht: REQ-055 (Daten werden aus den Widget-Konfigurationen berechnet), REQ-057 (GridPosition)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; alle 4 Widget-Typen, Typ-Unveränderlichkeit, BR-01–06 |
