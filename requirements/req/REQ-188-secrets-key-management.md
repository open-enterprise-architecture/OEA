---
id: REQ-188
title: Secrets and encryption key management
type: constraint
priority: must
status: proposed
version: 0.2.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-02
    - UC-03
  business_objects:
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
    - ADR-026
supersedes: []
superseded_by: []
---

# REQ-188: Secrets and Encryption Key Management

## Statement

Secrets stored by the application (TOTP secrets, OIDC client secrets, webhook secrets, license key) **MUST** be encrypted with AES-256-GCM using a key-encryption key supplied from outside the database (file, environment or key management system), **MUST** support key rotation without data loss, **MUST** never be returned by any API, and the key-encryption key **MUST NOT** be included in database backups.

## Rationale

If the key lies next to the database dump in the same backup, whoever obtains the backup can decrypt all TOTP secrets and log in as anyone once password hashes are cracked. Plaintext OIDC client secrets are exposed by any SQL injection.

## Context

Source: security review SEC-26, SEC-27 (proposal PR-32). Client secrets of service accounts are hashed (REQ-176), not encrypted. REQ-129 refers to this REQ for at-rest encryption done by OEA itself.

## Type-Specific Fields

**Type of constraint**: technical

**Source**: security review 2026-10 (SEC-26, SEC-27); NIST SP 800-57

**Binding strength**: hard

**Impact of non-compliance**: mass credential compromise from a database dump or backup

**Documented exception (ADR-037 §5)**: the HMAC key for the analytics request context is stored in `reporting_ctx.keys` inside the database. It protects only the integrity of an analyst's masking context, not data at rest, and is rotated at least every 90 days.

## Acceptance Criteria

**AC1**:

- Given: a database dump
- When: searched for any stored OIDC client secret or TOTP secret in plaintext or Base32
- Then: none is found

**AC2**:

- Given: a key rotation command with old and new key configured
- When: executed
- Then: all secrets are re-encrypted and logins with existing TOTP credentials still succeed

**AC3**:

- Given: the authentication provider and settings read endpoints
- When: called
- Then: no client secret or license key value is returned (only a "set/not set" indicator)

**AC4**:

- Given: the documented backup procedure (REQ-082)
- When: a backup is created
- Then: the key-encryption key is not part of the backup output

## Verification Method

- [ ] Method: test (automated) + DB inspection
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes (AC1–AC3)

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-022, REQ-082, REQ-129, REQ-147
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: credential compromise from backups – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-26/SEC-27 (PR-32) |
| 0.2.0 | 2026-10-13 | Lukas Mathis | Documented exception for the analytics context key (ADR-037 §5) |
