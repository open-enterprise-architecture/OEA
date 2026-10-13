# Derived from: requirements/req/req-069-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-09
Feature: REQ-069 – Edit and display Arc42 documentation for an entity

  For every ArchitectureEntity whose EntityType is assigned to at
  least one Arc42ChapterCollection, the system MUST display an "Arc42
  Documentation" tab. This tab shows all configured questions of the
  assigned collections and enables creating, editing, and deleting
  Arc42MetaObject answer entities (REQ-068).

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Tab visible on collection assignment – EntityType `application-compo...
    Given EntityType `application-component` has collection `arc42-standard` assigned
    When  Michael opens CRM-System entity (id=1)
    Then  tab "Arc42 Documentation" visible; 12 questions listed; all initially unanswered

  @AC2
  Scenario: AC2 – Tab missing without assignment – EntityType `technology-component` has no collection
    Given EntityType `technology-component` has no collection
    When  user opens Technology-Component entity
    Then  no Arc42 tab; no error

  @AC3
  Scenario: AC3 – Create answer – Michael clicks "Answer now" on question 1, writes con...
    Given the precondition according to the requirement is met
    When  Michael clicks "Answer now" on question 1, writes content, saves
    Then  arc42-meta-object entity created (id=201); arc42-describes(source=201, target=1) created; question 1 shows "✓"

  @AC4
  Scenario: AC4 – Edit answer – Michael clicks "Edit" on question 1 (id=201)
    Given the precondition according to the requirement is met
    When  Michael clicks "Edit" on question 1 (id=201)
    Then  WYSIWYG editor opens with current content; after saving, entity 201 is updated

  @AC5
  Scenario: AC5 – Progress indicator – 4 of 12 questions answered
    Given the precondition according to the requirement is met
    When  4 of 12 questions answered
    Then  chip shows "4 / 12"; open questions visually distinguished (color or icon)

  @AC6
  Scenario: AC6 – Multiple collections – EntityType has 2 collections
    Given EntityType has 2 collections
    When  Arc42 tab opened
    Then  both collections as separate sections (or tabs); progress per collection separate

  @AC7
  Scenario: AC7 – Web portal read-only – CIO opens entity in the web portal
    Given the precondition according to the requirement is met
    When  CIO opens entity in the web portal
    Then  Arc42 tab visible; contents with rendered diagrams; no "Answer" button

  @AC8
  Scenario: AC8 – Delete answer – Michael clicks "Delete" on an answered question
    Given the precondition according to the requirement is met
    When  Michael clicks "Delete" on an answered question
    Then  arc42-meta-object entity (201) and arc42-describes connection deleted; question "unanswered" again
