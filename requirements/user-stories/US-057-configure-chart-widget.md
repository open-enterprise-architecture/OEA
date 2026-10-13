# US-057: Configure a Chart Widget (Bar, Pie, Other Types)

**ID**: US-057
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to add chart widgets (bar, line, pie, donut, area, treemap) to a dashboard and configure them with a PropertyAggregation DataSource including grouping – so that I can show the CIO distributions and comparisons (e.g. investment costs per plateau, applications per domain) as meaningful charts.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-configure-widget.md), [REQ-053](../req/REQ-053-property-aggregation-datasource.md), [REQ-055](../req/REQ-055-retrieve-dashboard-data.md), [REQ-057](../req/REQ-057-widget-grid-layout.md)

## Acceptance Criteria

**AC1** (bar chart: costs per plateau):
- Given: 3 plateau instances with investmentCostForecast: P1=5M, P2=12M, P3=25M
- When: Kurt configures chart widget: chartType=bar, entityType=plateau, propertyName=investmentCostForecast, aggregationFn=sum, groupBy=name
- Then: Bar chart with 3 bars; X axis = plateau name; Y axis = amount; bar heights correct

**AC2** (pie chart: applications by domain):
- Given: 20 ApplicationComponents, 12 domain=finance, 8 domain=hr
- When: Chart widget: chartType=pie, entityType=application-component, propertyName=id, aggregationFn=count, groupBy=architectureDomainId
- Then: Pie chart with 2 segments (Finance 60%, HR 40%); labels show domain names (not IDs)

**AC3** (pie chart: two yAxis rejected):
- When: chartType=pie configured with two yAxis entries
- Then: HTTP 422 "pie allows exactly one Y axis"

**AC4** (groupBy=architectureLayerId):
- Given: EntityTypes with layer assignment; 50 entities distributed across 3 layers
- When: Bar chart, aggregationFn=count, groupBy=architectureLayerId
- Then: 3 bars (Business, Application, Technology); labels from ArchitectureLayerDefinition.name

**AC5** (legend and data labels):
- When: showLegend=true, showDataLabels=true configured
- Then: Chart renders legend and direct value labels on the chart elements

**AC6** (invalid groupBy reference):
- When: groupBy=nonExistentProperty
- Then: HTTP 422 "groupBy 'nonExistentProperty' is not a valid PropertyDefinition name on EntityType 'application-component' and not a system value"

## Technical Notes

- Chart types: bar, line, pie, donut, area, treemap (all the same widget configuration; only chartType changes)
- xAxis is optional for pie/donut/treemap; for bar/line/area xAxis is mandatory if groupBy is set
- groupBy system values `architectureDomainId` and `architectureLayerId` resolve name labels from the associated definitions (not raw IDs)
- Chart library for the web portal: not yet decided (no ADR needed; implementation decision)

## Definition of Done

- [ ] AC1–AC6 met
- [ ] Tests: bar with groupBy, pie with architectureDomainId, pie-2-yAxis error, architectureLayerId, data labels/legend, invalid groupBy
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
