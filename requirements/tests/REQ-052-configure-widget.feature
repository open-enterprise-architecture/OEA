# Derived from: requirements/req/req-052-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-052 – Add, configure, and remove widgets

  The system MUST enable the dashboard creator to add, configure, and
  remove widgets on an existing
  [Dashboard](../../business-objects/dashboard.md). Four widget types
  MUST be supported: `kpi`, `chart`, `table`, `text`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create KPI widget – `POST /api/v1/dashboards/{id}/widgets` with type=...
    Given the precondition according to the requirement is met
    When  `POST /api/v1/dashboards/{id}/widgets` with type=kpi, title="Total costs", dataSource={PropertyAggregation, entityType=plateau, propertyName=investitionskostenPrognose, aggregationFn=sum}, gridPosition={col:1,row:1,width:3,height:1}
    Then  HTTP 201; widget saved in DB; GET /api/v1/dashboards/{id} contains the widget

  @AC2
  Scenario: AC2 – Chart widget, chartType=pie → no xAxis allowed – POST with type=chart, chartType=pie, two yAxis entries
    Given the precondition according to the requirement is met
    When  POST with type=chart, chartType=pie, two yAxis entries
    Then  HTTP 422 "pie/donut allows exactly one Y axis"

  @AC3
  Scenario: AC3 – Type change rejected – widget type=kpi exists
    Given widget type=kpi exists
    When  PUT with widgetType=chart
    Then  HTTP 422 "widgetType is immutable"

  @AC4
  Scenario: AC4 – Invalid DataSource for table – POST with type=table, dataSource=PropertyAggregation
    Given the precondition according to the requirement is met
    When  POST with type=table, dataSource=PropertyAggregation
    Then  HTTP 422 "Table widget requires CatalogQuery DataSource"

  @AC5
  Scenario: AC5 – Remove – DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
    Given the precondition according to the requirement is met
    When  DELETE /api/v1/dashboards/{id}/widgets/{widgetId}
    Then  HTTP 204; widget no longer contained in GET /api/v1/dashboards/{id}

  @AC6
  Scenario: AC6 – Another user's dashboard – Franz tries to add a widget to Kurt's dashboard
    Given Franz tries to add a widget to Kurt's dashboard
    When  POST without permission
    Then  HTTP 403
