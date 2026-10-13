# US-009: Passkey Login (Local Authentication)

**ID**: US-009
**Story Points**: 8
**Status**: backlog
**Sprint**: unassigned

## Story

As a person on an instance without an external identity provider, I want to sign in with a passkey, ideally without first entering a username or password, so that I get passwordless, phishing-resistant access.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-009: Passkey Login](../req/REQ-009-passkey-login.md)

## Acceptance Criteria

**AC1**:

- Given: a person with a previously registered passkey
- When: they sign in with this passkey
- Then: they receive a session according to their active role assignments

**AC2**:

- Given: a failed passkey verification
- When: the login attempt is completed
- Then: no session is created, error message indistinguishable from other error cases

**AC3**:

- Given: a person with a passkey registered as a discoverable credential
- When: they sign in
- Then: no prior entry of username or password is required

## Technical Notes

- Affected components: auth module, WebAuthn client/server library
- Affected EntityTypes/Relations: passkey public keys (separate from the `Person` object, sensitive credential data)
- API endpoints: WebAuthn challenge/assertion endpoints
- Database changes: separate table for passkey credentials per person

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (WebAuthn test client with virtual authenticator)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001 (shared role lookup schema), US-022 (passkey enrollment must exist so that passkeys are available)
- Blocks: none

## Notes

Enrollment (registration of a passkey) is explicitly not part of this story – UC-01 assumes a completed registration. Enrollment is a separate use case, not derived here.
