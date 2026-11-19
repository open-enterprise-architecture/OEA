# US-057: Configure a Chart Widget (Bar, Pie, Other Types)

**ID**: US-057
**Story Points**: 8
**Status**: done
**Sprint**: S17
**Implementation**: OpenSpec change `us-056-kpi-and-chart-widgets` – chart widgets of the types bar, line, area, pie, donut and treemap (Apache ECharts) with the property aggregation, grouping by a property, the element type or the architecture layer, legend and data labels, at most 50 groups with "Other"; grouping by architecture domain and by the entity name is not offered (owner decision 2026-11-18: property, metatype and layer)

## Story

As a Lead Enterprise Architect, I want to add chart widgets (bar, line, pie, donut, area, treemap) to a dashboard and configure them with a PropertyAggregation DataSource including grouping – so that I can show the CIO distributions and comparisons (e.g. investment costs per plateau, applications per domain) as meaningful charts.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-configure-widget.md), [REQ-053](../req/REQ-053-property-aggregation-datasource.md), [REQ-055](../req/REQ-055-retrieve-dashboard-data.md), [REQ-057](../req/REQ-057-widget-grid-layout.md)

## Acceptance Criteria

**AC1** (bar chart: costs per plateau):

- Given: applications with the integer property "Investment" and the enum property "Plateau" with the values P1, P2 and P3, summing to P1=5M, P2=12M, P3=25M
- When: Kurt configures chart widget: chartType=bar, the element type, property "Investment", function=sum, groupBy = the property "Plateau"
- Then: Bar chart with 3 bars; X axis = the labels of the enum values; Y axis = amount; bar heights correct

**AC2** (pie chart: applications by property):

- Given: 20 applications, 12 with the enum property "Domain" = Finance, 8 = HR
- When: Chart widget: chartType=pie, the element type, function=count, groupBy = the property "Domain"
- Then: Pie chart with 2 segments (Finance 60%, HR 40%); labels show the names (not IDs)
- Note: grouping by architecture domains follows when domains exist (owner decision 2026-11-18)

**AC3** (pie chart: one series):

- A chart widget shows one series, so a pie with two Y axes cannot be configured; the contract has no axes

**AC4** (groupBy = architecture layer):

- Given: element types with layer assignment; 50 entities distributed across 3 layers
- When: Bar chart, function=count, groupBy = the architecture layer
- Then: 3 bars (Business, Application, Technology); labels from the layer names, translated in the language of the reader

**AC5** (legend and data labels):

- When: showLegend=true, showDataLabels=true configured
- Then: Chart renders legend and direct value labels on the chart elements

**AC6** (invalid groupBy reference):

- When: groupBy names a property that is not mapped to the element type, or of a data type other than enum, string, boolean or reference
- Then: HTTP 422 naming `groupBy`

## Technical Notes

- Chart types: bar, line, pie, donut, area, treemap (all the same widget configuration; only chartType changes)
- The axes follow from the grouping and the aggregated value; `xAxis` and `yAxis` are not configurable
- The kinds of `groupBy` are `property`, `metatype` and `layer`; the labels are names, never IDs (the client translates them with the label bundle)
- Chart library for the web portal: Apache ECharts (Apache-2.0, ADR-043), imported modularly

## Definition of Done

- [ ] AC1–AC6 met
- [ ] Tests: bar with groupBy, pie with architectureDomainId, pie-2-yAxis error, architectureLayerId, data labels/legend, invalid groupBy
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
