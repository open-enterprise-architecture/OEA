# Derived from: requirements/req/req-155-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: test (automated) + demonstration

@functional @should @UC-02
Feature: REQ-155 – Service account management for external systems

  The system **MUST** enable administrators to create service accounts,
  assign them the role `integration-writer` and issue
  rotating client credentials, so that external systems can access the write API without
  human user login.

  Background:
    Given a running OEA instance
    And a user with the required role is logged in

  @AC1
  Scenario: AC1 – Admin is logged in
    Given admin is logged in
    When  POST /admin/service-accounts with `{name: "ci-scanner", role: "integration-writer"}`
    Then  201 Created; response contains `clientSecret` once; DB contains `machine_credentials` entry

  @AC2
  Scenario: AC2 – Service account with valid client_id/secret
    Given service account with valid client_id/secret
    When  OAuth2 client credentials flow → JWT obtained; PUT /entities/by-external-id/...
    Then  200/201; audit log shows `source=API`, `agent_id=<client_id>`

  @AC3
  Scenario: AC3 – Admin rotates credentials of a service account
    Given admin rotates credentials of a service account
    When  old secret used for auth request
    Then  401 Unauthorized; new secret required
