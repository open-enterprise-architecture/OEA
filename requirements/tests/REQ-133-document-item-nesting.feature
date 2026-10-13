# Derived from: requirements/req/req-133-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-09
Feature: REQ-133 – Nesting of DocumentItems (hierarchical chapter structure)

  The system MUST allow hierarchical nesting of DocumentItems
  within a DocumentCollection. A DocumentItem MAY
  reference a parent DocumentItem of the same collection
  via the field `parentId` (`parentId=null` = root chapter).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create child element – user selects "Create subchapter" in the chapter navigation
    Given the precondition according to the requirement is met
    When  user selects "Create subchapter" in the chapter navigation
    Then  new DocumentItem with `parentId` of the selected item is created; appears indented in the navigation

  @AC2
  Scenario: AC2 – Depth limit – user tries to create a subchapter at level 6
    Given the precondition according to the requirement is met
    When  user tries to create a subchapter at level 6
    Then  action is refused with the note "Maximum depth (5) reached"

  @AC3
  Scenario: AC3 – Cycle protection – `parentId` of an item would be set such that a cycle arises (item A → B → A)...
    Given the precondition according to the requirement is met
    When  `parentId` of an item would be set such that a cycle arises (item A → B → A)
    Then  saving refused; error message

  @AC4
  Scenario: AC4 – Cascade delete warning – user deletes an item with children
    Given the precondition according to the requirement is met
    When  user deletes an item with children
    Then  warning "This chapter has N subchapters that will also be deleted"; confirmation required

  @AC5
  Scenario: AC5 – Drag & drop – user moves an item in the chapter navigation via drag & drop
    Given the precondition according to the requirement is met
    When  user moves an item in the chapter navigation via drag & drop
    Then  `parentId` and `sortOrder` are updated; navigation shows new structure immediately
