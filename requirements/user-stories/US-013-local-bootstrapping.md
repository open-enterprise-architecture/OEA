# US-013: Local Bootstrapping When No System Admin Account Exists

**ID**: US-013
**Story Points**: 5
**Status**: done
**Sprint**: S1
**Implementation**: OpenSpec change `us-013-local-bootstrapping`, commit c962535 (#24) – AC1 implemented; OpenSpec change `us-013-system-admin-login`, commit 1fafd95 (#31) – AC2 implemented (login during the setup phase of ADR-042, TOTP enrollment, session)

## Story

As an operator of a freshly installed OEA instance, I want to be able to set up a local system admin account, so that I can configure the instance without having to connect an external identity provider first.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-013: Local Bootstrapping When No System Admin Account Exists](../req/REQ-013-local-bootstrapping.md)

## Acceptance Criteria

**AC1**:

- Given: a freshly installed instance without a system admin account
- When: it is started or accessed for the first time
- Then: the system offers the local bootstrapping process without requiring an external IdP

**AC2**:

- Given: a completed local bootstrapping process
- When: the operator signs in with the defined credential
- Then: they gain access with system admin permissions

## Technical Notes

- Affected components: setup wizard/CLI, auth module
- Affected EntityTypes/Relations: `SystemAdminAccount` (see `system-admin-account.md`)
- API endpoints: bootstrapping endpoint (reachable only as long as no account exists)
- Database changes: new table/collection for `SystemAdminAccount`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (integration test on a fresh instance)
- [ ] Documentation updated (installation guide)
- [ ] ADR created if architecture decision (see ADR-006)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: none
- Blocks: US-014, US-015, US-016, US-017, US-019

## Notes

Covers the basic flow from UC-02. The setup token handover itself is the subject of US-017.
