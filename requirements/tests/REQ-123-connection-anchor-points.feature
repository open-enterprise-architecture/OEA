# Derived from: requirements/req/req-123-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05
Feature: REQ-123 – Anchor points on connections

  The system MUST allow setting, moving and removing
  **anchor points** (waypoints) on connection lines.
  Anchor points control the course of the connection between source and
  target entity.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Set anchor point – user clicks on a selected connection
    Given the precondition according to the requirement is met
    When  user clicks on a selected connection
    Then  new anchor point is set on the nearest grid point and is visible as a handle

  @AC2
  Scenario: AC2 – Anchor point always on grid – anchor point is moved via drag & drop (any routing mode)
    Given the precondition according to the requirement is met
    When  anchor point is moved via drag & drop (any routing mode)
    Then  anchor point snaps to the nearest grid point; sub-grid positioning is not possible

  @AC3
  Scenario: AC3 – Remove anchor point – user double-clicks an anchor point or selects "Remove anchor point" in the context menu...
    Given the precondition according to the requirement is met
    When  user double-clicks an anchor point or selects "Remove anchor point" in the context menu
    Then  anchor point is removed; connection adjusts its course automatically

  @AC4
  Scenario: AC4 – Mode behavior – connection mode = Curved and anchor point set
    Given the precondition according to the requirement is met
    When  connection mode = Curved and anchor point set
    And   connection mode = Orthogonal and anchor point set
    Then  Bézier curve runs through the anchor point; curve handles appear on both sides of the anchor point for curve control
    And   segments run at right angles to the anchor point; anchor point lies on grid intersection
