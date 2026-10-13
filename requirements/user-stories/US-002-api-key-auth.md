# US-002: API Key Authentication for Machine-to-Machine Access

**ID**: US-002
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As a machine client (e.g. CI script, discovery adapter), I want to authenticate with an API key, so that I can access protected resources without going through a browser OIDC flow.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-002: API Key Authentication](../req/REQ-002-api-key-authentication.md)

## Acceptance Criteria

**AC1**:

- Given: a request with a valid, non-revoked API key
- When: it is made against a protected resource
- Then: it is authorized without a redirect, according to the roles of the associated identity

**AC2**:

- Given: a request with an invalid or revoked API key
- When: it is made
- Then: it is rejected without an authorization context being created

## Technical Notes

- Affected components: auth module (API key validation)
- Affected EntityTypes/Relations: `Person`/service identity, `Role`
- API endpoints: all protected endpoints, additional header `Authorization: ApiKey ...`
- Database changes: storage of API keys (hash, not plaintext), scoping metadata

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001 (shared role/authorization model)
- Blocks: none

## Notes

Covers alternative A2 from UC-01.
