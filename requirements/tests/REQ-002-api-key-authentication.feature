# Derived from: requirements/req/req-002-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-01
Feature: REQ-002 – API key authentication for machine-to-machine access

  The system SHOULD be able to validate requests with a valid API key
  directly, without a redirect to an OIDC identity provider, and
  determine the identity assigned to the API key together with its
  active role assignments.

  Background:
    Given an OEA instance with a configured authentication mechanism
    And a person with email "test@example.com" and an active role exists

  @AC1
  Scenario: AC1 – a request contains a valid, non-revoked API key
    Given a request contains a valid, non-revoked API key
    When  the request is made against a protected resource
    Then  it is authorized without a redirect, according to the roles of the assigned identity

  @AC2
  Scenario: AC2 – a request contains an invalid or revoked API key
    Given a request contains an invalid or revoked API key
    When  the request is made
    Then  it is rejected without an authorization context being created
