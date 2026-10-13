# Derived from: requirements/req/req-066-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-04 @UC-05 @UC-06 @UC-08
Feature: REQ-066 – Configurable entity creation workflows (wizard)

  For every `EntityTypeDefinition` with at least one configured
  `creationStep`, the system MUST automatically open a multi-page
  wizard when creating a new entity — regardless of whether in the
  catalog context or in the diagram context. Each `CreationStep`
  corresponds to exactly one pop-over page.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Wizard opens automatically – catalog context – ApplicationComponent has 3 `creationSteps`
    Given ApplicationComponent has 3 `creationSteps`
    When  Lukas clicks "+ New entity" in the ApplicationComponent catalog
    Then  wizard opens on page 1 "Basic data"; no classic creation modal

  @AC2
  Scenario: AC2 – Wizard opens automatically – diagram context – Lukas places an Appli...
    Given the precondition according to the requirement is met
    When  Lukas places an ApplicationComponent element in the diagram (drag & drop from palette)
    Then  wizard opens immediately; entity is visible on the canvas only after "Finish"

  @AC3
  Scenario: AC3 – Navigation Next / Finish – 3 steps
    Given 3 steps
    When  Lukas navigates: Next → Next → Finish
    Then  step 1 → step 2 → step 3 → Finish; entity and all connections persisted atomically

  @AC4
  Scenario: AC4 – Back navigation – Lukas is on step 2 and clicks "Back"
    Given the precondition according to the requirement is met
    When  Lukas is on step 2 and clicks "Back"
    Then  step 1 is displayed again; data entered in step 1 is retained

  @AC5
  Scenario: AC5 – Mandatory property blocks Finish – step 1 has property `owner` (valid...
    Given step 1 has property `owner` (validationMode=mandatory); Lukas leaves it empty
    When  Lukas clicks "Finish" (or tries to proceed to step 3)
    Then  error message on the corresponding step; no persistence

  @AC6
  Scenario: AC6 – connectionAssignment minConnections=1 – step 3 "Capability linking" with minConnections=1
    Given step 3 "Capability linking" with minConnections=1
    When  Lukas clicks "Finish" without having selected a capability
    Then  step 3 shows error "At least 1 capability must be linked"; no persistence

  @AC7
  Scenario: AC7 – Cancel = no entity – Lukas closes the wizard (ESC or X) on step 2
    Given the precondition according to the requirement is met
    When  Lukas closes the wizard (ESC or X) on step 2
    Then  no entity in the repository; no partially created connection

  @AC8
  Scenario: AC8 – No wizard without creationSteps – EntityType `stereotype` without `creationSteps` (empty list)
    Given EntityType `stereotype` without `creationSteps` (empty list)
    When  user creates a new instance
    Then  classic creation modal (no wizard); no regression error

  @AC9
  Scenario: AC9 – Atomic persistence – wizard with entity + 2 connections
    Given wizard with entity + 2 connections
    When  the second connection creation fails server-side (e.g. target entity deleted)
    Then  neither entity nor first connection are persistent; HTTP 422 with error description
