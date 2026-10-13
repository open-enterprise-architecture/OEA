# Derived from: requirements/req/req-057-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-07
Feature: REQ-057 – Configure widget grid layout (GridPosition)

  For each widget of a dashboard, the system MUST store a
  `GridPosition` and return it at runtime, which determines where the
  widget is placed in the dashboard's **12-column grid**. The system
  MUST reject `col + width > 13` (exceeding the grid width) as a
  validation error.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Valid GridPosition – create widget with gridPosition={col:4, row:1, width:9, height:3}
    Given the precondition according to the requirement is met
    When  create widget with gridPosition={col:4, row:1, width:9, height:3}
    Then  HTTP 201; `col + width = 13` → valid (limit exactly exhausted)

  @AC2
  Scenario: AC2 – Widget too wide – create widget with gridPosition={col:4, row:1, width:10, height:2}
    Given the precondition according to the requirement is met
    When  create widget with gridPosition={col:4, row:1, width:10, height:2}
    Then  HTTP 422 "col + width = 14 exceeds the maximum grid width of 12"

  @AC3
  Scenario: AC3 – Invalid values – create widget with gridPosition={col:0, row:1, width:3, height:1}
    Given the precondition according to the requirement is met
    When  create widget with gridPosition={col:0, row:1, width:3, height:1}
    Then  HTTP 422 "col must be ≥ 1"

  @AC4
  Scenario: AC4 – Height limit – create widget with height=7
    Given the precondition according to the requirement is met
    When  create widget with height=7
    Then  HTTP 422 "height must be at most 6"

  @AC5
  Scenario: AC5 – GridPosition in dashboard response – dashboard with 3 widgets with different GridPositions
    Given dashboard with 3 widgets with different GridPositions
    When  GET /api/v1/dashboards/{id}
    Then  each widget contains its `gridPosition` object; client renders layout from it

  @AC6
  Scenario: AC6 – Overlap not blocked server-side – two widgets are created with overlapping GridPositions
    Given the precondition according to the requirement is met
    When  two widgets are created with overlapping GridPositions
    Then  both widgets are accepted (HTTP 201); overlap is a client concern
