# Derived from: requirements/req/req-047-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-06
Feature: REQ-047 – Set filters and save as SavedFilter

  The system MUST enable all catalog visitors to set filter
  expressions on attributes of the primary entity and of joined target
  entities. Supported operators: `eq`, `neq`, `contains`,
  `startsWith`, `in`, `notIn`, `isNull`, `isNotNull`, `gt`, `lt`,
  `gte`, `lte`.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create SavedFilter, happy path – `POST /api/v1/catalogs/{id}/filters`...
    Given the precondition according to the requirement is met
    When  `POST /api/v1/catalogs/{id}/filters` with `{ name: "Active systems only", logicalOperator: "and", expressions: [{attributePath: "status", operator: "eq", value: "active"}] }`
    Then  HTTP 201; SavedFilter in catalog; offered in quick selection on the next query

  @AC2
  Scenario: AC2 – Apply SavedFilter – SavedFilter "Active systems only" with id=filter-1 exists
    Given SavedFilter "Active systems only" with id=filter-1 exists
    When  `GET /api/v1/catalogs/{id}/query?savedFilterIds[]=filter-1`
    Then  only entities with status=active in the response; `totalCount` reduced accordingly

  @AC3
  Scenario: AC3 – BR-04 – invalid attributePath – primaryEntityType ApplicationComponent has no attribute `nonexistent`
    Given primaryEntityType ApplicationComponent has no attribute `nonexistent`
    When  FilterExpression with `attributePath: "nonexistent"`
    Then  HTTP 422 "The attribute ‹nonexistent› is not defined for the type ‹ApplicationComponent›"

  @AC4
  Scenario: AC4 – Dot notation on join – JoinDefinition "Interfaces" with targetEntityT...
    Given JoinDefinition "Interfaces" with targetEntityType Interface, attribute `protocol`
    When  FilterExpression with `attributePath: "Interfaces.protocol", operator: "eq", value: "REST"`
    Then  HTTP 201; filter filters join results to protocol=REST

  @AC5
  Scenario: AC5 – OR combination – SavedFilter with `logicalOperator: "or"` and two exp...
    Given the precondition according to the requirement is met
    When  SavedFilter with `logicalOperator: "or"` and two expressions: `status=active` OR `status=deprecated`
    Then  query returns entities with status=active or status=deprecated

  @AC6
  Scenario: AC6 – Scope check – Franz has only the visitor role (no write permission)
    Given Franz has only the visitor role (no write permission)
    When  Franz tries to create a SavedFilter with scope=instance
    Then  HTTP 403; Franz can only create SavedFilters with scope=personal

  @AC7
  Scenario: AC7 – Delete SavedFilter and clean up SavedView – SavedView "Compact view" references SavedFilter id=filter-1
    Given SavedView "Compact view" references SavedFilter id=filter-1
    When  `DELETE /api/v1/catalogs/{id}/filters/filter-1`
    Then  HTTP 200; SavedFilter deleted; SavedView cleaned up (filter-1 removed from `activeFilterIds`)
