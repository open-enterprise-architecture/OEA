# Derived from: requirements/req/req-063-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: manual (UI test in client app)

@functional @should @UC-08
Feature: REQ-063 – n-Connection canvas rendering (3-dot indicator + connection panel)

  In the canvas editor of the client app (Electron, ADR-009), the
  system MUST display a **circular 3-dot indicator** (`•••`) on the
  connection line for each connection entity that is referenced as
  `sourceEntityId` in at least one further connection (n-Connection);
  double-clicking this indicator MUST open a **connection panel** that
  lists all n-Connections of this connection and is filterable by
  connection type at the top edge.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Indicator appears – DataFlow id=5 is `sourceEntityId` of carries-data id=103
    Given DataFlow id=5 is `sourceEntityId` of carries-data id=103
    When  Lukas opens a diagram containing DataFlow id=5
    Then  the 3-dot circle indicator appears on the line of DataFlow id=5

  @AC2
  Scenario: AC2 – No indicator without n-Connection – DataFlow id=7 is sourceEntityId in no n-Connection
    Given DataFlow id=7 is sourceEntityId in no n-Connection
    When  diagram with DataFlow id=7 opened
    Then  no indicator on this line

  @AC3
  Scenario: AC3 – Double-click opens panel – Lukas double-clicks the 3-dot indicator on DataFlow id=5
    Given the precondition according to the requirement is met
    When  Lukas double-clicks the 3-dot indicator on DataFlow id=5
    Then  connection panel opens; header shows "ERP → DWH (data-flow)"; filter bar shows chip `carries-data (2)`

  @AC4
  Scenario: AC4 – Filter chip switches view – panel with chips `carries-data (2)` and `security-control (1)` (both active)
    Given panel with chips `carries-data (2)` and `security-control (1)` (both active)
    When  Lukas clicks `security-control`
    Then  only `security-control` connections visible; `carries-data` entries hidden

  @AC5
  Scenario: AC5 – Web portal read-only – CIO opens a diagram with n-Connection indicator in the web portal
    Given the precondition according to the requirement is met
    When  CIO opens a diagram with n-Connection indicator in the web portal
    Then  indicator visible; double-click opens panel in read-only mode; "Create new n-Connection" button missing

  @AC6
  Scenario: AC6 – Multiple indicators on one line – DataFlow id=5 has 3 carries-data and 2 security-control connections
    Given DataFlow id=5 has 3 carries-data and 2 security-control connections
    When  diagram loaded
    Then  a single indicator (not 5 separate ones); panel shows both types in filter bar; total count visible (e.g. `•••  5`)
