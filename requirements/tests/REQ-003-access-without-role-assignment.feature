# Derived from: requirements/req/req-003-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@business-rule @must @UC-01
Feature: REQ-003 – Restricted access without an active role assignment

  A successfully authenticated person WITHOUT an active role assignment
  MUST NOT access any content of the repository other than publicly
  readable content.

  @AC1
  Scenario: AC1 – a person is successfully authenticated but has no active role assignm...
    Given a person is successfully authenticated but has no active role assignment
    When  they access the repository
    Then  they receive access to publicly readable content only and a notice that no role is assigned

  @AC2
  Scenario: AC2 – a person subsequently receives an active role assignment
    Given a person subsequently receives an active role assignment
    When  they log in again or the session is refreshed
    Then  their permissions reflect the new role
