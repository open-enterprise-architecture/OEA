# US-026: Create a Secure Password via Generator

**ID**: US-026
**Story Points**: 3
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-027-password-administration` – generator for `setInitialPassword` with `generate: true`, `resetPersonPassword` and `POST /me/password/suggestion` (`suggestPassword`), button *Suggest a password* on SCR-004

## Story

As an administrator or person, I want to have a secure password generated at the push of a button, so that I do not have to invent a compliant password myself and can be sure that it meets the configured policies.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-027: Password Generator](../req/REQ-027-password-generator.md)

## Acceptance Criteria

**AC1**:

- Given: Max sets an initial password for a person (admin UI)
- When: he clicks "Generate password"
- Then: a password is displayed that meets all active policies (REQ-028)

**AC2**:

- Given: the instance has `requireSpecialChars=true` configured
- When: a password is generated
- Then: it contains a special character from the full ASCII special character set

**AC3**:

- Given: a generated password is displayed
- When: Max generates it again
- Then: the new password is with high probability different (CSPRNG)

**AC4**:

- Given: a person changes their password
- When: they click "Generate password"
- Then: the same generator is also available in the user context

## Technical Notes

- Affected components: auth module (generator function), admin UI (initial password form), password change form (future)
- Affected EntityTypes/Relations: none of its own; generator is a pure helper function
- API endpoints: `GET /auth/utils/generate-password?length=20` (returns plaintext password; HTTPS only; no persistence)
- Database changes: none

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (policy compliance, CSPRNG usage, full special character set)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-028 (password policy configuration should exist beforehand so that the generator works configuration-driven); US-023 (admin set-password form must exist)
- Blocks: none

## Notes

Realigned by OpenSpec change `us-027-password-administration`: the generator has no standalone endpoint `GET /auth/utils/generate-password`; administrators get a generated password from `setInitialPassword` with `generate: true` and from the reset, persons from `POST /me/password/suggestion`. A generated password has the minimum length of the policy, at least 16 characters; the length is not a parameter. AC1 is an API operation, documented in the manual; an admin UI follows later.

The generator endpoint returns the password in plaintext (briefly, only in the HTTP response); the client displays it once. No client-side generation (would presuppose browser entropy), instead server-side with CSPRNG. The length can optionally be passed as a parameter; the default is the configured recommended value or 20 characters.
