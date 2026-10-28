# Spec: openspec identity/access-administration (deactivation of the local system admin account)
# Source material: requirements/user-stories/US-019-disable-local-admin.md (AC1), ADR-032 SoD-7

@US-019 @UC-02 @oidc
Feature: Deactivating the local system admin account

  Once system administration works through a group of the identity provider, the
  local account of the bootstrapping can be deactivated. Its sessions end and its
  password no longer works, while the administrators of the IdP group keep access.

  Background:
    Given an OEA instance with the development realm as enabled OIDC provider

  @US-019 @AC1 @REQ-019
  Scenario: Deactivation with a working remote mapping
    Given the instance has the local system admin "admin" with a session
    And the realm group "oea-system-admins" is mapped to the role "system-admin"
    And the person bound to "dev-system-admin" has the status "active"
    And "dev-system-admin" has logged in through the provider
    When the person deactivates the local system admin account
    Then the local system admin account has the status "deactivated"
    And the session of the local system admin is rejected with 401
    And a login with the password of the local system admin fails
    And "dev-system-admin" still administers the instance after a new login
    And the audit trail contains the deactivation with the outcome "success"
