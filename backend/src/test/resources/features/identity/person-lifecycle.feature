# Spec: openspec identity/person-lifecycle
# Source material: requirements/user-stories/US-148-revoke-access-on-offboarding.md (AC1, AC2, AC5)

@US-148 @UC-23
Feature: Person lifecycle

  An administrator takes a person's access away temporarily or for good. Leaving
  the status active ends the person's sessions at once; offboarding also revokes
  the local role assignments, while history and audit stay attributed.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And an active person "Anna" without any role has a session

  @US-148 @AC1 @AC2
  Scenario: Offboarding ends sessions and local assignments
    Given the system admin has assigned "enterprise-architect" to "Anna"
    When the system admin sets "Anna" to "offboarded"
    Then the session of "Anna" is rejected with 401
    And the audit trail contains the event "session.revoked"
    And the assignment of "enterprise-architect" to "Anna" was revoked by the system admin with the reason "offboarding"
    And the audit trail contains the event "role.revoked"
    And the audit trail contains the event "person.status-changed"

  @US-148 @AC5
  Scenario: Missing permission
    Given a custom role "assigner" with only the permission "role:assign"
    And an active person "Max" holding "assigner" has a session confirmed with a second factor
    When "Max" sets "Anna" to "offboarded"
    Then the request is rejected with status 403
    And "Anna" is still "active"
