# Spec: openspec identity/service-accounts
# Source material: requirements/user-stories/US-142-create-service-account.md (AC1, AC3, AC4),
# US-149-assign-roles-to-service-account.md (AC1, AC3), REQ-155, REQ-176, REQ-172

@US-142 @US-149 @UC-02 @UC-23
Feature: Service accounts

  External systems access the API through service accounts. The system admin
  creates them with a client secret that is shown once and expires, assigns only
  roles flagged as assignable to service accounts, rotates the secret and
  deactivates an account for good.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in
    And the session is confirmed with a second factor

  # Requirement: Create a service account; Client secret

  @US-142 @AC1 @REQ-155
  Scenario: Service account created
    When the system admin creates the service account "ci-scanner" with the roles "integration-writer"
    Then the service account is created with a client ID and a client secret
    And a person of type "service-account" named "ci-scanner" exists
    And the service account "ci-scanner" holds the roles "integration-writer"
    And the audit trail contains the event "service-account.created"

  @US-142 @REQ-155
  Scenario: Default role
    When the system admin creates the service account "ci-scanner" without roles
    Then the service account "ci-scanner" holds the roles "integration-writer"

  @US-142 @REQ-155
  Scenario: Without permission
    Given an active person "Erika" holding "enterprise-architect" has a session confirmed with a second factor
    When "Erika" creates the service account "ci-scanner"
    Then the request is rejected with status 403
    And no service account "ci-scanner" exists

  @US-142 @REQ-155
  Scenario Outline: Name length boundaries
    When the system admin creates a service account with a name of <length> characters
    Then the service account request is answered with <status>

    Examples:
      | length | status |
      | 100    | 201    |
      | 101    | 400    |
      | 0      | 400    |

  @US-142 @REQ-155
  Scenario: Name taken
    Given the system admin has created the service account "ci-scanner"
    When the system admin creates the service account "CI-Scanner" without roles
    Then the request is rejected with status 422
    And no service account "CI-Scanner" exists

  @US-142 @AC1 @REQ-176
  Scenario: Secret shown once
    When the system admin creates the service account "ci-scanner" with the roles "integration-writer"
    And the system admin reads the service account "ci-scanner" and the list of service accounts
    Then only the creation response contains the secret, which starts with "oea_cs_"
    And the stored secret of "ci-scanner" is an argon2id hash that differs from the secret

  # Requirement: Secret expiry; Secrets that expire soon

  @REQ-176
  Scenario: Default expiry
    When the system admin creates the service account "ci-scanner" without roles
    Then the secret of "ci-scanner" expires 90 days after its creation

  @REQ-176
  Scenario Outline: Validity boundaries
    When the system admin creates a service account with a secret validity of <days> days
    Then the service account request is answered with <status>

    Examples:
      | days | status |
      | 1    | 201    |
      | 0    | 400    |
      | 365  | 201    |
      | 366  | 400    |

  @REQ-176 @AC2
  Scenario: 14-day boundary
    Given the system admin has created the service accounts "first" and "second"
    And the secret of "first" expires in exactly 14 days and the secret of "second" in 14 days and one minute
    When the system admin lists the service accounts
    Then "first" is marked as expiring soon and "second" is not

  # Requirement: Rotate the secret

  @US-142 @AC3 @REQ-155
  Scenario: Secret rotated
    Given the system admin has created the service account "ci-scanner"
    When the system admin rotates the secret of "ci-scanner"
    Then the response contains a new secret and the same client ID
    And the secrets of "ci-scanner" are "rotated, active"
    And the audit trail contains the event "service-account.secret-rotated"

  @US-142 @REQ-155
  Scenario: Unknown service account
    When the system admin rotates the secret of an unknown client ID
    Then the request is rejected with status 404

  # Requirement: Roles of a service account

  @US-149 @AC3 @REQ-155 @REQ-176 @AC1
  Scenario: Privileged role rejected
    When the system admin creates the service account "ci-scanner" with the roles "system-admin"
    Then the request is rejected with status 422
    And the problem names "system-admin"
    And no service account "ci-scanner" exists
    And the audit trail contains the event "authz.denied"

  @US-149 @AC1
  Scenario: Assignable roles
    When the system admin creates the service account "ci-scanner" with the roles "viewer, integration-writer, auditor"
    Then the service account "ci-scanner" holds the roles "auditor, integration-writer, viewer"

  @US-149
  Scenario Outline: Role count boundaries
    Given <count> custom roles assignable to service accounts exist
    When the system admin creates a service account with <count> of them
    Then the service account request is answered with <status>

    Examples:
      | count | status |
      | 20    | 201    |
      | 21    | 400    |

  @US-149 @REQ-157
  Scenario: Without role:assign
    Given a custom role "account-manager" with only the permission "service-account:manage"
    And an active person "Max" holding "account-manager" has a session confirmed with a second factor
    When "Max" creates the service account "ci-scanner" with the roles "viewer"
    Then the request is rejected with status 403
    And no service account "ci-scanner" exists

  # Requirement: Change a service account

  @US-149 @AC1 @REQ-157
  Scenario: Role replaced
    Given the system admin has created the service account "cmdb-importer" with the roles "viewer"
    When the system admin changes the roles of "cmdb-importer" to "integration-writer"
    Then the service account "cmdb-importer" holds the roles "integration-writer"
    And the audit trail contains the event "role.assigned" 2 times
    And the audit trail contains the event "role.revoked" 1 times
    And the revoked assignment of "viewer" to "cmdb-importer" keeps its history

  @US-142 @AC4
  Scenario: Without integration-writer
    Given the system admin has created the service account "cmdb-importer" with the roles "viewer"
    Then the permissions of "cmdb-importer" contain "entity:read" and not "entity:write"

  # Requirement: Deactivate a service account

  @REQ-155
  Scenario: Service account deactivated
    Given the system admin has created the service account "ci-scanner"
    When the system admin deactivates "ci-scanner"
    Then the service account request is answered with 204
    And the service account "ci-scanner" is "inactive" without an active secret and without roles
    And the audit trail contains the event "service-account.deactivated"

  @REQ-155
  Scenario: Deactivated account stays inactive
    Given the system admin has created the service account "ci-scanner"
    And the system admin has deactivated "ci-scanner"
    When the system admin rotates the secret of "ci-scanner"
    Then the request is rejected with status 409
    When the system admin changes the roles of "ci-scanner" to "viewer"
    Then the request is rejected with status 409
    And the service account "ci-scanner" is "inactive" without an active secret and without roles

  # Requirement: Re-authentication

  @REQ-172
  Scenario: Authentication too old
    Given the system admin has created the service account "ci-scanner"
    And the system admin authenticated 5 minutes and 1 second ago
    When the system admin rotates the secret of "ci-scanner"
    Then the request is rejected with status 401 and problem type "reauthentication-required"
    And the secrets of "ci-scanner" are "active"

  @REQ-172
  Scenario: Recent authentication
    Given the system admin has created the service account "ci-scanner"
    And the system admin authenticated 4 minutes and 59 seconds ago
    When the system admin rotates the secret of "ci-scanner"
    Then the service account request is answered with 200

  # Requirement: List service accounts

  @REQ-155
  Scenario: Active filter
    Given the system admin has created the service accounts "active-one" and "inactive-one"
    And the system admin has deactivated "inactive-one"
    When the system admin lists the active service accounts
    Then the list contains "active-one" and not "inactive-one"
