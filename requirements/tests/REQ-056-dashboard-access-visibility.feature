# Derived from: requirements/req/req-056-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-056 – Dashboard access and visibility (scope logic)

  The system MUST enforce the visibility of
  [dashboards](../../business-objects/dashboard.md) based on the
  `scope` attribute: `scope=instance` dashboards MUST be visible and
  readable to all logged-in users of the instance; `scope=personal`
  dashboards MUST be visible exclusively to the creator. No user may
  read `scope=personal` dashboards of other users, not even via direct
  API access.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – instance dashboard: everyone sees it – Kurt has created a `scope=instance` dashboard
    Given Kurt has created a `scope=instance` dashboard
    When  Franz calls `GET /api/v1/dashboards`
    Then  Kurt's dashboard appears in the list

  @AC2
  Scenario: AC2 – personal dashboard: no data leak – Franz has created a `scope=personal` dashboard
    Given Franz has created a `scope=personal` dashboard
    When  Kurt calls `GET /api/v1/dashboards` or `GET /api/v1/dashboards/{franz-dashboard-id}`
    Then  Franz's dashboard neither appears in the list nor is it retrievable by ID (403)

  @AC3
  Scenario: AC3 – personal URL access blocked – Kurt has the ID of Franz's personal dashboard (e.g. guessed or from logs)
    Given Kurt has the ID of Franz's personal dashboard (e.g. guessed or from logs)
    When  `GET /api/v1/dashboards/{id}/data` is called directly
    Then  HTTP 403

  @AC4
  Scenario: AC4 – Scope upgrade – Franz has a personal dashboard; afterwards he receive...
    Given Franz has a personal dashboard; afterwards he receives dashboard write permission
    When  `PUT /api/v1/dashboards/{id}` with scope=instance
    Then  HTTP 200; dashboard is visible to everyone from now on

  @AC5
  Scenario: AC5 – Downgrade blocked when other users exist – Kurt has an instance dashboard; 5 other users have accessed it
    Given Kurt has an instance dashboard; 5 other users have accessed it
    When  Kurt tries PUT with scope=personal
    Then  HTTP 409 "Dashboard is used by other users; downgrade not possible"
