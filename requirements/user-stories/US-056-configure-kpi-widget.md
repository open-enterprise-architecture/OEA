# US-056: Configure a KPI Widget with PropertyAggregation

**ID**: US-056
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to add a KPI widget to a dashboard that displays an aggregated metric (e.g. sum of the investment costs of all plateaus) as a single prominent number – so that the CIO sees the most important metric at a glance.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052: Configure Widget](../req/REQ-052-configure-widget.md), [REQ-053: PropertyAggregation DataSource](../req/REQ-053-property-aggregation-datasource.md), [REQ-055: Retrieve Data](../req/REQ-055-retrieve-dashboard-data.md)

## Acceptance Criteria

**AC1** (create widget with sum):
- Given: MetamodelConfiguration contains EntityType `plateau` with PropertyDefinition `investitionskostenPrognose` (kind=int); 3 plateau instances with values 5,000,000, 12,000,000, 25,000,000
- When: Kurt creates KPI widget: title="Total Investment Forecast", entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum, unit="CHF"
- Then: Widget saved; on data retrieval the widget shows "42,000,000 CHF"

**AC2** (count aggregation):
- Given: 15 plateau instances in the repository
- When: Kurt creates KPI widget: propertyName=id, aggregationFn=count, unit="Plateaus"
- Then: Widget shows "15 Plateaus"

**AC3** (invalid aggregation: varchar property with sum):
- When: Kurt selects propertyName=name (kind=varchar), aggregationFn=sum
- Then: Error message "sum requires an integer property"; widget not saved

**AC4** (no data → null display):
- Given: EntityType `plateau` has no instances with a populated property
- When: Retrieve data
- Then: Widget shows "– (no data)" instead of a number; no error status

**AC5** (comparisonMode=vs-target without targetValue):
- When: Kurt sets comparisonMode=vs-target without targetValue
- Then: Error "targetValue is mandatory for comparisonMode=vs-target"

## Technical Notes

- Widget type: `kpi`; DataSource must be `PropertyAggregation` (CatalogQuery → 422)
- Number display: numberFormat from widget configuration; default: locale-sensitive format without decimal places
- `value=null` on empty data: frontend renders "–" instead of 0

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: sum, count, invalid aggregation, null data, missing targetValue
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
