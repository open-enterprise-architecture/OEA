# Derived from: requirements/req/req-122-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05
Feature: REQ-122 – Connection routing modes (curved, straight, orthogonal)

  The system MUST offer three routing modes for each connection on the canvas
  that can be selected individually per connection: | Mode |
  Course | Grid relation | |---|---|---| | **Curved** | Bézier curve;
  soft swing around anchor points | does **not** follow the grid | |
  **Straight** | Direct line from source to target anchor point | does
  **not** necessarily follow the grid | | **Orthogonal** | Exclusively
  90° angles (right-angled routing) | follows the **grid** | The
  routing mode MUST be changeable via the connection's context menu (right-click)
  or a toolbar when the connection is selected. The
  default routing mode for new connections MUST be
  configurable in the viewpoint.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Curved mode – routing mode = Curved
    Given the precondition according to the requirement is met
    When  routing mode = Curved
    Then  connection is rendered as a Bézier curve; curve course is controllable via Bézier handles (via anchor points, REQ-123); line follows no grid

  @AC2
  Scenario: AC2 – Orthogonal mode – routing mode = Orthogonal
    Given the precondition according to the requirement is met
    When  routing mode = Orthogonal
    Then  connection makes exclusively 90° bends; all segments run horizontally or vertically; bend points lie on grid points (REQ-121)

  @AC3
  Scenario: AC3 – Mode switch – user changes the routing mode of an existing connection
    Given the precondition according to the requirement is met
    When  user changes the routing mode of an existing connection
    Then  connection is re-rendered immediately; existing anchor points (REQ-123) are retained and applied to the new routing (rounded to grid if necessary when switching to orthogonal)

  @AC4
  Scenario: AC4 – Viewpoint default – a new connection is dragged onto the canvas
    Given the precondition according to the requirement is met
    When  a new connection is dragged onto the canvas
    Then  routing mode matches the default configured in the viewpoint; without configuration = Orthogonal
