# US-019: Ability to Disable the Local System Admin Account

**ID**: US-019
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a system admin with a working remote mapping, I want to be able to disable the original local bootstrap account, so that after initial configuration the instance no longer has an additional, uncontrolled access left open.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-019: Ability to Disable the Local System Admin Account](../req/REQ-019-disable-local-admin.md)

## Acceptance Criteria

**AC1**:
- Given: an active local system admin account and a functioning remote mapping
- When: a system admin disables the local account
- Then: signing in via the local account is no longer possible afterwards, while the remote route continues to work

**AC2**:
- Given: no functioning remote mapping configured
- When: an attempt is made to disable the local account
- Then: the system warns of a possible complete lockout, but does not necessarily block the action

## Technical Notes

- Affected components: admin management UI/CLI
- Affected EntityTypes/Relations: `SystemAdminAccount.active`
- API endpoints: management endpoint for system admin accounts
- Database changes: no additional ones (lifecycle transition `active → deactivated`)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (disabling with/without functioning remote mapping)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-013, US-014, US-018
- Blocks: none

## Notes

Warning instead of hard block, so as not to cause a lockout ourselves (UC-02, E4).
