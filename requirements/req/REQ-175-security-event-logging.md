---
id: REQ-175
title: Security event logging
type: compliance
priority: must
status: proposed
version: 0.1.0
created: 2026-10-13
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-14
    - UC-01
  business_objects:
    - person
    - role
  business_rules: []
  stakeholders:
    - SH-05
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-024
supersedes: []
superseded_by: []
---

# REQ-175: Security Event Logging

## Statement

The system **MUST** record in the audit log, with timestamp, principal, source address, outcome and correlation ID: logout and session revocation, authorization failures (403), role and role-assignment changes, authentication provider and settings changes, visibility configuration changes, service account creation, rotation and deactivation, throttling and lockout events, analytics queries and bulk exports; application logs **MUST NOT** contain secrets, tokens, passwords or TOTP codes.

## Rationale

Without these events an incident responder cannot reconstruct a 403 probe storm, a self-granted role or which service account wrote which entities.

## Context

Source: security review SEC-14 (proposal PR-19). Existing audit REQs: REQ-005 (login), REQ-016 (bootstrap), REQ-025 (enrollment), REQ-031 (reset), REQ-128 (override reads).

## Type-Specific Fields

### For type=compliance

**Regulatory framework**: ISO/IEC 27001:2022, DORA

**Specific provision**: ISO/IEC 27001 Annex A 8.15 (logging), A 8.16 (monitoring); DORA Art. 12

**Evidence requirement**: event catalog document; sample export; CI log scan result

**Audit relevance**: yes

## Acceptance Criteria

**AC1**:

- Given: a viewer calls an admin endpoint
- When: the request is rejected with 403
- Then: an audit event `authz.denied` with principal, operation and target ID exists

**AC2**:

- Given: an admin assigns a role to a person
- When: saved
- Then: an audit event `role.assigned` with actor, target person, role and validity exists

**AC3**:

- Given: the full test suite runs with log capture
- When: the logs are scanned for the test passwords, TOTP secrets, client secrets and bearer tokens used in the tests
- Then: none is found

**AC4**:

- Given: audit events for a period
- When: exported via the API by a principal with `audit:read`
- Then: the export is available as JSON Lines suitable for SIEM ingestion, and the export itself is audited

## Verification Method

- [ ] Method: test (automated) + log scan in CI
- [ ] Pass criterion: AC1–AC4 green
- [ ] Integrated in CI: yes

## Dependencies

- **Prerequisites**: REQ-151, REQ-174
- **Follow-on effects**: REQ-161, REQ-164, REQ-168, REQ-176, REQ-179
- **Conflicts**: REQ-189 (personal data in audit records is pseudonymized)

## Risks of Non-Fulfillment

- Risk 1: undetectable abuse and missing forensic evidence – medium

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-10-13 | requirements-engineer | Initial draft from security review SEC-14 (PR-19) |
