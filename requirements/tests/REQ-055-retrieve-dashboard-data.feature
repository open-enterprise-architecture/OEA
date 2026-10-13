# Derived from: requirements/req/req-055-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-055 – Compute and retrieve dashboard data live

  On demand, the system MUST compute the current data for all widgets
  of a [dashboard](../../business-objects/dashboard.md) and return it
  collectively. The computation MUST reflect the current state of the
  architecture repository (no cache).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Successful dashboard with mixed widgets – dashboard with KPI, chart, and table widget (all valid DataSources)
    Given dashboard with KPI, chart, and table widget (all valid DataSources)
    When  GET /api/v1/dashboards/{id}/data
    Then  HTTP 200; all 3 widgets with `status=ok`; `calculatedAt` contains current timestamp

  @AC2
  Scenario: AC2 – Error isolation – dashboard with 3 widgets; widget 2 has an invalid P...
    Given dashboard with 3 widgets; widget 2 has an invalid PropertyDefinition (metamodel changed)
    When  GET /api/v1/dashboards/{id}/data
    Then  widgets 1 and 3 → `status=ok`; widget 2 → `status=error` with error message; HTTP 200 (no 500)

  @AC3
  Scenario: AC3 – KPI: value = null on empty data – PropertyAggregation on EntityType without instances
    Given PropertyAggregation on EntityType without instances
    When  retrieve data
    Then  `{ "status": "ok", "data": { "value": null } }`; no error

  @AC4
  Scenario: AC4 – Apply globalFilter – dashboard with globalFilter `status eq 'active'`...
    Given dashboard with globalFilter `status eq 'active'`; KPI widget aggregates over `plateau`
    When  GET /api/v1/dashboards/{id}/data
    Then  only plateaus with status=active are included in the aggregation

  @AC5
  Scenario: AC5 – Text widget – dashboard has a text widget
    Given dashboard has a text widget
    When  retrieve data
    Then  `{ "status": "static", "type": "text" }`; no DB call for this widget

  @AC6
  Scenario: AC6 – Live data – plateau instance receives new value for investmentCostForecast
    Given plateau instance receives new value for investmentCostForecast
    When  GET /api/v1/dashboards/{id}/data immediately afterwards
    Then  KPI value reflects the new value (no stale cache)
