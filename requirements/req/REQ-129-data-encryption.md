---
id: REQ-129
title: Data Encryption (at-rest and in-transit)
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-01
    - UC-21
  business_objects:
    - entity
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-129: Data Encryption (at-rest and in-transit)

## Statement

The system MUST cryptographically protect all stored data (at-rest) and all data transports (in-transit):

**At-rest**:
- All persisted data (database, backups, export files in storage) MUST be encrypted with **AES-256** or an equivalent, recognized method
- Backups MUST be stored encrypted; unencrypted backup files MUST NOT remain on storage media
- Encryption keys MUST NOT be hardcoded; they MUST be configurable via environment variables or a secrets management system

**In-transit**:
- All HTTP connections (browser ↔ API, API ↔ database, API ↔ object storage) MUST be handled over **TLS 1.2** or higher
- Unencrypted HTTP connections to the API server MUST be redirected to HTTPS (301 redirect) or blocked
- TLS certificates MUST be configurable (own certificates, Let's Encrypt, proxy termination)

## Rationale

UC-21 shows that OEA manages sensitive data (investment costs, security classifications). Without encryption, this data is unprotected in the event of database dumps, backup exfiltration, or man-in-the-middle attacks. For self-hosted operators (SH-06), their own infrastructure is the primary attack surface.

## Context

Applies to all deployment scenarios (self-hosted, SaaS). Encryption at the operating system/storage level (e.g. LUKS, AWS EBS Encryption) is the operator's responsibility and is not the subject of this REQ. OEA ensures that backups and exports are encrypted before they are handed over to storage.

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable Target Values**:

| Metric | Target Value | Scope |
|---|---|---|
| Minimum TLS version | TLS 1.2 | all external connections |
| Unencrypted HTTP requests | 0 in production | API server |
| Backup encryption | 100 % of backup files | all backup targets |
| Hardcoded keys in source code | 0 | codebase, Docker images |

## Acceptance Criteria

**AC1** (TLS enforced):
- When: a browser sends an HTTP request to the OEA API (port 80)
- Then: the response is a 301 redirect to HTTPS or the connection is refused; no plaintext response

**AC2** (TLS version):
- When: a TLS scan is performed against the API endpoint
- Then: TLS 1.0 and TLS 1.1 are rejected; TLS 1.2 and 1.3 are accepted

**AC3** (Backup encrypted):
- When: the backup routine is executed
- Then: the backup file is AES-256-encrypted; no plaintext dump on storage

**AC4** (No hardcoded keys):
- When: static code analysis (secrets scanner) runs against the source code
- Then: no hits for encryption keys, passwords, or tokens in the source code or in Docker images

## Verification Method

- [x] Method: security-test (TLS scan via `testssl.sh` or `sslyze`)
- [x] Method: secrets-scan (Trufflehog or gitleaks in CI)
- [x] Method: backup-integration-test (check of the backup header magic bytes)
- [x] Integrated in CI: yes (secrets scanner), no (TLS scan — production environment)

## Dependencies

- **Prerequisites**: deployment architecture (TLS termination: direct or via reverse proxy)
- **Consequences**: REQ-082 (backup retention) — encrypted backups must remain decryptable (consider key rotation)

## Risks of Non-Fulfillment

- Data leak in the event of a database dump or backup exfiltration; sensitive properties (UC-21) readable in plaintext
- Compliance violation for GDPR-relevant data (personal reference via `person` entities)

## Trade-offs

- Proxy-based TLS termination (nginx, Traefik) is the standard route for self-hosted operators; OEA explicitly supports this pattern — direct TLS configuration in the app server is optional
- Key rotation for backup encryption requires re-encryption of older backups; the process must be documented

## Implementation

- ADR(s): –
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
