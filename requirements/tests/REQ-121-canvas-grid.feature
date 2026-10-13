# Derived from: requirements/req/req-121-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05
Feature: REQ-121 – Canvas grid with guide lines

  The diagram canvas MUST display a visible grid consisting of
  **4 thin auxiliary lines** followed by **1 thicker
  guide line** (repetition period: 5
  grid units). Entities that are placed on the canvas or resized
  MUST snap to the grid points.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Grid display – canvas is opened
    Given the precondition according to the requirement is met
    When  canvas is opened
    Then  grid visible with pattern 4 thin + 1 thick line; thicker line has clearly higher visual contrast than thin ones

  @AC2
  Scenario: AC2 – Entity snapping – an entity is moved or created on the canvas
    Given the precondition according to the requirement is met
    When  an entity is moved or created on the canvas
    Then  position and size snap to the nearest grid unit; sub-grid positioning is not possible

  @AC3
  Scenario: AC3 – Show/hide grid – user presses `G` or clicks toolbar button
    Given the precondition according to the requirement is met
    When  user presses `G` or clicks toolbar button
    Then  grid lines no longer visible; snapping remains active
