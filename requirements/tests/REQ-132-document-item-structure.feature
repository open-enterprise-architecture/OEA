# Derived from: requirements/req/req-132-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-09
Feature: REQ-132 – DocumentItem as atomic unit of the DocumentCollection

  The system MUST provide `DocumentItem` as the atomic instance unit of a
  `DocumentCollection`. Every DocumentItem MUST carry the following
  fields: | Field | Mandatory | Description | |---|---|---| |
  `name` | required | Chapter title; displayed in the chapter navigation
  and as heading in the content; max.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create – user creates a new chapter in a DocumentCollection
    Given the precondition according to the requirement is met
    When  user creates a new chapter in a DocumentCollection
    Then  system creates a DocumentItem with `name` (mandatory), optional `alias`, empty `content` and automatically assigned `sortOrder`

  @AC2
  Scenario: AC2 – Name in navigation – DocumentCollection is opened
    Given the precondition according to the requirement is met
    When  DocumentCollection is opened
    Then  chapter navigation shows `name` of each item; items are sorted by `sortOrder`

  @AC3
  Scenario: AC3 – Alias unique – user sets `alias` that is already assigned within the collection
    Given the precondition according to the requirement is met
    When  user sets `alias` that is already assigned within the collection
    Then  inline error; saving prevented

  @AC4
  Scenario: AC4 – Content WYSIWYG – user opens a DocumentItem
    Given the precondition according to the requirement is met
    When  user opens a DocumentItem
    Then  WYSIWYG editor is available; all supported formats (Markdown, Mermaid, PlantUML, Draw.io, `[[`, `{{`) are usable
