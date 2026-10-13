# US-008: Meet and Measure Login Latency Targets

**ID**: US-008
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want the login process to stay within a defined latency budget, so that daily use of the tool is not impaired by slow sign-in.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-008: Login Latency](../req/REQ-008-login-latency.md)

## Acceptance Criteria

**AC1**:

- Given: a repository with 10,000 persons and 1,000 roles
- When: 95% of OIDC login processes are measured
- Then: the server-side processing time is below 300ms

**AC2**:

- Given: a repository with 1,000 active service accounts
- When: 95% of client credentials token requests are measured (server-side portion)
- Then: the processing time is below 300ms

## Technical Notes

- Affected components: auth module, caching layer for role assignments
- Affected EntityTypes/Relations: `Person`, `Role`, `RoleAssignment`
- API endpoints: all login/auth endpoints
- Database changes: additional indexes if needed, cache layer (solution open, no solution design here)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Load test with synthetic dataset written and integrated into CI as a performance gate
- [ ] Documentation updated
- [ ] ADR created if architecture decision (e.g. cache strategy)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001, US-142 (US-002 rejected)
- Blocks: none

## Notes

Target values are preliminary walking skeleton assumptions (see REQ-008) and must be validated with real load measurement. Should only be meaningfully measurable once US-001/US-142 are in place. 2026-10-13: AC2 changed from API key validation to client credentials token issuance (REQ-008 v0.2.0, security review SEC-15 / owner decision Q5).
