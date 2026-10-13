# Derived from: requirements/req/req-129-*.md
# V-model system test specification – created 2026-06-30
# Verification: method: security-test (TLS scan via `testssl.sh` or `sslyze`)

@non-functional @performance @must @UC-01 @UC-21
Feature: REQ-129 – Data encryption (at-rest and in-transit)

  The system MUST cryptographically protect all stored data (at-rest) and all
  data transports (in-transit): **At-rest**: -
  All persisted data (database, backups, export files in
  storage) MUST be encrypted with **AES-256** or an equivalent,
  recognized method - Backups MUST be stored
  encrypted; unencrypted backup files MUST NOT
  remain on data carriers - Encryption keys MUST NOT
  be hardcoded; they MUST be configurable via environment variables or a
  secrets management system **In-transit**: - All
  HTTP connections (browser ↔ API, API ↔ database, API ↔ object
  storage) MUST be handled via **TLS 1.2** or higher -
  Unencrypted HTTP connections to the API server MUST be redirected to HTTPS
  (301 redirect) or blocked - TLS certificates
  MUST be configurable (own certificates, Let's Encrypt,
  proxy termination)

  @AC1
  Scenario: AC1 – TLS enforced – browser sends HTTP request to OEA API (port 80)
    Given the precondition according to the requirement is met
    When  browser sends HTTP request to OEA API (port 80)
    Then  response is a 301 redirect to HTTPS or connection is refused; no plaintext response

  @AC2
  Scenario: AC2 – TLS version – TLS scan is performed against the API endpoint
    Given the precondition according to the requirement is met
    When  TLS scan is performed against the API endpoint
    Then  TLS 1.0 and TLS 1.1 are rejected; TLS 1.2 and 1.3 are accepted

  @AC3
  Scenario: AC3 – Backup encrypted – backup routine is executed
    Given the precondition according to the requirement is met
    When  backup routine is executed
    Then  backup file is AES-256-encrypted; no plaintext dump on the storage

  @AC4
  Scenario: AC4 – No hardcoded keys – static code analysis (secrets scanner) runs against the source code
    Given the precondition according to the requirement is met
    When  static code analysis (secrets scanner) runs against the source code
    Then  no hits for encryption keys, passwords or tokens in the source code or in Docker images
