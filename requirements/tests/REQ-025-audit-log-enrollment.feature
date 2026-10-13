# Derived from: requirements/req/req-025-*.md
# V-model system test specification – created 2026-06-30
# Verification: Method: test (automated)

@compliance @must @UC-03
Feature: REQ-025 – Audit log entry for every enrollment process

  The system MUST create an audit log entry for every completed
  enrollment process – successful or failed – with timestamp, affected
  person ID, method set up, and result; credential values (secrets,
  hashes, public keys) MUST NOT appear in the audit log.

  @AC1
  Scenario: AC1 – an enrollment process is completed successfully (REQ-022, REQ-023 or...
    Given an enrollment process is completed successfully (REQ-022, REQ-023 or REQ-024)
    When  the method is persisted
    Then  the audit log contains an entry with: timestamp (ISO 8601), person ID, method type (totp | passkey | password), result `success`

  @AC2
  Scenario: AC2 – an enrollment process fails (e.g. TOTP verification failed, passkey c...
    Given an enrollment process fails (e.g. TOTP verification failed, passkey cancellation, password too weak)
    When  the failure occurs
    Then  the audit log contains an entry with timestamp, person ID (as far as determinable from the token), method type, result `failure` and failure reason (generic, without sensitive details)

  @AC3
  Scenario: AC3 – an audit log entry for an enrollment process
    Given an audit log entry for an enrollment process
    When  it is stored in the log infrastructure
    Then  it contains no credential values (no TOTP secret, no password hash, no public key)

  @AC4
  Scenario: AC4 – an invalid enrollment token is submitted (E1 from UC-03)
    Given an invalid enrollment token is submitted (E1 from UC-03)
    When  the failure occurs
    Then  the audit log contains an entry with timestamp, result `failure`, failure reason `invalid_token`; no person ID (token is invalid, person not determinable)
