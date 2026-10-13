# Derived from: requirements/req/req-145-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05
Feature: REQ-145 – Shape Drag-to-Canvas with Entity Lookup and Inline Creation

  After a shape template from the palette is dropped
  onto the canvas, the system MUST display an entity lookup dialog. The dialog MUST
  contain a text input field that, while typing, displays existing
  entities of the same type as autocomplete suggestions.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Insert existing entity – entity "Catalog-Service" [AC] exists in the solution
    Given entity "Catalog-Service" [AC] exists in the solution
    When  user drags [AC] shape onto canvas, types "Catalog", sees "Catalog-Service (existing)", selects it
    Then  shape appears on canvas; no new object in the model; log shows "existing"

  @AC2
  Scenario: AC2 – Create new entity – user types "New-Reporting-Service", no autocomplete match, presses OK
    Given the precondition according to the requirement is met
    When  user types "New-Reporting-Service", no autocomplete match, presses OK
    Then  new entity "New-Reporting-Service" [AC] is created; shape appears on canvas; explorer tree updated

  @AC3
  Scenario: AC3 – Autocomplete on input – user types "auth"
    Given the precondition according to the requirement is met
    When  user types "auth"
    Then  dropdown shows max. 8 entities with "auth" in the name; response time < 300 ms with 10,000 entities

  @AC4
  Scenario: AC4 – Cancel = no side effect – user presses Cancel or ESC after the drop
    Given the precondition according to the requirement is met
    When  user presses Cancel or ESC after the drop
    Then  no entity created, no shape in the diagram, drop completely undone

  @AC5
  Scenario: AC5 – Duplicate warning – user drags the same entity into the same diagram a second time
    Given the precondition according to the requirement is met
    When  user drags the same entity into the same diagram a second time
    Then  system shows warning "Entity already on canvas — insert again?" with Yes/No
