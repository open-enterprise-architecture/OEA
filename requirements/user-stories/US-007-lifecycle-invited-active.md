# US-007: Lifecycle Transition invited→active on First Successful Login

**ID**: US-007
**Story Points**: 1
**Status**: backlog
**Sprint**: unassigned

## Story

As a person with status "invited", I want to be automatically marked as "active" after my first successful login, so that my status in the repository reflects the actual onboarding progress.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-007: Lifecycle Transition invited→active](../req/REQ-007-lifecycle-transition-invited-active.md)

## Acceptance Criteria

**AC1**:
- Given: a person with status `invited`
- When: they sign in successfully for the first time
- Then: their status changes to `active`

**AC2**:
- Given: a person with status `active`
- When: they sign in again
- Then: their status remains unchanged at `active`

## Technical Notes

- Affected components: auth module, person lifecycle
- Affected EntityTypes/Relations: `Person.status`
- API endpoints: login endpoints (all mechanisms)
- Database changes: no additional ones

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001
- Blocks: none

## Notes

The transition must be idempotent (no error on repeated login).
