---
id: REQ-055
title: Dashboard-Daten live berechnen und abrufen
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
    - catalog
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-01
    - SH-02
    - SH-03
    - SH-04
    - SH-05
    - SH-06
    - SH-07
  concept:
    - concept/70-platform/21-api-architektur.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-055: Dashboard-Daten live berechnen und abrufen

## Aussage

Das System MUSS auf Abruf die aktuellen Daten für alle Widgets eines [Dashboards](../../business-objects/dashboard.md) berechnen und gesammelt zurückliefern. Die Berechnung MUSS den aktuellen Stand des Architecture-Repositorys widerspiegeln (kein Cache). Scheitert die Daten-Berechnung eines einzelnen Widgets, DARF das NICHT die Berechnung der übrigen Widgets blockieren; das fehlerhaften Widget liefert einen isolierten Fehler-Status, alle anderen Widgets liefern normale Daten.

## Begründung

Live-Berechnung stellt sicher, dass C-Level-Dashboards immer den aktuellen Repository-Stand zeigen. Widget-Fehler-Isolation verhindert, dass ein defektes Widget (z.B. durch Metamodell-Änderung) das ganze Dashboard unbrauchbar macht.

## Kontext

`text`-Widgets haben keine DataSource und liefern keine berechneten Daten; sie erscheinen im Response mit `status=static`. `globalFilters` des Dashboards werden auf alle PropertyAggregation-DataSources AND-verknüpft; CatalogQuery-DataSources ignorieren `globalFilters` (verwenden stattdessen ihre eigenen Katalog-Filter plus Widget-`additionalFilters`).

## Typ-spezifische Felder

### API-Endpunkt

`GET /api/v1/dashboards/{id}/data`

**Response-Struktur (HTTP 200)**:

```json
{
  "dashboardId": "uuid",
  "calculatedAt": "2026-06-26T10:00:00Z",
  "widgets": {
    "<widgetId-kpi>": {
      "status": "ok",
      "type": "kpi",
      "data": { "value": 42500000, "unit": "Mio. CHF" }
    },
    "<widgetId-chart>": {
      "status": "ok",
      "type": "chart",
      "data": {
        "series": [
          { "group": "Plateau 2026", "value": 12000000 },
          { "group": "Plateau 2027", "value": 30500000 }
        ]
      }
    },
    "<widgetId-table>": {
      "status": "ok",
      "type": "table",
      "data": {
        "totalCount": 47,
        "page": 0,
        "pageSize": 10,
        "columns": ["name", "status"],
        "rows": [...]
      }
    },
    "<widgetId-text>": {
      "status": "static",
      "type": "text"
    },
    "<widgetId-broken>": {
      "status": "error",
      "type": "kpi",
      "error": "PropertyDefinition 'investitionskostenPrognose' nicht auf EntityType 'plateau' gefunden"
    }
  }
}
```

### Verarbeitungsreihenfolge pro Widget

1. DataSource-Typ bestimmen
2. **PropertyAggregation**: globalFilters AND widget.filters anwenden; Aggregation auf Entity-Store ausführen; groupBy aufschlüsseln
3. **CatalogQuery**: Katalog-Abfrage-API (REQ-046) mit widget.savedViewId und widget.additionalFilters aufrufen; pageSize aus Widget-Konfiguration
4. **text**: kein API-Aufruf; `status=static` zurückgeben
5. Bei Exception in einem Widget: `status=error` mit Fehlermeldung; nächstes Widget fortfahren

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | Jedes Widget wird unabhängig berechnet; Fehler in Widget N blockiert Widget N+1 nicht | onQuery |
| BR-02 | `globalFilters` des Dashboards werden auf PropertyAggregation-DataSources angewendet; CatalogQuery-Widgets ignorieren `globalFilters` | onQuery |
| BR-03 | Keine Caching-Schicht in v1.0; jeder Aufruf berechnet Live-Daten | onQuery |
| BR-04 | `text`-Widgets liefern immer `status=static`; kein Datenbankzugriff | onQuery |

## Akzeptanzkriterien

**AC1** (Erfolgreiches Dashboard mit gemischten Widgets):
- Gegeben: Dashboard mit KPI-, Chart- und Table-Widget (alle gültige DataSources)
- Wenn: GET /api/v1/dashboards/{id}/data
- Dann: HTTP 200; alle 3 Widgets mit `status=ok`; `calculatedAt` enthält aktuellen Timestamp

**AC2** (Fehler-Isolation):
- Gegeben: Dashboard mit 3 Widgets; Widget 2 hat ungültige PropertyDefinition (Metamodell geändert)
- Wenn: GET /api/v1/dashboards/{id}/data
- Dann: Widget 1 und 3 → `status=ok`; Widget 2 → `status=error` mit Fehlermeldung; HTTP 200 (kein 500)

**AC3** (KPI: Wert = null bei leeren Daten):
- Gegeben: PropertyAggregation auf EntityType ohne Instanzen
- Wenn: Daten abrufen
- Dann: `{ "status": "ok", "data": { "value": null } }`; kein Fehler

**AC4** (globalFilter anwenden):
- Gegeben: Dashboard mit globalFilter `status eq 'active'`; KPI-Widget aggregiert über `plateau`
- Wenn: GET /api/v1/dashboards/{id}/data
- Dann: Nur Plateaus mit status=active fliessen in die Aggregation ein

**AC5** (Text-Widget):
- Gegeben: Dashboard hat ein text-Widget
- Wenn: Daten abrufen
- Dann: `{ "status": "static", "type": "text" }`; kein DB-Aufruf für dieses Widget

**AC6** (Live-Daten):
- Gegeben: Plateau-Instanz erhält neuen Wert für investitionskostenPrognose
- Wenn: GET /api/v1/dashboards/{id}/data unmittelbar danach
- Dann: KPI-Wert spiegelt den neuen Wert wider (kein veralteter Cache)

## Abhängigkeiten

- Blockiert durch: REQ-051 (Dashboard), REQ-052 (Widgets), REQ-053 (PropertyAggregation), REQ-054 (CatalogQuery)
- Nutzt intern: REQ-046 (Katalog-Abfrage-API)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; Widget-Fehler-Isolation, globalFilters, Live-Berechnung, Response-Schema |
