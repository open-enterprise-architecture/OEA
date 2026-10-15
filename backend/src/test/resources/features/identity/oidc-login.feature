# Spec: openspec identity/oidc-login
# Source material: requirements/tests/REQ-001-oidc-login-session.feature, REQ-004, REQ-006, REQ-007

@US-001 @UC-01 @oidc
Feature: Login of persons through the configured OIDC provider

  A person logs in through the identity provider stored by the administrator.
  The backend runs the authorization code flow with PKCE, validates the ID
  token, and finds the person only through the binding of issuer and subject.
  The browser receives the session cookie and nothing else; every refused
  login looks the same from the outside and is audited with its reason.

  Background:
    Given an OEA instance with the development realm as enabled OIDC provider

  @US-001 @AC1
  Scenario: Successful login
    Given the person bound to "dev-viewer" has the status "active"
    When "dev-viewer" logs in through the provider
    Then the browser is sent to "/me" with only the session cookie
    And the audit trail contains "auth.login" with the method "oidc"

  @US-001 @AC2
  Scenario: Existing session
    Given the person bound to "dev-viewer" has the status "active"
    And "dev-viewer" has logged in through the provider
    When the current principal is requested with that session
    Then it names the person "dev-viewer" without a redirect to the provider

  @REQ-166 @AC3
  Scenario: Login without PKCE
    Given the person bound to "dev-viewer" has the status "active"
    When "dev-viewer" logs in through the provider with an authorization request without PKCE
    Then the browser is sent to the login page with the generic failure
    And no session exists

  Scenario: Only enabled providers offered
    Given a disabled provider "Old IdP"
    When the login page lists the providers
    Then only the enabled provider is listed, with identifier and name only

  @REQ-007 @AC1
  Scenario: First login of an invited person
    Given the person bound to "dev-architect" has the status "invited"
    When "dev-architect" logs in through the provider
    Then the browser is sent to "/me" with only the session cookie
    And the person "dev-architect" has the status "active"
    And the audit trail contains "person.activated" with the trigger "first-login"

  @REQ-166 @AC1
  Scenario: Matching e-mail with a different subject
    Given the person bound to "dev-architect" has the status "active"
    When "dev-email-twin" logs in through the provider
    Then the browser is sent to the login page with the generic failure
    And the audit trail contains "auth.login-failed" with the reason "identity-unknown"

  @REQ-004 @REQ-006
  Scenario Outline: Unknown and inactive identities
    Given the person bound to "dev-auditor" has the status "<status>"
    When "<user>" logs in through the provider
    Then the browser is sent to the login page with the generic failure
    And the audit trail contains "auth.login-failed" with the reason "<reason>"

    Examples:
      | user        | status     | reason            |
      | dev-auditor | inactive   | person-not-active |
      | dev-auditor | offboarded | person-not-active |
      | dev-no-role | active     | identity-unknown  |
