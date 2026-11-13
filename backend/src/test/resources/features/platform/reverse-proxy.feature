# Spec: openspec platform/deployment
# Source material: security-reviews/application-audit-2026-11.md AA-01; REQ-168, REQ-175, REQ-187

@REQ-168 @REQ-175 @REQ-187 @platform
Feature: Client address behind a trusted reverse proxy

  The operator runs the backend behind a TLS reverse proxy. The proxy connects
  from a trusted address (here loopback, part of the default list) and forwards
  the client address, the scheme and the host in X-Forwarded-* headers. The
  throttling, the audit trail and the origin check see the client, not the
  proxy.

  Background:
    Given an instance behind a trusted reverse proxy with a local system admin

  Scenario: Two clients behind the trusted proxy are throttled separately
    When 100 logins with different unknown login names fail through the proxy from a first client address
    And the system admin logs in through the proxy from the first client address
    And the system admin logs in through the proxy from a second client address
    Then the login from the first client address is answered with 401 and a waiting time
    And the login from the second client address succeeds

  Scenario: Throttling just below the limit
    When 99 logins with different unknown login names fail through the proxy from a first client address
    And the system admin logs in through the proxy from the first client address
    Then the login from the first client address succeeds

  Scenario: Audit source address
    When a login with an unknown login name fails through the proxy from a first client address
    Then the audit event "auth.login-failed" records the first client address as source address

  Scenario: Origin behind a TLS proxy
    Given the system admin is logged in through the proxy at "https://oea.example.org"
    When the system admin logs out through the proxy with the origin "https://oea.example.org"
    Then the logout is answered with 200
    And the session has ended

  Scenario: Foreign origin behind a TLS proxy
    Given the system admin is logged in through the proxy at "https://oea.example.org"
    When the system admin logs out through the proxy with the origin "https://evil.example.org"
    Then the logout is answered with 403
    And the session is still valid
