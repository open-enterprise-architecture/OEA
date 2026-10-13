# Derived from: requirements/req/req-001-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @must @UC-01
Feature: REQ-001 – OIDC-based login and session creation

  The system MUST redirect a person who has no valid session to a
  configured OIDC identity provider for authentication and, after
  successful authentication, create a session that is bound to the
  person and their active role assignments. The system MAY additionally
  perform an optional online validation against the identity provider
  on top of the local signature check of the identity token (e.g.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – a person without a valid session accesses a protected resource
    Given a person without a valid session accesses a protected resource
    When  they successfully authenticate with the configured OIDC provider
    Then  they receive a session that reflects their active role assignments

  @AC2
  Scenario: AC2 – a person already has a valid session
    Given a person already has a valid session
    When  they access another protected resource
    Then  no new redirect to the identity provider occurs

  @AC3
  Scenario: AC3 – an instance configured for online token validation against the IdP, a...
    Given an instance configured for online token validation against the IdP, and an identity token that is validly signed locally but has already been revoked at the IdP
    When  the login process is completed
    Then  session creation is refused

  @AC4
  Scenario: AC4 – a token issued by the external IdP with a validity period set by the IdP
    Given a token issued by the external IdP with a validity period set by the IdP
    When  OEA creates the session
    Then  OEA adopts this validity period unchanged, without shortening or extending it
