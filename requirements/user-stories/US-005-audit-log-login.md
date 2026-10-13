# US-005: Audit Log Entry for Every Login Attempt

**ID**: US-005
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want every login attempt to be logged, so that in an audit I can trace who signed in when and with which mechanism – successfully or not.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-005: Audit Log Entry for Every Login Attempt](../req/REQ-005-audit-log-login.md)

## Acceptance Criteria

**AC1**:

- Given: a successful login attempt (OIDC, API key, passkey, local)
- When: it is completed
- Then: the audit log contains an entry with timestamp, mechanism, result `success` and identity reference

**AC2**:

- Given: a failed login attempt
- When: the failure occurs
- Then: the audit log contains an entry with timestamp, mechanism, result `failure` and – as far as possible without disclosing security-relevant details – the failure reason

## Technical Notes

- Affected components: audit log module, auth module (all mechanisms)
- Affected EntityTypes/Relations: no business ones, separate audit log schema
- API endpoints: all login endpoints (OIDC callback, API key, passkey, local)
- Database changes: audit log table/append-only store

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration across all mechanisms)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001, US-002 (at least one login mechanism must exist in order to log)
- Blocks: none

## Notes

Should be extended in parallel with every new login mechanism (passkey, TOTP, local) – possibly iteratively rather than in one step for all mechanisms.
