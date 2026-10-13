# Derived from: requirements/req/req-146-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-05 @UC-14 @UC-15
Feature: REQ-146 – Delete from Diagram (DEL) vs. Delete Entity (Ctrl+DEL) via Context Menu

  The system MUST offer two semantically different
  delete operations in the diagram editor: 1. **"Remove from Diagram" (DEL)**:
  Removes the shape from the current diagram.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Remove from Diagram — model untouched – shape "Catalog-Service" [AC] is in diagram A and B
    Given shape "Catalog-Service" [AC] is in diagram A and B
    When  user presses DEL on shape in diagram A
    Then  shape in diagram A gone; shape in diagram B unchanged; entity present in explorer

  @AC2
  Scenario: AC2 – Delete Entity — confirmation dialog – user presses Ctrl+DEL on shape
    Given the precondition according to the requirement is met
    When  user presses Ctrl+DEL on shape
    Then  confirmation dialog appears with warning "affects all diagrams"; proceed only on confirmation

  @AC3
  Scenario: AC3 – Delete Entity — soft delete – user confirms Delete Entity
    Given the precondition according to the requirement is met
    When  user confirms Delete Entity
    Then  entity has `deleted_at` set; no longer appears in normal catalog queries; restorable via audit log (UC-15)

  @AC4
  Scenario: AC4 – Delete Entity — other diagrams marked – entity deleted that occurs as a shape in 3 further diagrams
    Given the precondition according to the requirement is met
    When  entity deleted that occurs as a shape in 3 further diagrams
    Then  in these 3 diagrams the shape is marked as "deleted" (gray, warning); no silent data loss

  @AC5
  Scenario: AC5 – Context menu accessibility – user right-clicks on a shape
    Given the precondition according to the requirement is met
    When  user right-clicks on a shape
    Then  context menu appears with both options incl. shortcuts; DEL and Ctrl+DEL also work without the context menu

  @AC6
  Scenario: AC6 – Undo Remove from Diagram – user has removed shape from diagram via DEL
    Given the precondition according to the requirement is met
    When  user has removed shape from diagram via DEL
    Then  Ctrl+Z restores the shape (local undo, no API call)
