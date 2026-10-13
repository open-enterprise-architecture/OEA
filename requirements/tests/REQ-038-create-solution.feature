# Derived from: requirements/req/req-038-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-05
Feature: REQ-038 – Create solution and describe architecture vision

  The system MUST enable persons with solution architect permission to
  create a solution with name, architecture vision (free text: goal
  and scope of the initiative) and – in plateau mode – the plateau
  references (`fromPlateauId`, `toPlateauId`); the system MUST enforce
  business rules BR-01 and BR-02 of the solution.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Michael has solution architect permission; no plateau mode
    Given Michael has solution architect permission; no plateau mode
    When  he enters the name "ERP Extension Logistics" and a description text and saves
    Then  solution exists with status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; visible in the solution overview

  @AC2
  Scenario: AC2 – instance in plateau mode; plateau P0 (baseline) and P1 (target) exist
    Given instance in plateau mode; plateau P0 (baseline) and P1 (target) exist
    When  Michael sets `fromPlateauId=P0` and `toPlateauId=P1` and saves
    Then  solution created with both plateau references; BR-02 is satisfied

  @AC3
  Scenario: AC3 – Michael enters `fromPlateauId=P0`, leaves `toPlateauId` empty
    Given Michael enters `fromPlateauId=P0`, leaves `toPlateauId` empty
    When  he saves
    Then  validation error "Both plateau references must be set or both empty"; no object is created (BR-01)

  @AC4
  Scenario: AC4 – a solution with the name "ERP Extension Logistics" already exists
    Given a solution with the name "ERP Extension Logistics" already exists
    When  Michael wants to create a second solution with the same name
    Then  validation error "Name 'ERP Extension Logistics' is already taken"

  @AC5
  Scenario: AC5 – person without solution architect permission
    Given person without solution architect permission
    When  they try to create a solution
    Then  403 Forbidden; no object is created
