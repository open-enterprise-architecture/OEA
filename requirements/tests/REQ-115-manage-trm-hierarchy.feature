# Derived from: requirements/req/req-115-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-19
Feature: REQ-115 – Manage TRM category hierarchy

  The system MUST allow creating, renaming and deleting
  TRM categories with `scope=organization`. On creation,
  the `level` field MUST be set automatically to `parent.level + 1`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Automatic level – a subcategory is created under "Security Services"
    Given the precondition according to the requirement is met
    When  a subcategory is created under "Security Services"
    Then  the `level` field is automatically set to `parent.level + 1`

  @AC2
  Scenario: AC2 – Delete with child nodes – a category with 3 child nodes is to be deleted
    Given the precondition according to the requirement is met
    When  a category with 3 child nodes is to be deleted
    Then  the system responds with HTTP 422 "Child nodes must be removed first"

  @AC3
  Scenario: AC3 – Drag & drop cycle protection – a category is dragged via drag & drop under one of its own descendants...
    Given the precondition according to the requirement is met
    When  a category is dragged via drag & drop under one of its own descendants
    Then  the drop is visually blocked; an API attempt responds with HTTP 422
