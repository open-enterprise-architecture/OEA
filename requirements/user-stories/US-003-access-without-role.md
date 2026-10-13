# US-003: Restricted Access Without an Active Role Assignment

**ID**: US-003
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a person without an active role assignment, I want to still be able to sign in, so that I can at least see publicly readable content and receive a notice that no role has been assigned to me yet.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-003: Restricted Access Without Role Assignment](../req/REQ-003-access-without-role-assignment.md)

## Acceptance Criteria

**AC1**:

- Given: a successfully authenticated person without an active role assignment
- When: they access the repository
- Then: they receive access only to publicly readable content and a notice that no role is assigned

**AC2**:

- Given: a person subsequently receives an active role assignment
- When: they sign in again or the session is refreshed
- Then: their permissions reflect the new role

## Technical Notes

- Affected components: authorization module
- Affected EntityTypes/Relations: `Person`, `Role`, `RoleAssignment`
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

Covers exception flow E3 from UC-01. Implements the concept constraint `person-minimum-role` as a warning, not a hard block.
