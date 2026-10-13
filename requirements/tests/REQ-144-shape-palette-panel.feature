# Derived from: requirements/req/req-144-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05 @UC-12
Feature: REQ-144 – Collapsible Shape Palette Panel in Diagram Editor

  The system MUST offer a shape palette in the diagram editor that is
  positioned as a standalone panel between the explorer and the diagram canvas.
  The panel MUST be completely showable and hideable via a
  toggle button (◀ / ▶).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Palette shows only allowed types – viewpoint "Application Layer View" allows AC, AS, AI, AF, AP
    Given viewpoint "Application Layer View" allows AC, AS, AI, AF, AP
    When  diagram editor is opened with this viewpoint
    Then  palette shows only the 5 allowed types; technology types are grayed out/hidden

  @AC2
  Scenario: AC2 – No viewpoint = all types – diagram editor without active viewpoint (free-form)
    Given diagram editor without active viewpoint (free-form)
    When the described action is performed
    Then  all configured metamodel types appear in the palette

  @AC3
  Scenario: AC3 – Collapse/expand – user clicks the toggle button (◀)
    Given the precondition according to the requirement is met
    When  user clicks the toggle button (◀)
    And   user clicks again (▶)
    Then  panel collapses to a 28px wide strip; canvas gains the freed space
    And   panel expands again to default width

  @AC4
  Scenario: AC4 – Persistence of state – user collapses the palette and closes the editor
    Given the precondition according to the requirement is met
    When  user collapses the palette and closes the editor
    Then  the next time it is opened, the palette is still collapsed
