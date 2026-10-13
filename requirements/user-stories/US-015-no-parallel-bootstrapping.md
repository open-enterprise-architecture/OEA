# US-015: No Parallel or Repeated Bootstrapping

**ID**: US-015
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want only one bootstrapping process per instance to complete successfully, so that no unauthorized second system admin account can come into existence.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-015: No Parallel or Repeated Bootstrapping](../req/REQ-015-no-parallel-bootstrapping.md)

## Acceptance Criteria

**AC1**:
- Given: two nearly simultaneously started bootstrapping attempts on the same fresh instance
- When: both compete for completion
- Then: exactly one completes successfully, the other is rejected

**AC2**:
- Given: an instance with an already existing system admin account
- When: a repeated bootstrapping attempt is made
- Then: it is rejected without an explicit reset action

## Technical Notes

- Affected components: bootstrapping endpoint
- Affected EntityTypes/Relations: `SystemAdminAccount`
- API endpoints: bootstrapping endpoint
- Database changes: atomic check-and-set mechanism (DB constraint or distributed lock)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person (with focus on concurrency)
- [ ] Tests written (concurrency test for AC1)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-013
- Blocks: none

## Notes

Covers exception flow E3 from UC-02. Security-critical – race condition tests are a mandatory part of testing, not optional.
