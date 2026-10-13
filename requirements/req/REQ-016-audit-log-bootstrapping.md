---
id: REQ-016
title: Audit log entry for the bootstrapping process
type: compliance
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-02
  business_objects:
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

# REQ-016: Audit log entry for the bootstrapping process

## Statement

The system MUST record every completed or rejected bootstrapping process in the audit log with timestamp, chosen mode (local/remote), and result.

## Rationale

The bootstrapping process creates the first privileged access of an instance – a security-critical event that must be traceable, also because no regular RBAC applies at that point yet. UC-02, postconditions on success and exception flows.

## Context

Applies to the Basic Flow (local), Alternative A1 (remote) and A2 (combined), as well as to rejected attempts (E3, REQ-015).

## Type-Specific Fields

### For type=compliance

**Rule set**: OEA's internal audit/traceability principle (see concept §22 Analyzability), compatible with ISO 27001 / DORA audit trail requirements

**Specific provision**: Every bootstrapping process (successful or rejected) must be logged with timestamp, mode, and result

**Evidence requirement**: The audit log must also be accessible for events before any regular person/role configuration (e.g., via a local log file or database, not exclusively via a UI that requires RBAC)

**Audit relevance**: yes, particularly high relevance, since bootstrapping creates the first privileged access

## Acceptance Criteria

**AC1**:
- Given: a successfully completed local or remote bootstrapping process
- When: it is completed
- Then: the audit log contains an entry with timestamp, mode, and result `success`

**AC2**:
- Given: a rejected bootstrapping attempt (e.g., due to REQ-015)
- When: the rejection occurs
- Then: the audit log contains an entry with timestamp and result `rejected`

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: check bootstrapping attempts (successful, rejected) against the audit log content
- [x] Measurement tool: test suite + audit log reader
- [x] Pass criterion: AC1, AC2 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-013, REQ-014, REQ-015
- **Downstream effects**: none known
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without logging of the bootstrapping process, the forensic basis is missing if a system admin access was created abusively – severe

## Trade-offs

- none known

## Implementation Notes

- The audit log mechanism should be the same as in REQ-005 (UC-01), but must already work before regular person/role data exist

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-02, postconditions on success.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-02 |
