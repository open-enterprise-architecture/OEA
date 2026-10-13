# Derived from: requirements/req/req-125-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-21
Feature: REQ-125 – Configuration form for property visibility in the metamodel editor

  The metamodel editor MUST provide a configuration form for visibility
  per `PropertyDefinition`. The form
  MUST: 1.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Mode selector visible – Kurt opens a property in the metamodel editor
    Given the precondition according to the requirement is met
    When  Kurt opens a property in the metamodel editor
    Then  visibility section is present; current mode is preselected (`public` for new properties)

  @AC2
  Scenario: AC2 – Conditional fields – mode selector is set to `Role-based`
    Given the precondition according to the requirement is met
    When  mode selector is set to `Role-based`
    Then  role selection appears; `scopingConnectionType` field is hidden

  @AC3
  Scenario: AC3 – Badge – a property has `visibilityMode ≠ public`
    Given the precondition according to the requirement is met
    When  a property has `visibilityMode ≠ public`
    Then  property entry in the list shows a "Restricted" badge or a corresponding icon

  @AC4
  Scenario: AC4 – Save validation in the UI – Kurt clicks "Save" with empty `allowedRoles` for `role-restricted`
    Given the precondition according to the requirement is met
    When  Kurt clicks "Save" with empty `allowedRoles` for `role-restricted`
    Then  inline error message appears; server request is not sent
