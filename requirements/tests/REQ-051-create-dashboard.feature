# Derived from: requirements/req/req-051-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-051 – Create dashboard

  The system MUST enable authorized users to create a new
  [Dashboard](../../business-objects/dashboard.md) object with name,
  optional description, and scope (`instance` or `personal`).
  Dashboards with `scope=instance` MUST only be created by users who
  have the role "Enterprise Architect" or an equivalent dashboard
  write permission.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create with scope=instance – Kurt (role Enterprise Architect) is logged in
    Given Kurt (role Enterprise Architect) is logged in
    When  `POST /api/v1/dashboards` with name="IT Investment Planning" and scope=instance
    Then  HTTP 201; dashboard object in DB; visible to all users in GET /api/v1/dashboards

  @AC2
  Scenario: AC2 – Duplicate name – dashboard "IT Investment Planning" (scope=instance) already exists
    Given dashboard "IT Investment Planning" (scope=instance) already exists
    When  second POST with the same name
    Then  HTTP 409 with error message "Name already taken"

  @AC3
  Scenario: AC3 – Missing permission for scope=instance – Franz (role Junior Domain Arc...
    Given Franz (role Junior Domain Architect, no dashboard write permission) is logged in
    When  POST with scope=instance
    Then  HTTP 403; no dashboard created

  @AC4
  Scenario: AC4 – Personal dashboard for any user – Franz (without write permission) is logged in
    Given Franz (without write permission) is logged in
    When  POST with scope=personal, name="My Analysis"
    Then  HTTP 201; dashboard is visible only to Franz (not contained in GET /api/v1/dashboards for other users)

  @AC5
  Scenario: AC5 – Scope upgrade – Franz has a personal dashboard; later he receives the...
    Given Franz has a personal dashboard; later he receives the dashboard permission
    When  PUT with scope=instance
    Then  HTTP 200; dashboard is visible to everyone from now on
