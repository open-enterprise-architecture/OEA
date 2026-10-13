# Derived from: requirements/req/req-124-*.md
# V-model system test specification – created 2026-06-30

@functional @must @UC-21
Feature: REQ-124 – Extend PropertyDefinition with visibilityMode

  The data model of `PropertyDefinition` (in
  `MetamodelConfiguration`) MUST be extended by three new fields:
  | Field | Type | Default | Constraint | |---|---|---|---| |
  `visibilityMode` | enum | `public` | `[public, role-restricted,
  connection-scoped]` | | `allowedRoles` | Role[] | [] | Mandatory if
  `visibilityMode = role-restricted`; at least 1 entry | |
  `scopingConnectionType` | string | null | Mandatory if
  `visibilityMode = connection-scoped`; must reference a valid
  `ConnectionType` ID | **Semantics of the modes**: -
  `public`: value is readable and writable by every authenticated user
  (current behavior, default) - `role-restricted`: value
  is readable/writable only by users whose active role is contained in
  `allowedRoles` - `connection-scoped`: value is readable/writable only by
  users who have a path to the requested entity in the graph via the connection type
  `scopingConnectionType`

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Data model – a `PropertyDefinition` is created or edited
    Given the precondition according to the requirement is met
    When  a `PropertyDefinition` is created or edited
    Then  `visibilityMode` can be saved; default `public` is correct

  @AC2
  Scenario: AC2 – Validation role-restricted – `visibilityMode = role-restricted` and `allowedRoles` empty
    Given the precondition according to the requirement is met
    When  `visibilityMode = role-restricted` and `allowedRoles` empty
    Then  saving is rejected with a validation error

  @AC3
  Scenario: AC3 – Validation connection-scoped – `visibilityMode = connection-scoped` and `scopingConnectionType` points to a...
    Given the precondition according to the requirement is met
    When  `visibilityMode = connection-scoped` and `scopingConnectionType` points to a non-existent ConnectionType ID
    Then  saving is rejected with a validation error

  @AC4
  Scenario: AC4 – Backward compatibility – existing `PropertyDefinition` without `visibilityMode`
    Given the precondition according to the requirement is met
    When  existing `PropertyDefinition` without `visibilityMode`
    Then  default `public` is applied; existing values are retained
