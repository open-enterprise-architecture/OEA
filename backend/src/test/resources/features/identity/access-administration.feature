# Spec: openspec identity/access-administration
# Source material: requirements/user-stories/US-147-map-idp-group-to-role.md (AC1, AC4, AC7), ADR-042;
# US-018 (AC1, AC2), US-019 (AC2, adjusted to SoD-7), US-146 (AC1, AC4, AC5)

@US-147 @UC-23
Feature: Access administration through the API

  The system admin configures OIDC providers, creates persons, binds them to the
  immutable subject of a provider and maps provider groups to roles. The first
  binding or mapping gives another identity access, so it needs the admin's
  second factor and ends the setup phase for good.

  Background:
    Given a bootstrapped OEA instance in its setup phase with the system admin "admin"
    And the system admin is logged in

  @ADR-036
  Scenario Outline: Provider with an unsafe issuer
    When the system admin saves a provider with the issuer "<issuer>"
    Then the request is rejected with status 422 and problem type "validation-failed"
    And the problem names the issuer "<issuer>"

    Examples:
      | issuer                            |
      | http://idp.example.org/realms/oea |
      | https://localhost:8443/realms/oea |

  @REQ-188 @AC3
  Scenario: Client secret never returned
    When the system admin saves a provider with a client secret
    And the system admin reads the provider again
    Then the response states that a client secret is set but does not contain it
    And the audit trail contains the event "auth-provider.changed"

  @ADR-042 @REQ-171
  Scenario: First mapping without second factor
    Given the provider "https://idp.example.org/realms/oea" is configured
    When the system admin maps a group to the role "viewer"
    Then the request is rejected with status 403 and problem type "second-factor-required"
    And no mapping is stored
    And the setup phase continues

  @ADR-042 @REQ-171
  Scenario: First mapping with second factor
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the system admin maps a group to the role "viewer"
    And the request is rejected with status 403 and problem type "second-factor-required"
    When the system admin starts the TOTP enrollment
    And the system admin confirms the enrollment with a valid code
    And the system admin maps the same group to the role "viewer" again
    Then the mapping is stored
    And the setup phase has ended with the action "idp-group-mapping"
    And the audit trail contains the event "system.setup-phase-ended"

  @REQ-166
  Scenario: Binding created
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    When the system admin creates a person and binds it to the subject "a1b2c3"
    Then the person exists in status "invited"
    And the binding stores provider, issuer, subject and the method "admin-entered"
    And the audit trail contains the event "person.created"
    And the audit trail contains the event "person.identity-bound"

  @REQ-166
  Scenario: Pair already bound
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    And the system admin creates a person and binds it to the subject "a1b2c3"
    When the system admin creates a second person and binds it to the subject "a1b2c3"
    Then the request is rejected with status 409 and problem type "conflict"
    And the subject "a1b2c3" is still bound to the first person

  @US-147 @AC1
  Scenario: Mapping created
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    When the system admin maps a group to the role "viewer"
    Then the mapping is stored with issuer, group identifier, role, creator and creation time
    And the audit trail contains the event "idp-mapping.created"

  @US-147 @AC4
  Scenario: Display name instead of identifier
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    When the system admin maps the group "EA-Viewers" to the role "viewer"
    Then the request is rejected with status 422 and problem type "validation-failed"
    And no mapping is stored

  @US-147 @AC7
  Scenario: Mapping deleted
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    And the system admin maps a group to the role "viewer"
    When the system admin deletes the mapping
    Then the mapping is no longer listed
    And the audit trail contains the event "idp-mapping.deleted"

  @US-018 @AC1 @AC2 @REQ-018
  Scenario: Mapping to a group never seen
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    When the system admin maps a group to the role "system-admin"
    Then the mapping is stored
    And the response carries the warning "group-not-seen"

  @US-019 @AC2 @REQ-019 @SoD-7
  Scenario: Deactivation without another path
    Given the provider "https://idp.example.org/realms/oea" is configured
    And the session is confirmed with a second factor
    And the system admin maps a group to the role "system-admin"
    When the system admin deactivates the local system admin account
    Then the request is rejected with status 409 and problem type "last-admin-path"
    And the local system admin account is still usable
    And the audit trail contains the event "system-admin-account.deactivated"

  @US-146 @AC1 @AC4 @SoD-7
  Scenario: Revoking the last holder and another path
    Given the session is confirmed with a second factor
    And an active person "Erika" without any role has a session
    And an active person "Paul" without any role has a session
    And the system admin has assigned "system-admin" to "Erika"
    And an active person "Max" holding "security-admin" has a session confirmed with a second factor
    And the local system admin account is deactivated
    When "Max" revokes the assignment of "system-admin" to "Erika"
    Then the request is rejected with status 409 and problem type "last-admin-path"
    When "Max" assigns "system-admin" to "Paul"
    And "Max" revokes the assignment of "system-admin" to "Erika"
    Then the revocation succeeds

  @US-146 @AC5 @SoD-7
  Scenario: Status change of the last holder
    Given the session is confirmed with a second factor
    And an active person "Erika" without any role has a session
    And the system admin has assigned "system-admin" to "Erika"
    And an active person "Max" holding "security-admin" has a session confirmed with a second factor
    And the local system admin account is deactivated
    When "Max" sets "Erika" to "offboarded"
    Then the request is rejected with status 409 and problem type "last-admin-path"
    And "Erika" is still "active"
