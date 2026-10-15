# US-016: Audit Log Entry for the Bootstrapping Process

**ID**: US-016
**Story Points**: 2
**Status**: review
**Sprint**: S1
**Implementation**: OpenSpec change `us-013-local-bootstrapping` (commit hash added when the change is archived) – local mode; the remote mode is audited together with US-014

## Story

As an operator, I want every bootstrapping process to be logged, so that I can prove when and how the instance's first privileged access came into existence.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-016: Audit Log Entry for Bootstrapping Process](../req/REQ-016-audit-log-bootstrapping.md)

## Acceptance Criteria

**AC1**:

- Given: a successfully completed bootstrapping process (local or remote)
- When: it is completed
- Then: the audit log contains an entry with timestamp, mode and result `success`

**AC2**:

- Given: a rejected bootstrapping attempt
- When: the rejection occurs
- Then: the audit log contains an entry with timestamp and result `rejected`

## Technical Notes

- Affected components: audit log module, bootstrapping endpoint
- Affected EntityTypes/Relations: no business ones
- API endpoints: bootstrapping endpoint
- Database changes: the audit log must be functional before any Person/Role configuration

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (successful and rejected attempts)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-013, US-015
- Blocks: none

## Notes

The audit log mechanism should be shared with US-005 (login audit log), but must be ready for use before any regular Person/Role configuration.
