---
id: REQ-055
title: Calculate and retrieve dashboard data live
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
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-055: Calculate and retrieve dashboard data live

## Statement

The system MUST, on request, calculate the current data for all widgets of a [dashboard](../../business-objects/dashboard.md) and return it collectively. The calculation MUST reflect the current state of the architecture repository (no cache). If the data calculation of a single widget fails, this MUST NOT block the calculation of the remaining widgets; the faulty widget returns an isolated error status, all other widgets return normal data.

## Rationale

Live calculation ensures that C-level dashboards always show the current repository state. Widget error isolation prevents a defective widget (e.g., due to a metamodel change) from making the entire dashboard unusable.

## Context

`text` widgets have no DataSource and return no calculated data; they appear in the response with `status=static`. The dashboard's `globalFilters` are combined with all PropertyAggregation DataSources using AND; CatalogQuery DataSources ignore `globalFilters` (they use their own catalog filters plus the widget's `additionalFilters` instead).

## Type-Specific Fields

### API Endpoint

`GET /api/v1/dashboards/{id}/data`

**Response structure (HTTP 200)**:

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
      "error": "PropertyDefinition 'investmentCostForecast' not found on EntityType 'plateau'"
    }
  }
}
```

### Processing Order per Widget

1. Determine the DataSource type
2. **PropertyAggregation**: apply globalFilters AND widget.filters; execute aggregation on the entity store; break down by groupBy
3. **CatalogQuery**: call the catalog query API (REQ-046) with widget.savedViewId and widget.additionalFilters; pageSize from widget configuration
4. **text**: no API call; return `status=static`
5. On an exception in a widget: `status=error` with error message; continue with the next widget

## Business Rules

| Rule-ID | Statement | Trigger |
|---|---|---|
| BR-01 | Each widget is calculated independently; an error in widget N does not block widget N+1 | onQuery |
| BR-02 | The dashboard's `globalFilters` are applied to PropertyAggregation DataSources; CatalogQuery widgets ignore `globalFilters` | onQuery |
| BR-03 | No caching layer in v1.0; every call calculates live data | onQuery |
| BR-04 | `text` widgets always return `status=static`; no database access | onQuery |

## Acceptance Criteria

**AC1** (Successful dashboard with mixed widgets):

- Given: Dashboard with KPI, chart and table widget (all valid DataSources)
- When: GET /api/v1/dashboards/{id}/data
- Then: HTTP 200; all 3 widgets with `status=ok`; `calculatedAt` contains the current timestamp

**AC2** (Error isolation):

- Given: Dashboard with 3 widgets; widget 2 has an invalid PropertyDefinition (metamodel changed)
- When: GET /api/v1/dashboards/{id}/data
- Then: Widgets 1 and 3 → `status=ok`; widget 2 → `status=error` with error message; HTTP 200 (not 500)

**AC3** (KPI: value = null for empty data):

- Given: PropertyAggregation on an EntityType without instances
- When: Retrieve data
- Then: `{ "status": "ok", "data": { "value": null } }`; no error

**AC4** (Apply globalFilter):

- Given: Dashboard with globalFilter `status eq 'active'`; KPI widget aggregates over `plateau`
- When: GET /api/v1/dashboards/{id}/data
- Then: Only plateaus with status=active are included in the aggregation

**AC5** (Text widget):

- Given: Dashboard has a text widget
- When: Retrieve data
- Then: `{ "status": "static", "type": "text" }`; no DB call for this widget

**AC6** (Live data):

- Given: Plateau instance receives a new value for investmentCostForecast
- When: GET /api/v1/dashboards/{id}/data immediately afterwards
- Then: KPI value reflects the new value (no stale cache)

## Dependencies

- Blocked by: REQ-051 (dashboard), REQ-052 (widgets), REQ-053 (PropertyAggregation), REQ-054 (CatalogQuery)
- Uses internally: REQ-046 (catalog query API)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | Requirements Engineer | Initial draft; widget error isolation, globalFilters, live calculation, response schema |
