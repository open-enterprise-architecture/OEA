---
id: REQ-005
title: Audit log entry for every login attempt
type: compliance
priority: must
status: proposed
version: 0.1.0
created: 2026-06-24
last_updated: 2026-06-24
author: requirements-engineer
references:
  use_cases:
    - UC-01
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-005: Audit log entry for every login attempt

## Statement

The system MUST create an audit log entry with timestamp, authentication mechanism used, and result for every login attempt – successful or not.

## Rationale

UC-01 requires an audit log entry on success in its postconditions; Exception Flows E1, E2, and E4 require the same traceability on failure. Traceability of access attempts is the basis for audit preparation (see SH-03 pain point "audit reports are cobbled together") and for compliance evidence.

## Context

Concerns all paths from UC-01: successful OIDC login, API key login, and all error cases E1–E4.

## Type-Specific Fields

### For type=compliance

**Rule set**: OEA's internal audit/traceability principle (see concept §22 Analyzability); no specific external standard prescribed, but compatible with ISO 27001 / DORA audit trail requirements

**Specific provision**: Every authentication attempt must be logged traceably (timestamp, mechanism, result, affected identity where determinable)

**Evidence requirement**: The audit log must be retrievable and exportable over the retention period prescribed in the respective organization (specific period: open question, see below)

**Audit relevance**: yes, part of future audit reports (concept §22)

## Acceptance Criteria

**AC1**:

- Given: a login attempt (OIDC or API key) is performed
- When: it succeeds
- Then: the audit log contains an entry with timestamp, mechanism, result `success`, and identity reference

**AC2**:

- Given: a login attempt fails (E1, E2, or E4)
- When: the failure occurs
- Then: the audit log contains an entry with timestamp, mechanism, result `failure`, and – where possible without disclosing security-relevant details – the failure reason

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: check login attempts in all paths (success, E1, E2, E4) against the audit log content
- [x] Measurement tool: test suite of the auth module + audit log reader
- [x] Pass criterion: AC1, AC2 green for all paths
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-001, REQ-002, REQ-004
- **Downstream effects**: later use cases on audit reports (concept §22)
- **Conflicts**: REQ-006 (the audit log must not disclose security-relevant details such as account existence – level of detail must be aligned)

## Risks of Non-Fulfillment

- Risk 1: Without an audit trail, no forensic traceability in case of security incidents – severe
- Risk 2: Missing logging continues to make audit preparation difficult (unresolved pain point of SH-03)

## Trade-offs

- vs. REQ-006: audit log level of detail vs. avoiding outward user enumeration – the log itself may be more detailed internally than the error message visible externally

## Implementation Notes

- Retention period and export format are to be clarified in a separate NFR/requirement on audit reports, not to be anticipated here

## Implementation

- ADR(s): none
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-01 Login, postconditions, and all exception flows.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-24 | requirements-engineer | Initial draft from UC-01 |
