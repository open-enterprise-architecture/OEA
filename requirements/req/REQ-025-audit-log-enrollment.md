---
id: REQ-025
title: Audit log entry for every enrollment process
type: compliance
priority: must
status: proposed
version: 0.3.0
created: 2026-06-25
last_updated: 2026-10-30
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - person
  business_rules: []
  stakeholders:
    - SH-03
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-025: Audit log entry for every enrollment process

## Statement

The system MUST create an audit log entry with timestamp, affected person ID, method set up, and result for every completed enrollment process – successful or failed; credential values (secrets, hashes, public keys) MUST NOT appear in the audit log.

## Rationale

Registering an authentication method is a security-critical event that must be fully traceable: who set up which method and when? In the event of a security incident (e.g., enrollment by an unauthorized person after token compromise), the time and method must be reconstructable from the audit log. Corresponds to OEA's traceability philosophy (concept §22) and complements REQ-005 (audit log for login).

## Context

Concerns all enrollment paths from UC-03: TOTP (REQ-022), passkey (REQ-023), password (REQ-024), and the addition of further methods for authenticated persons (REQ-026). Failed attempts (e.g., wrong TOTP verification code, passkey cancellation) must also be logged, but without the plain-text value of the entered code.

## Type-Specific Fields

### For type=compliance

**Rule set**: OEA's internal audit/traceability principle (concept §22); compatible with ISO 27001 Annex A.8.15 (Logging) and DORA Art. 10 (ICT security)

**Specific provision**: Every enrollment process must be logged with timestamp, affected identity, method type, and result

**Evidence requirement**: The audit log must be retrievable and exportable over the instance-wide configured retention period

**Audit relevance**: yes; enrollment events are part of the security event log

## Acceptance Criteria

**AC1**:

- Given: an enrollment process is completed successfully (REQ-022, REQ-023, or REQ-024)
- When: the method is persisted
- Then: the audit log contains an entry with: timestamp (ISO 8601), person ID, method type (totp | passkey | password), result `success`

**AC2**:

- Given: an enrollment process fails (e.g., TOTP verification failed, passkey cancellation, password too weak)
- When: the failure occurs
- Then: the audit log contains an entry with timestamp, person ID (from the login transaction), method type, result `failure`, and failure reason (generic, without sensitive details)

**AC3**:

- Given: an audit log entry for an enrollment process
- When: it is stored in the log infrastructure
- Then: it contains no credential values (no TOTP secret, no password hash, no public key)

**AC4**:

- Given: an enrollment request is submitted without a valid required-action state (no login transaction with a pending enrollment required action, or an expired one)
- When: the request is rejected
- Then: the audit log contains an entry with timestamp, result `failure`, failure reason `invalid_required_action_state`, and the person ID only if the login transaction identifies one

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: check enrollment attempts (success and failure) against the audit log content
- [x] Measurement tool: test suite of the enrollment module + audit log reader
- [x] Pass criterion: AC1–AC4 green for all enrollment paths
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-022, REQ-023, REQ-024 (REQ-021 was token validation and was rejected; the person ID context now comes from the JIT login flow)
- **Downstream effects**: later use cases on audit reports (concept §22)
- **Conflicts**: REQ-022 AC4 / REQ-024 AC4 (the audit log must not contain secrets – covered by AC3)

## Risks of Non-Fulfillment

- Risk 1: Without an enrollment audit log, a subsequent compromise analysis (when was which method registered?) is not possible – severe
- Risk 2: Credentials in the audit log would be a separate leak vector – severe (mitigated by AC3)

## Trade-offs

- None: audit logging for security-critical events is non-negotiable

## Implementation Notes

- Realized for TOTP by OpenSpec change `us-021-person-totp`: `auth.factor-added` (AC1) and `auth.factor-enrollment-failed` with reason `invalid-code` or `enrollment-expired` (AC2); no secret or code in any event (AC3). AC4 does not apply without required-action enrollment states; passkeys follow with S5 change 4.
- The audit log schema should be consistent with REQ-005 (login audit): same fields, same formats
- Method type as an enum value, not free text (avoids typos in log analysis)
- AC4 (invalid required-action state): repeated attempts are additionally subject to throttling (REQ-168)

## Implementation

- ADR(s): none
- Implemented by: OpenSpec change `us-021-person-totp`
- Tests: –
- Verification status: not-verified

## Notes

Derived from UC-03 postconditions (audit log entry) and analogous to REQ-005 (audit log for login). Deliberately classified as type `compliance`, since audit logging is a traceability requirement, not a functional core requirement.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft from UC-03 |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-14: AC4 no longer references the rejected enrollment token (REQ-021); replaced by invalid required-action state |
| 0.3.0 | 2026-10-30 | requirements-engineer | Implementation note (change `us-021-person-totp`) |
