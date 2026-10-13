# US-017: Secure Handover of the Setup Token

**ID**: US-017
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want the initial setup token to be accessible only via a channel that already requires privileged access to the server, so that nobody without infrastructure access can take over the instance.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-017: Secure Handover of the Setup Token](../req/REQ-017-secure-setup-token-handover.md)

## Acceptance Criteria

**AC1**:
- Given: a freshly installed instance
- When: the setup token is generated
- Then: it can only be retrieved via a channel that already requires privileged access (e.g. CLI output at startup, container exec)

**AC2**:
- Given: the initial setup web interface (if UI-based)
- When: it is accessed without a valid setup token
- Then: it refuses to set a password

## Technical Notes

- Affected components: setup wizard/CLI, container entrypoint
- Affected EntityTypes/Relations: no business ones
- API endpoints: setup endpoint with token check
- Database changes: short-lived setup token store (e.g. with expiry time)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person (with focus on security)
- [ ] Tests written (verification that the token cannot be retrieved via open network endpoints)
- [ ] Documentation updated (installation guide)
- [ ] ADR created if architecture decision (concrete handover channel)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-013
- Blocks: none

## Notes

The concrete handover channel (CLI stdout, temporary file, one-time setup URL) is recorded in UC-02 as an open question – should be decided via ADR or solution design before implementing this story.
