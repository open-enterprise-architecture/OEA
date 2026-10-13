# US-058: Configure a Table Widget with Catalog DataSource

**ID**: US-058
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to add a table widget to a dashboard that embeds the rows of an existing catalog directly in the dashboard – so that in addition to charts I can also show detail tables (e.g. top applications by cost) on the same dashboard.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-052](../req/REQ-052-configure-widget.md), [REQ-054](../req/REQ-054-catalog-query-datasource.md), [REQ-055](../req/REQ-055-retrieve-dashboard-data.md)

## Acceptance Criteria

**AC1** (create widget with catalog):
- Given: Catalog "Application Inventory" (scope=instance) exists; SavedView "Compact view" present
- When: Kurt creates table widget: title="Application Overview", catalogId=<id>, savedViewId=<view-id>, pageSize=10
- Then: Widget saved; on data retrieval the table shows the first 10 rows according to the SavedView configuration

**AC2** (without SavedView → default view of the catalog):
- When: Table widget configured without savedViewId
- Then: Catalog query uses the default state of the catalog (no SavedView)

**AC3** (catalog not visible):
- Given: Franz has a scope=personal catalog
- When: Kurt tries to use this catalog as DataSource
- Then: HTTP 403; widget not saved

**AC4** (catalog deleted later: error isolation):
- Given: Table widget references catalog "Inventory"; catalog is deleted afterwards
- When: Dashboard data retrieved
- Then: Table widget shows error notice "Catalog not found"; other widgets render normally

**AC5** (PropertyAggregation for table → rejected):
- When: Table widget configured with DataSource=PropertyAggregation
- Then: HTTP 422 "Table widget requires CatalogQuery DataSource"

## Technical Notes

- Widget type: `table`; only CatalogQuery allowed as DataSource
- Table pagination: frontend renders pagination control based on `totalCount` and `pageSize`
- Column overrides: optional title renaming or width override in the dashboard context (without changing the catalog)

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: with SavedView, without SavedView, foreign catalog, deleted catalog, wrong DataSource
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
