# Spec: openspec portal/portal-dashboards
# Source material: requirements/user-stories/US-061-show-dashboard-in-web-portal.md, US-060 AC5; REQ-055, REQ-056; ADR-032

@US-061 @UC-07 @portal @portal-dashboards
Feature: Dashboards in the portal

  Readers of the web portal see the shared dashboards and their own
  personal dashboards read-only, with the same widget data as in the
  work area. Personal dashboards of other persons are not found.
  The administrator and the service account are tested in
  PortalDashboardsTests.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP
    And the portal reader and a person without role are logged in through the IdP

  @AC1 @REQ-056
  Scenario: Shared dashboards for a portal reader
    Given the enterprise architect has created the dashboard "Portal IT Investment Planning" with scope "instance"
    And the enterprise architect has created the dashboard "Portal My analysis" with scope "personal"
    When the portal reader lists the portal dashboards
    Then the portal dashboard list contains "Portal IT Investment Planning" and not "Portal My analysis"
    And no portal dashboard is editable

  @AC1 @REQ-056
  Scenario: Own personal dashboard
    Given the enterprise architect has created the dashboard "Portal Own analysis" with scope "personal"
    When the enterprise architect lists the portal dashboards
    Then the portal dashboard list contains "Portal Own analysis" with scope "personal"
    And no portal dashboard is editable

  @AC1 @AC5 @US-060
  Scenario: Configuration of a shared dashboard
    Given the enterprise architect has created the dashboard "Portal Layout" with scope "instance"
    And the enterprise architect adds the text widget "Left" with the content "x" to the dashboard "Portal Layout"
    And the enterprise architect adds the text widget "Right" with the content "y" to the dashboard "Portal Layout"
    And the enterprise architect places the widget "Right" of the dashboard "Portal Layout" at column 5, row 2, width 8 and height 2
    When the portal reader reads the portal dashboard "Portal Layout"
    Then the portal dashboard has 2 widgets and is not editable
    And the portal widget "Right" is at column 5, row 2, width 8 and height 2

  @AC5
  Scenario: Manager reads without edit rights
    Given the enterprise architect has created the dashboard "Portal Managed" with scope "instance"
    When the enterprise architect reads the portal dashboard "Portal Managed"
    Then the portal dashboard has 0 widgets and is not editable

  @AC6 @REQ-056
  Scenario: Another person's personal dashboard
    Given the enterprise architect has created the dashboard "Portal Private analysis" with scope "personal"
    And the enterprise architect adds the text widget "Secret" with the content "x" to the dashboard "Portal Private analysis"
    When the portal reader reads the portal dashboard "Portal Private analysis"
    Then the portal request is answered with 404
    When the portal reader reads the data of the portal dashboard "Portal Private analysis"
    Then the portal request is answered with 404
    When the portal reader reads page 1 of the widget "Secret" of the portal dashboard "Portal Private analysis"
    Then the portal request is answered with 404

  Scenario: Unknown dashboard
    When the portal reader reads the data of an unknown portal dashboard
    Then the portal request is answered with 404

  Scenario: Without portal permission
    Given the enterprise architect has created the dashboard "Portal Denied" with scope "instance"
    When the person without role reads the portal dashboard "Portal Denied"
    Then the portal request is answered with 403
    When the person without role lists the portal dashboards
    Then the portal request is answered with 403

  @AC1 @REQ-055 @dashboard-aggregation
  Scenario: KPI and chart in the portal
    Given the element type "Portal Sum Application"
    And the property "Portal Sum Investment" of type "integer" is mapped to the element type "Portal Sum Application"
    And the entity "Portal Sum 1" of the element type "Portal Sum Application" with the "Portal Sum Investment" "5000000"
    And the entity "Portal Sum 2" of the element type "Portal Sum Application" with the "Portal Sum Investment" "12000000"
    And the enterprise architect has created the dashboard "Portal Sum" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Portal Sum":
      | type | title            | metatype                | property               | function | unit |
      | kpi  | Total Investment | Portal Sum Application  | Portal Sum Investment  | sum      | CHF  |
    Then the dashboard request is accepted
    When the enterprise architect adds this widget to the dashboard "Portal Sum":
      | type  | title      | metatype               | function | groupBy   | chartType |
      | chart | Per Type   | Portal Sum Application | count    | metatype  | pie       |
    Then the dashboard request is accepted
    When the portal reader reads the data of the portal dashboard "Portal Sum"
    Then the portal widget "Total Investment" has the value 17000000
    And the portal widget "Per Type" has the status "ok"

  @AC4 @REQ-056 @dashboard-aggregation
  Scenario: Restricted property
    Given the element type "Portal Restricted Application"
    And the property "Portal Restricted Investment" of type "integer" is mapped to the element type "Portal Restricted Application"
    And the aggregated property "Portal Restricted Investment" is readable only by the role "enterprise-architect"
    And the entity "Portal Restricted 1" of the element type "Portal Restricted Application" with the "Portal Restricted Investment" "100"
    And the enterprise architect has created the dashboard "Portal Restricted Sum" with scope "instance"
    When the enterprise architect adds this widget to the dashboard "Portal Restricted Sum":
      | type | title      | metatype                       | property                       | function |
      | kpi  | Investment | Portal Restricted Application  | Portal Restricted Investment   | sum      |
    Then the dashboard request is accepted
    When the portal reader reads the data of the portal dashboard "Portal Restricted Sum"
    Then the portal widget "Investment" shows the error "restricted"
    When the enterprise architect reads the data of the portal dashboard "Portal Restricted Sum"
    Then the portal widget "Investment" has the value 100

  @AC2 @REQ-055
  Scenario: Failing widget
    Given the enterprise architect has created the catalog "Portal Doomed Inventory" with scope "instance"
    And the enterprise architect has created the dashboard "Portal Failing" with scope "instance"
    And the enterprise architect adds the text widget "Still Fine" with the content "x" to the dashboard "Portal Failing"
    And the enterprise architect adds the table widget "Doomed" for the catalog "Portal Doomed Inventory" to the dashboard "Portal Failing"
    And the catalog "Portal Doomed Inventory" is deleted
    When the portal reader reads the data of the portal dashboard "Portal Failing"
    Then the portal widget "Doomed" shows the error "catalog-not-found"
    And the portal widget "Still Fine" has the status "static"

  @AC1 @REQ-055 @dashboard-entities
  Scenario: Pages of a table widget
    Given the repository contains no applications
    And the shared catalog "Portal Dashboard Paged Inventory" has 47 entities and the saved view "Compact view"
    And the enterprise architect has created the dashboard "Portal Pages" with scope "instance"
    And the enterprise architect adds the table widget "Paged Overview" for the catalog "Portal Dashboard Paged Inventory" with 10 rows per page to the dashboard "Portal Pages"
    When the portal reader reads page 1 of the widget "Paged Overview" of the portal dashboard "Portal Pages"
    Then the portal widget shows the entities "Dash App 01" to "Dash App 10", page 1 of 5
    When the portal reader reads page 2 of the widget "Paged Overview" of the portal dashboard "Portal Pages"
    Then the portal widget shows the entities "Dash App 11" to "Dash App 20", page 2 of 5
    When the portal reader reads page 0 of the widget "Paged Overview" of the portal dashboard "Portal Pages"
    Then the portal request is answered with 400
