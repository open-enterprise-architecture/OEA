---
id: REQ-129
title: Data Encryption (at-rest and in-transit)
type: non-functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
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

- Volume and storage encryption of the database and file storage is the operator's duty; OEA MUST document it as a prerequisite and MUST log a startup warning in production mode that refers to this documentation
- OEA itself MUST encrypt stored credential secrets as specified in REQ-188 (AES-256-GCM with a key-encryption key supplied from outside the database)
- If OEA provides a backup or export command, its output MUST be encrypted with **AES-256** or an equivalent, recognized method, using a key that is not stored alongside the output
- Encryption keys MUST NOT be hardcoded; they MUST be configurable via file, environment variables or a secrets management system

**In-transit**:

- Every external listener of OEA (browser ↔ API, client ↔ API) MUST use **TLS 1.2** or higher, either directly or via a TLS-terminating reverse proxy
- The connection API ↔ database MUST use TLS 1.2 or higher when the database does not run on the same host or in the same container network namespace
- Unencrypted HTTP connections to the API server MUST be redirected to HTTPS (301 redirect) or blocked
- TLS certificates MUST be configurable (own certificates, Let's Encrypt, proxy termination)

## Rationale

UC-21 shows that OEA manages sensitive data (investment costs, security classifications). Without encryption, this data is unprotected in the event of database dumps, backup exfiltration, or man-in-the-middle attacks. For self-hosted operators (SH-06), their own infrastructure is the primary attack surface.

## Context

Applies to self-hosted deployments (Docker Compose, Kubernetes); there is no SaaS or multi-tenant offering (owner decision Q7 of 2026-10-13). Encryption at the operating system/storage level (e.g. LUKS, AWS EBS Encryption) is the operator's responsibility; OEA documents and reminds of it but cannot verify it. OEA ensures that backups and exports it produces itself are encrypted before they are handed over to storage.

Security review SEC-27 (2026-10-13): version 0.1.0 required "all persisted data encrypted with AES-256" while declaring storage encryption out of scope, which is contradictory and not testable for a PostgreSQL volume OEA does not control. Version 0.2.0 separates operator duties from what OEA itself guarantees.

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable Target Values**:

| Metric | Target Value | Scope |
|---|---|---|
| Minimum TLS version | TLS 1.2 | all external listeners; API ↔ database when not on the same host/network namespace |
| Unencrypted HTTP requests | 0 in production | API server |
| Encryption of backup/export files produced by OEA | 100 % | backup/export commands provided by OEA |
| Credential secrets stored in plaintext | 0 | database (see REQ-188) |
| Hardcoded keys in source code | 0 | codebase, Docker images |

## Acceptance Criteria

**AC1** (TLS enforced):

- When: a browser sends an HTTP request to the OEA API (port 80)
- Then: the response is a 301 redirect to HTTPS or the connection is refused; no plaintext response

**AC2** (TLS version):

- When: a TLS scan is performed against the API endpoint
- Then: TLS 1.0 and TLS 1.1 are rejected; TLS 1.2 and 1.3 are accepted

**AC3** (Backup encrypted):

- Given: a backup or export command provided by OEA
- When: it is executed
- Then: the output file is AES-256-encrypted with a key that is not written next to it; no plaintext dump remains on storage

**AC4** (No hardcoded keys):

- When: static code analysis (secrets scanner) runs against the source code
- Then: no hits for encryption keys, passwords, or tokens in the source code or in Docker images

**AC5** (Database TLS):

- Given: the database is configured on a different host than the API server
- When: the application connects without TLS or with TLS below 1.2
- Then: the connection is refused and startup fails with a clear message

**AC6** (Operator reminder):

- Given: the application starts in production mode
- When: the startup log is inspected
- Then: it contains a warning that refers to the operator documentation on volume and storage encryption

## Verification Method

- [x] Method: security-test (TLS scan via `testssl.sh` or `sslyze`)
- [x] Method: secrets-scan (Trufflehog or gitleaks in CI)
- [x] Method: backup-integration-test (check of the backup header magic bytes)
- [x] Integrated in CI: yes (secrets scanner), no (TLS scan — production environment)

## Dependencies

- **Prerequisites**: deployment architecture (TLS termination: direct or via reverse proxy)
- **Consequences**: REQ-082 (backup retention) — encrypted backups must remain decryptable (consider key rotation)
- **Related**: REQ-188 (secrets and key management), REQ-187 (HTTP security headers incl. HSTS)

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
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-27 / owner decision Q7: at-rest scope rewritten (operator duty for volumes, OEA encrypts credential secrets and its own backup/export output); DB TLS only across hosts; SaaS removed; AC5, AC6 added |
