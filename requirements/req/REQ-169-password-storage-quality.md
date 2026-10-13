---
id: REQ-169
title: Password storage and password quality
type: non-functional
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-03
    - UC-02
  business_objects:
    - local-credential
    - system-admin-account
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-169: Password Storage and Password Quality

## Statement

The system **MUST** hash all local passwords, including the system admin password, with argon2id using at least the OWASP-recommended parameters, **MUST** rehash on successful login when parameters are outdated, **MUST** enforce a minimum length of 12 and accept a maximum of at least 64 characters, and **SHOULD** reject passwords contained in a configurable breached-password list.

## Rationale

"argon2id or bcrypt" without parameters is not testable; bcrypt silently truncates at 72 bytes while REQ-027 can generate longer passwords. Forced periodic rotation contradicts NIST SP 800-63B; length and breached-password checks are more effective.

## Context

Source: security review SEC-09 (proposal PR-13). REQ-024 and REQ-031 were aligned on 2026-10-13. REQ-028 (`expiryDays`) needs review against NIST SP 800-63B (follow-up).

## Type-Specific Fields

### For type=non-functional

**Category**: security

**Measurable target values**:

| Metric | Target value | Threshold (warning) | Threshold (critical) | Scope |
|---|---|---|---|---|
| Hash algorithm | argon2id | – | any other | all stored password credentials |
| argon2id parameters | m ≥ 19 MiB, t ≥ 2, p = 1 | – | below | all stored password credentials |
| Minimum length | 12 characters | – | < 12 configured | all local passwords |
| Maximum accepted length | ≥ 64 characters, no truncation | – | truncation | all local passwords |

## Acceptance Criteria

**AC1**:

- Given: any stored password credential
- When: the database is inspected
- Then: the hash string starts with `$argon2id$` and the parameters meet the minimum

**AC2**:

- Given: a stored hash with outdated parameters
- When: the user logs in successfully
- Then: the hash is replaced with one using current parameters

**AC3**:

- Given: a 100-character password
- When: it is set and used for login
- Then: login succeeds, and a password differing only in character 80 fails

**AC4**:

- Given: breached-password checking is enabled
- When: a password from the configured list (e.g. `Password123!`) is set
- Then: it is rejected

## Verification Method

- [ ] Method: test (automated) + DB inspection
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: none
- **Follow-on effects**: REQ-013, REQ-024, REQ-027, REQ-028, REQ-031
- **Conflicts**: REQ-008 (login latency) – argon2id cost must fit the latency budget

## Risks of Non-Fulfillment

- Risk 1: offline cracking of leaked hashes – medium

## Implementation Notes

- The breached-password list must be available offline (no external service by default, consistent with owner decision Q9)
- `business-objects/local-credential.md` ("argon2id or bcrypt") needs alignment (follow-up for business engineer)

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-09 (PR-13) |
