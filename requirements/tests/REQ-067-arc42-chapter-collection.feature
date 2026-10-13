# Derived from: requirements/req/req-067-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-04 @UC-09
Feature: REQ-067 – Configure Arc42ChapterCollection in the metamodel

  The system MUST enable the metamodel administrator to create, edit,
  and delete `Arc42ChapterCollection` objects in the
  MetamodelConfiguration. A collection consists of a `name`, an
  ordered list of `Arc42QuestionTemplate` entries, and a list of
  `assignedEntityTypeIds`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create collection – admin posts `{ id: "arc42-sme", name: "Arc42 SME"...
    Given the precondition according to the requirement is met
    When  admin posts `{ id: "arc42-sme", name: "Arc42 SME", questions: [...], assignedEntityTypeIds: ["application-component"] }`
    Then  HTTP 201; collection saved in MetamodelConfiguration; EntityType `application-component` shows collection when opened

  @AC2
  Scenario: AC2 – Add question – admin adds `{ questionId: "context", title: "Context"...
    Given the precondition according to the requirement is met
    When  admin adds `{ questionId: "context", title: "Context", questionText: "...", sortOrder: 1 }` to a collection
    Then  question appears at position 1 in the collection

  @AC3
  Scenario: AC3 – Import built-in standard collection – admin clicks "Import standard Arc42"
    Given the precondition according to the requirement is met
    When  admin clicks "Import standard Arc42"
    Then  collection `arc42-standard` with 12 questions is created as a user-defined copy (editable); original remains immutable

  @AC4
  Scenario: AC4 – Multiple assigned EntityTypes – `assignedEntityTypeIds: ["application-component", "technology-component"]`
    Given the precondition according to the requirement is met
    When  `assignedEntityTypeIds: ["application-component", "technology-component"]`
    Then  both EntityTypes show the collection in the Arc42 tab

  @AC5
  Scenario: AC5 – Delete collection – admin deletes a collection that already has answe...
    Given the precondition according to the requirement is met
    When  admin deletes a collection that already has answer entities
    Then  HTTP 422; message: "Collection has {N} answers — remove answers first or mark collection as inactive"; no data loss
