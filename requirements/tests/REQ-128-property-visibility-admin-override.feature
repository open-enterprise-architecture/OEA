# Derived from: requirements/req/req-128-*.md
# V-model system test specification – created 2026-06-30

@functional @should @UC-21
Feature: REQ-128 – Admin override for property visibility

  The system MUST provide a configurable **admin override**:
  users with a system role marked as
  `overridesPropertyVisibility=true` (default:
  `system-admin`) MUST be able to read all properties, regardless of
  `visibilityMode`. The override status MUST be noted in the audit log
  when a restricted property is read by an override user.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Override effective – user with `system-admin` role retrieves an entity with restricted properties
    Given the precondition according to the requirement is met
    When  user with `system-admin` role retrieves an entity with restricted properties
    Then  all values are returned; `visibilityMode` has no effect

  @AC2
  Scenario: AC2 – Audit log entry – override user reads a `role-restricted` or `connection-scoped` field
    Given the precondition according to the requirement is met
    When  override user reads a `role-restricted` or `connection-scoped` field
    Then  audit log contains entry with type `property-override-access`, user ID, entity ID and property name

  @AC3
  Scenario: AC3 – Override configurable – Kurt edits the override role list in the metamodel editor and adds another role...
    Given the precondition according to the requirement is met
    When  Kurt edits the override role list in the metamodel editor and adds another role
    Then  users of this role receive override access from now on

  @AC4
  Scenario: AC4 – No implicit override – user has `EA-Manager` role, but `EA-Manager` is not in the override list...
    Given the precondition according to the requirement is met
    When  user has `EA-Manager` role, but `EA-Manager` is not in the override list
    Then  visibility restrictions apply normally; no automatic override through hierarchy assumption
