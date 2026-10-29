# Spec: openspec identity/role-assignments
# Source material: requirements/user-stories/US-144-assign-and-revoke-local-role.md (AC1, AC3, AC4)

@US-144 @UC-23
Feature: Local role assignments

  An administrator assigns an authorization role to a single person, optionally
  until a date, and revokes it again. The role applies within a minute, an expired
  assignment no longer grants anything, and a revocation keeps the history and ends
  the person's sessions.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in
    And the session is confirmed with a second factor
    And an active person "Anna" without any role has a session

  @US-144 @AC1
  Scenario: Assignment stored and effective
    When the system admin assigns "enterprise-architect" to "Anna" until the end of her contract with the reason "TICKET-123"
    Then the assignment is stored with source "local", the granting system admin, the validity and the reason
    And "Anna" may call an operation requiring "entity:write"
    And the audit trail contains the event "role.assigned"

  @US-144 @AC3
  Scenario: Expired assignment
    Given the system admin has assigned "enterprise-architect" to "Anna" until a time that passed more than 60 seconds ago
    Then "Anna" may not call an operation requiring "entity:write"
    And after the expiry job ran twice the audit trail contains the event "role.assignment-expired" 1 times

  @US-144 @AC4
  Scenario: Role revoked
    Given the system admin has assigned "enterprise-architect" and "viewer" to "Anna"
    When the system admin revokes the assignment of "enterprise-architect" to "Anna"
    Then the assignment shows when and by whom it was revoked
    And the session of "Anna" is rejected with 401
    And after a new login "Anna" holds only "viewer"
    And the audit trail contains the event "role.revoked"
