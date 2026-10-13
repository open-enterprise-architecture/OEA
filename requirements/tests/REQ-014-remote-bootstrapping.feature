# Derived from: requirements/req/req-014-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@functional @should @UC-02
Feature: REQ-014 – Remote bootstrapping via IdP claim mapping

  The system SHOULD make it possible to grant system admin permissions
  via a mapping to a group or role claim of a configured external
  identity provider (Entra ID or Authentik, see ADR-006) without
  requiring a local credential to be created for this purpose.

  Background:
    Given a freshly installed OEA instance without a system admin account

  @AC1
  Scenario: AC1 – a configured claim mapping to an IdP group
    Given a configured claim mapping to an IdP group
    When  a person with this group claim in the identity token logs in
    Then  the session receives system admin permissions

  @AC2
  Scenario: AC2 – the same mapping
    Given the same mapping
    When  a person without this claim logs in
    Then  the session does not receive system admin permissions via this route
