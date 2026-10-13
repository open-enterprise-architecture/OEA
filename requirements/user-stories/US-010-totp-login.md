# US-010: Username/Password Login with TOTP as Second Factor

**ID**: US-010
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a person on an instance without a passkey-capable client, I want to sign in with username, password and a TOTP code, so that I can use a second factor even without WebAuthn support.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-010: Username/Password Login with TOTP](../req/REQ-010-username-password-totp.md)

## Acceptance Criteria

**AC1**:

- Given: a person with the correct password and a valid TOTP code
- When: they sign in
- Then: they receive a session according to their active role assignments

**AC2**:

- Given: a correct password, but a wrong TOTP code
- When: the login attempt is completed
- Then: no session is created, and the error message does not reveal that the password was correct

## Technical Notes

- Affected components: auth module, TOTP library (RFC 6238)
- Affected EntityTypes/Relations: password hash, TOTP secret (separate from the `Person` object)
- API endpoints: login endpoint with two steps (password, then TOTP)
- Database changes: separate table for password hash (Argon2id) and TOTP secret per person

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (incl. rate-limiting test against brute force)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001, US-006 (generic error messages), US-021 (TOTP enrollment), US-023 (password enrollment)
- Blocks: none

## Notes

Rate limiting for TOTP attempts is a mandatory part of this story (brute-force protection with only a 6-digit code).
