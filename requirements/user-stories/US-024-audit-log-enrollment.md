# US-024: Log Enrollment Processes in the Audit Log

**ID**: US-024
**Story Points**: 2
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-021-person-totp`, commit 672fdae (#139) – audit events `auth.factor-added` and `auth.factor-enrollment-failed` for TOTP; passwords through `person.password-set` and `auth.password-changed`

## Story

As an operator, I want every enrollment process to be recorded in the audit log, so that in a security incident I can trace who set up which authentication method and when.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-025: Audit Log Entry for Every Enrollment Process](../req/REQ-025-audit-log-enrollment.md)

## Acceptance Criteria

**AC1**:

- Given: an enrollment process completes successfully (TOTP, passkey or password)
- When: the method is persisted
- Then: the audit log contains an entry with timestamp (ISO 8601), person ID, method type (`totp` | `passkey` | `password`), result `success`

**AC2**:

- Given: an enrollment process fails (wrong TOTP code, passkey cancellation, password too weak)
- When: the failure occurs
- Then: the audit log contains an entry with timestamp, person ID, method type, result `failure` and a generic failure reason

**AC3**:

- Given: an invalid enrollment token is submitted
- When: the failure occurs
- Then: the audit log contains an entry with timestamp, result `failure`, failure reason `invalid_token`; no person ID (cannot be determined)

**AC4**:

- Given: an audit log entry for an enrollment process
- When: it is stored in the log infrastructure
- Then: it contains no credential values (no TOTP secret, no password hash, no public key)

## Technical Notes

- Affected components: audit log module, enrollment module (all paths: TOTP, passkey, password, token validation)
- Affected EntityTypes/Relations: audit log schema (consistent with REQ-005/US-005: same table structure / same append-only store)
- API endpoints: no dedicated endpoint; logging happens internally in every enrollment endpoint
- Database changes: new event types in the audit log (`enrollment_success`, `enrollment_failure`, `enrollment_token_invalid`)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (success and failure for all three methods + invalid token; verify credential-freedom of the log)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-021, US-022, US-023 (at least one enrollment path must exist); US-005 (login audit log as reference for schema consistency)
- Blocks: none

## Notes

Realigned by OpenSpec change `us-021-person-totp`: a confirmed TOTP enrollment is `auth.factor-added`, a failed confirmation `auth.factor-enrollment-failed` with reason `invalid-code` or `enrollment-expired`; password "enrollments" were already audited. AC3 (invalid enrollment token) does not apply, because there are no enrollment tokens. Passkey enrollments follow with S5 change 4 and use the same events.

Should be extensible in parallel with the completion of each enrollment story – similar to the recommendation in US-005 to proceed iteratively. Schema consistency with the login audit log (US-005) is important for later audit report use cases (concept §22).
