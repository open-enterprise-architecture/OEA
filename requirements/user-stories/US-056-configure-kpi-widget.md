# US-056: Configure a KPI Widget with PropertyAggregation

**ID**: US-056
**Story Points**: 5
**Status**: in-progress
**Sprint**: S17
**Implementation**: OpenSpec change `us-056-kpi-and-chart-widgets` – KPI widgets with the property aggregation (`count`, `sum`, `avg`, `min`, `max`), the unit, the comparison `vs-target`, the display "– (no data)" and the error `restricted` for properties the reader may not read for every entity; the comparison with the value 30 days ago (`vs-previous-value`, owner decision 2026-11-18) follows in the change `us-056-kpi-previous-value`, which completes the story

## Story

As a Lead Enterprise Architect, I want to add a KPI widget to a dashboard that displays an aggregated metric (e.g. sum of the investment costs of all plateaus) as a single prominent number – so that the CIO sees the most important metric at a glance.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052: Configure Widget](../req/REQ-052-configure-widget.md), [REQ-053: PropertyAggregation DataSource](../req/REQ-053-property-aggregation-datasource.md), [REQ-055: Retrieve Data](../req/REQ-055-retrieve-dashboard-data.md)

## Acceptance Criteria

**AC1** (create widget with sum):

- Given: the element type "Application Component" with the integer property "Investment" mapped; 3 applications with values 5,000,000, 12,000,000, 25,000,000 (and deleted ones, which are not counted)
- When: Kurt creates KPI widget: title="Total Investment Forecast", element type, property "Investment", function=sum, unit="CHF"
- Then: Widget saved; on data retrieval the widget shows "42,000,000 CHF"

**AC2** (count aggregation):

- Given: 15 applications in the repository
- When: Kurt creates KPI widget: the element type, function=count without a property, unit="Applications"
- Then: Widget shows "15 Applications"

**AC3** (invalid aggregation: varchar property with sum):

- When: Kurt selects a property of type text and function=sum
- Then: Error message "sum requires an integer property"; widget not saved

**AC4** (no data → null display):

- Given: no entity has a value of the property
- When: Retrieve data
- Then: Widget shows "– (no data)" instead of a number; no error status

**AC5** (comparisonMode=vs-target without targetValue):

- When: Kurt sets the comparison `vs-target` without a target value
- Then: HTTP 422 naming `targetValue`; the comparison `vs-previous-value` follows in the change `us-056-kpi-previous-value` and answers 422 until then

## Technical Notes

- Widget type: `kpi`; DataSource must be `property-aggregation` (CatalogQuery → 422 naming `dataSource`)
- Number display: the number format of the user interface language, up to two decimals (a configurable `numberFormat` is not implemented); the trend against the target is an icon plus text
- `value=null` on empty data: frontend renders "– (no data)" instead of 0; `count` over no entities is 0

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: sum, count, invalid aggregation, null data, missing targetValue
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
