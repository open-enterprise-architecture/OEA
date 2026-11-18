# Spec: openspec dashboard/dashboards
# Source material: requirements/user-stories/US-055-create-dashboard.md, US-062-personal-dashboard.md; REQ-051, REQ-056; ADR-032

@US-055 @US-062 @UC-07 @dashboard
Feature: Dashboards

  Dashboards have a name, a description and a scope. Shared dashboards
  need dashboard:manage; personal dashboards are visible to their owner
  only. Every change needs the version that was read.

  Background:
    Given an instance with the starter package and catalog users logged in through the IdP

  @US-055 @AC1 @REQ-051
  Scenario: Shared dashboard
    When the enterprise architect creates the dashboard "Dash IT Investment Planning 2026-2030" with scope "instance"
    Then the dashboard is created without widgets and with the scope "instance"
    And the dashboard overview of the viewer lists "Dash IT Investment Planning 2026-2030"
    And the viewer receives 200 for the dashboard "Dash IT Investment Planning 2026-2030"
    And the audit records "dashboard.created" for the dashboard "Dash IT Investment Planning 2026-2030"

  @US-055 @AC2 @REQ-051
  Scenario: Name already taken
    Given the enterprise architect has created the dashboard "Dash Taken Name" with scope "instance"
    When the enterprise architect creates the dashboard "dash taken name" with scope "instance"
    Then the dashboard request is rejected with 422 "dashboard-name-taken" naming "name"
    And exactly one dashboard is named "Dash Taken Name" ignoring case

  @US-055 @US-062 @AC1
  Scenario: Personal dashboard without dashboard:manage
    When the viewer creates the dashboard "Dash My analysis" with scope "personal"
    Then the dashboard is created without widgets and with the scope "personal"

  @US-055 @AC3
  Scenario: Shared dashboard without dashboard:manage
    When the viewer creates the dashboard "Dash Not Allowed" with scope "instance"
    Then the dashboard request is rejected with 403
    And no dashboard is named "Dash Not Allowed"

  @REQ-051
  Scenario Outline: Name at the limits
    When the enterprise architect creates a dashboard with a name of <length> characters
    Then <outcome>

    Examples:
      | length | outcome                                           |
      | 200    | the dashboard is created                          |
      | 201    | the dashboard request is rejected with 422 naming "name"    |

  @REQ-051
  Scenario: Blank name
    When the enterprise architect creates a dashboard named with only blanks
    Then the dashboard request is rejected with 422 naming "name"

  @REQ-051
  Scenario Outline: Description at the limits
    When the enterprise architect creates a dashboard with a description of <length> characters
    Then <outcome>

    Examples:
      | length | outcome                                                 |
      | 1000   | the dashboard is created                                |
      | 1001   | the dashboard request is rejected with 422 naming "description"   |

  @US-062 @AC2 @AC3 @REQ-056
  Scenario: Another person's personal dashboard
    Given the viewer has created the dashboard "Dash Private Analysis" with scope "personal"
    Then the dashboard overview of the viewer lists "Dash Private Analysis"
    And the dashboard overview of the enterprise architect does not list "Dash Private Analysis"
    And the enterprise architect receives 404 for the dashboard "Dash Private Analysis"

  @US-062 @AC4
  Scenario: Scope upgrade with permission
    Given the enterprise architect has created the dashboard "Dash Upgrade" with scope "personal"
    When the enterprise architect changes the scope of the dashboard "Dash Upgrade" to "instance"
    Then the dashboard request is accepted
    And the dashboard overview of the viewer lists "Dash Upgrade"

  @US-062 @AC5
  Scenario: Scope upgrade without permission
    Given the viewer has created the dashboard "Dash No Upgrade" with scope "personal"
    When the viewer changes the scope of the dashboard "Dash No Upgrade" to "instance"
    Then the dashboard request is rejected with 403
    And the dashboard "Dash No Upgrade" has the scope "personal"

  @US-062 @AC4
  Scenario: Scope upgrade with a personal catalog
    Given the enterprise architect has created the personal catalog "Dash Personal Catalog Upgrade"
    And the enterprise architect has created the dashboard "Dash Personal Upgrade" with scope "personal"
    And the enterprise architect adds the table widget "Mine" for the catalog "Dash Personal Catalog Upgrade" to the dashboard "Dash Personal Upgrade"
    When the enterprise architect changes the scope of the dashboard "Dash Personal Upgrade" to "instance"
    Then the dashboard request is rejected with 422 naming "scope"
    And the dashboard "Dash Personal Upgrade" has the scope "personal"

  @US-062
  Scenario: Shared to personal
    Given the enterprise architect has created the dashboard "Dash Stay Shared" with scope "instance"
    When the enterprise architect changes the scope of the dashboard "Dash Stay Shared" to "personal"
    Then the dashboard request is rejected with 422 naming "scope"
    And the dashboard "Dash Stay Shared" has the scope "instance"

  @US-055 @REQ-051
  Scenario: Shared dashboard cannot be changed without dashboard:manage
    Given the enterprise architect has created the dashboard "Dash Read Only" with scope "instance"
    When the viewer renames the dashboard "Dash Read Only" to "Dash Renamed"
    Then the dashboard request is rejected with 403
    And no dashboard is named "Dash Renamed"

  @REQ-051
  Scenario: Rename a dashboard
    Given the enterprise architect has created the dashboard "Dash Before" with scope "instance"
    When the enterprise architect renames the dashboard "Dash Before" to "Dash After"
    Then the dashboard request is accepted
    And the audit records "dashboard.updated" for the dashboard "Dash After" with the changed field "name"

  @US-055
  Scenario: Delete a dashboard
    Given the enterprise architect has created the dashboard "Dash To Delete" with scope "instance"
    And the dashboard "Dash To Delete" has 2 widgets
    When the enterprise architect deletes the dashboard "Dash To Delete"
    Then the dashboard "Dash To Delete" and its widgets are gone

  @REQ-052
  Scenario: Stale version
    Given the enterprise architect has created the dashboard "Dash Versions" with scope "instance"
    And the dashboard "Dash Versions" has the version 3
    When the enterprise architect adds a widget to the dashboard "Dash Versions" with the version 2 and then without a version
    Then the first dashboard request is rejected with 412 and the second with 428
