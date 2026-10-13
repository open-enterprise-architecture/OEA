# Derived from: requirements/req/req-126-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-21
Feature: REQ-126 – Enforcement of role-restricted property visibility

  When returning an entity, the entity API MUST replace all
  `PropertyDefinition` values with `visibilityMode = role-restricted` by `null`
  for users without one of the configured `allowedRoles`. The frontend MUST display a `null`-value field with `visibilityMode
  ≠ public` as **empty and not editable** — without any hint
  of the actual value.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – API response – user without allowed role retrieves entity with role-restricted property
    Given the precondition according to the requirement is met
    When  user without allowed role retrieves entity with role-restricted property
    Then  API response contains `"investmentCostForecast": null`; no actual value in the response

  @AC2
  Scenario: AC2 – UI empty and read-only – user without permission opens the entity detail form
    Given the precondition according to the requirement is met
    When  user without permission opens the entity detail form
    Then  restricted field is displayed empty; input is disabled; no tooltip with real value

  @AC3
  Scenario: AC3 – Write protection – user without permission tries to write the field directly via API...
    Given the precondition according to the requirement is met
    When  user without permission tries to write the field directly via API (PATCH)
    Then  API responds with HTTP 403; value is not changed

  @AC4
  Scenario: AC4 – Authorized user – user with one of the `allowedRoles` retrieves the same field
    Given the precondition according to the requirement is met
    When  user with one of the `allowedRoles` retrieves the same field
    Then  actual value is returned; field is editable

  @AC5
  Scenario: AC5 – Catalog consistency – catalog (UC-06) shows a column for a role-restricted property
    Given the precondition according to the requirement is met
    When  catalog (UC-06) shows a column for a role-restricted property
    Then  column shows empty cells for unauthorized users; sorting and filtering of this column are disabled for these users
