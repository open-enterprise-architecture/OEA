# US-012: Configurable Token Lifetime for Local Authentication

**ID**: US-012
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator of an instance with local authentication, I want to be able to configure the lifetime of access and refresh tokens, so that I can make the trade-off between security and usability myself.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-012: Configurable Token Lifetime for Local Authentication](../req/REQ-012-token-lifetime-local.md)

## Acceptance Criteria

**AC1**:
- Given: an instance with a locally configured access token lifetime of 15 minutes
- When: a person signs in via a local mechanism
- Then: the access token expires after 15 minutes

**AC2**:
- Given: an expired access token, but a still valid refresh token
- When: a refresh is requested
- Then: a new access token with the configured lifetime is issued

## Technical Notes

- Affected components: instance configuration, token creation (local mechanisms)
- Affected EntityTypes/Relations: no business ones
- API endpoints: token refresh endpoint
- Database changes: configuration values `accessTokenLifetime`, `refreshTokenLifetime`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (expiry and refresh behavior with different values)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-009, US-010 or US-011 (at least one local mechanism must exist)
- Blocks: none

## Notes

Does not apply to externally issued tokens (there the IdP determines the lifetime, see US-001 AC3).
