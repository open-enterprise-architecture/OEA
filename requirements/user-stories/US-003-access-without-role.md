# US-003: Restricted Access Without an Active Role Assignment

**ID**: US-003
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a person without an active role assignment, I want to still be able to sign in, so that I receive a notice that no role has been assigned to me yet.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-003: Restricted Access Without Role Assignment](../req/REQ-003-access-without-role-assignment.md)

## Acceptance Criteria

**AC1**:

- Given: a successfully authenticated person without an active role assignment
- When: they access the repository
- Then: every request for repository content is rejected with 403 and they see a notice that no role is assigned

**AC2**:

- Given: a person subsequently receives an active role assignment
- When: they make the next request after at most 60 seconds
- Then: their permissions reflect the new role

## Technical Notes

- Affected components: authorization module
- Affected EntityTypes/Relations: `persons`, authorization roles, role assignments and IdP group mappings (ADR-032); business `Role`/`RoleAssignment` EA content is not evaluated
- API endpoints: authorization check on every protected endpoint
- Database changes: no additional ones

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001
- Blocks: none

## Notes

Covers exception flow E3 from UC-01. Implements the concept constraint `person-minimum-role` as a warning, not a hard block. 2026-10-13: "publicly readable content" removed (REQ-003 v0.2.0, security review SEC-05, owner decision Q1 – no anonymous or public access in v1). 2026-10-13: "active role assignment" means an active authorization role per ADR-032 (REQ-003 v0.3.0), not a business RoleAssignment in the EA model.
