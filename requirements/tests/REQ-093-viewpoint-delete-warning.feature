# Derived from: requirements/req/req-093-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-12
Feature: REQ-093 – Viewpoint deletion with diagram warning

  When deleting a user-defined viewpoint, the system MUST check whether
  diagrams reference this viewpoint and display a warning with the
  number of affected diagrams. Deletion is not
  blocked; after deletion, affected diagrams no longer reference
  a valid viewpoint and receive a corresponding
  note in the UI.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Warning for used viewpoint – a viewpoint used by 5 diagrams is deleted
    Given the precondition according to the requirement is met
    When  a viewpoint used by 5 diagrams is deleted
    Then  the system shows the warning "5 diagrams will be affected"; after confirmation it is deleted

  @AC2
  Scenario: AC2 – No warning for unused viewpoint – a viewpoint used by no diagram is deleted
    Given the precondition according to the requirement is met
    When  a viewpoint used by no diagram is deleted
    Then  direct confirmation occurs without a warning dialog
