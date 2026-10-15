# Spec: openspec identity/idp-roles; identity/sessions and identity/access-administration (changed scenarios)
# Source material: requirements/user-stories/US-147-map-idp-group-to-role.md (AC2, AC3, AC5, AC6, AC7), REQ-165 AC5

@US-147 @UC-23 @oidc
Feature: Roles derived from IdP group mappings

  A person who logs in through an identity provider receives the roles that the
  administrator mapped to the person's groups at that provider. The roles follow
  the IdP within 15 minutes, are withheld when the groups claim cannot be used,
  and privileged roles need a second factor at the IdP.

  Background:
    Given an OEA instance with the development realm as enabled OIDC provider

  @US-147 @AC2
  Scenario: Role derived at login
    Given the realm group "oea-viewers" is mapped to the role "viewer"
    And the person bound to "dev-viewer" has the status "active"
    When "dev-viewer" logs in through the provider
    Then the person holds the role "viewer" from the IdP, recorded with the session
    And only the permissions of effective roles are listed
    And the audit trail contains "auth.idp-roles-derived" with the trigger "login"

  @REQ-003
  Scenario: Person without any role
    Given the person bound to "dev-no-role" has the status "active"
    When "dev-no-role" logs in through the provider
    Then the person holds no role and no permission

  @US-147 @AC3
  Scenario: Groups claim missing
    Given the person bound to "dev-no-role" has the status "active"
    When "dev-no-role" logs in through the provider
    Then the session records the groups claim as "missing"
    And the audit trail contains "auth.idp-groups-unusable" with the reason "missing"

  @US-147 @AC3
  Scenario: Same group identifier from another issuer
    Given the realm group "oea-enterprise-architects" is mapped to the role "enterprise-architect" only for another issuer
    And the person bound to "dev-architect" has the status "active"
    When "dev-architect" logs in through the provider
    Then the person holds no role and no permission

  @US-147 @AC6 @REQ-165 @AC5
  Scenario: Removal from the group in the IdP
    Given the realm group "oea-portal-readers" is mapped to the role "portal-reader"
    And the person bound to "dev-portal-reader" has the status "active"
    And "dev-portal-reader" has logged in through the provider
    When "dev-portal-reader" is removed from the realm group "oea-portal-readers"
    And 15 minutes have passed since the last evaluation
    Then the person holds no role and no permission
    And the audit trail contains "auth.idp-roles-derived" with the trigger "refresh"

  @ADR-032 @Q17
  Scenario: IdP unreachable at re-evaluation
    Given the realm group "oea-analysts" is mapped to the role "analyst"
    And the person bound to "dev-analyst" has the status "active"
    And "dev-analyst" has logged in through the provider
    When the provider cannot be reached
    And 15 minutes have passed since the last evaluation
    Then the person holds no role and no permission
    And the audit trail contains "auth.idp-groups-unusable" with the reason "idp-unreachable"

  @US-147 @AC7
  Scenario: Mapping deleted in OEA
    Given the realm group "oea-auditors" is mapped to the role "auditor"
    And the person bound to "dev-auditor" has the status "active"
    And "dev-auditor" has logged in through the provider
    When the mapping of "oea-auditors" is deleted
    And 61 seconds have passed
    Then the person holds no role and no permission

  @REQ-171 @SoD-8
  Scenario: Privileged role without second factor
    Given the realm group "oea-system-admins" is mapped to the role "system-admin"
    And the person bound to "dev-admin-no-mfa" has the status "active"
    When "dev-admin-no-mfa" logs in through the provider
    Then the role "system-admin" is listed as not effective with the reason "mfa-required"
    And a request needing "role:assign" is rejected with 403

  @REQ-171 @SoD-8
  Scenario: Privileged role with second factor
    Given the realm group "oea-system-admins" is mapped to the role "system-admin"
    And the person bound to "dev-system-admin" has the status "active"
    When "dev-system-admin" logs in through the provider
    Then the role "system-admin" is effective and the session is second-factor-verified

  @REQ-165 @REQ-001
  Scenario: IdP refresh token rejected
    Given the realm group "oea-viewers" is mapped to the role "viewer"
    And the person bound to "dev-viewer" has the status "active"
    And "dev-viewer" has logged in through the provider
    When the IdP ends all sessions of "dev-viewer"
    And 15 minutes have passed since the last evaluation
    Then the session is rejected with 401 and ended with the reason "idp-refresh-failed"

  @REQ-165 @REQ-001
  Scenario: IdP refresh token expired
    Given the person bound to "dev-viewer" has the status "active"
    And "dev-viewer" has logged in through the provider
    When the IdP refresh token of the session has expired
    Then the session is rejected with 401 and ended with the reason "idp-refresh-failed"

  @US-147 @AC5
  Scenario: Own group rejected
    Given the setup phase of the instance has ended
    And the realm group "oea-security-admins" is mapped to the role "security-admin"
    And the person bound to "dev-security-admin" has the status "active"
    And "dev-security-admin" has logged in through the provider
    When the person maps the realm group "oea-security-admins" to the role "viewer"
    Then the request is rejected with 403 and the reason "self-administration"
    And the audit trail contains "authz.denied" with the reason "self-administration"
