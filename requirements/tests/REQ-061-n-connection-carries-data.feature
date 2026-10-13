# Derived from: requirements/req/req-061-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-08
Feature: REQ-061 – n-Connection carries-data — link DataFlow with DataObject

  The system MUST make it possible to create an ArchitectureEntity of
  type `carries-data` (`isConnection=true`) whose `sourceEntityId`
  points to a connection entity (i.e. an ArchitectureEntity with
  `isConnection=true`, e.g.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Create carries-data – DataFlow id=5 (`data-flow`, `allowsConnectionAs...
    Given DataFlow id=5 (`data-flow`, `allowsConnectionAsSource=true`); DataObject id=42 (`data-object`)
    When  Lukas creates `carries-data` connection with sourceEntityId=5, targetEntityId=42
    Then  HTTP 201; carries-data receives its own integer ID (e.g. 103); sourceEntityId=5 and targetEntityId=42 set

  @AC2
  Scenario: AC2 – Rejection if source is not allowsConnectionAsSource – ApplicationComp...
    Given ApplicationComponent id=1 (`application-component`, `allowsConnectionAsSource=false`)
    When  Lukas tries to create carries-data with sourceEntityId=1
    Then  HTTP 422; error message names `application-component` as not allowed as a connection source

  @AC3
  Scenario: AC3 – Rejection target=connection – DataFlow id=5 (isConnection=true) as potential targetEntityId
    Given DataFlow id=5 (isConnection=true) as potential targetEntityId
    When  Lukas tries to create carries-data with targetEntityId=5
    Then  HTTP 422

  @AC4
  Scenario: AC4 – Immutability of source/target – carries-data id=103 with sourceEntityId=5, targetEntityId=42
    Given carries-data id=103 with sourceEntityId=5, targetEntityId=42
    When  Lukas tries PUT with sourceEntityId=6
    Then  HTTP 422; source/target were not changed

  @AC5
  Scenario: AC5 – Depth limit v1.0 – carries-data id=103; carries-data has `allowsConnectionAsSource=false`
    Given carries-data id=103; carries-data has `allowsConnectionAsSource=false`
    When  Lukas tries to create another connection with sourceEntityId=103
    Then  HTTP 422 "n-Connection depth exceeded"
